import { WebSocket } from "ws";

import {
  type AppMessage,
  type CommandOp,
  type CommandParams,
  type PairingPayload,
  type ProgressEvent,
  type RelayOutbound,
  PROTOCOL_VERSION,
  actionTtlMs,
  b64urlDecode,
  b64urlEncode,
  decryptJson,
  deriveSessionKeys,
  encodePairing,
  encryptJson,
  generateKeyPair,
  newMessageId,
  parsePairing,
  relayWsOrigin,
  type SessionKeys,
  formatSafetyCode,
} from "@pony/shared";

export type { AppMessage, ProgressEvent } from "@pony/shared";

export interface ReconnectOptions {
  /** First retry delay. */
  initialMs?: number;
  /** Longest retry delay. */
  maxMs?: number;
  /** How long a request waits for this side's socket to come back. */
  requestGraceMs?: number;
  /** How long a request waits for a peer that dropped off. */
  awayGraceMs?: number;
  /** Ping interval. A socket that misses a pong is replaced. */
  heartbeatMs?: number;
}

export interface PairOptions {
  relayHttp: string;
  relayWs?: string;
  /** Shown to the phone. Defaults to [relayHttp]. Tests can advertise a Tailscale URL. */
  publicRelay?: string;
  /** Socket the bot dials. Defaults to the advertised relay. */
  socketUrl?: string;
  clientName?: string;
  /** `false` turns off rejoining after a dropped socket. */
  reconnect?: ReconnectOptions | false;
}

export interface CommandResult {
  ok: boolean;
  error?: string;
  result?: Record<string, unknown>;
}

export interface RequestOptions {
  /** The phone is holding this command, for example until a call screen closes. */
  onProgress?: (event: ProgressEvent) => void;
}

/**
 * - `connecting`: first dial.
 * - `waiting`: in the room, the other side hasn't finished pairing.
 * - `ready`: both sides present with session keys.
 * - `away`: the other side dropped off. The relay is holding the room for it.
 * - `reconnecting`: this side's socket dropped and is being redialed.
 * - `ended`: the room is gone. Pair again.
 */
export type LinkState = "connecting" | "waiting" | "ready" | "away" | "reconnecting" | "ended";

export interface LinkStatus {
  state: LinkState;
  /** Why the session ended, or the last transient problem. */
  reason?: string;
  /** The other side's own reason for ending, such as `time_limit` or `owner`. */
  endedBy?: string;
  /** Reconnect attempts since the link was last ready. */
  attempt: number;
  nextRetryAt?: number;
  /** The last `ready` rejoined a live session. */
  resumed: boolean;
}

/** Everything a bot needs to rejoin its session after a restart. Holds a private key. Store it 0600. */
export interface SavedSession {
  v: 1;
  role: "bot";
  payload: PairingPayload;
  socketUrl: string;
  privateKey: string;
  publicKey: string;
  /** The phone's key from the handshake. A rejoin with another key is ignored. */
  peerKey: string;
  clientName?: string;
  pairedAt: number;
}

interface Pending {
  op: CommandOp;
  resolve: (result: CommandResult) => void;
  reject: (err: Error) => void;
  timer?: ReturnType<typeof setTimeout>;
  deadline: number;
  onProgress?: (event: ProgressEvent) => void;
}

const DEFAULT_RECONNECT: Required<ReconnectOptions> = {
  initialMs: 1_000,
  maxMs: 30_000,
  requestGraceMs: 10_000,
  awayGraceMs: 8_000,
  heartbeatMs: 25_000,
};

/** Reasons after which the room can't be rejoined. */
export const ENDED_REASONS = new Set([
  "unknown_token",
  "expired_token",
  "room_closed",
  "bad_token",
  "bad_role",
  "role_taken",
  "peer_left",
  "replaced",
]);

/** Added to the phone's own limit before a held command times out here. */
export const PROGRESS_SLACK_MS = 15_000;

/** How long a reply to a request failed by a drop is still accepted. */
const ORPHAN_TTL_MS = 2 * 60 * 1000;

const CLOSE_ROOM_GONE = 4000;
const CLOSE_PEER_LEFT = 4001;
const CLOSE_REPLACED = 4002;

/** 1 s, 2 s, 4 s … capped, with ±20% jitter. */
export function backoffDelay(attempt: number, initialMs = 1_000, maxMs = 30_000, random = Math.random): number {
  const base = Math.min(maxMs, initialMs * 2 ** Math.max(0, attempt - 1));
  const jitter = base * 0.2 * (random() * 2 - 1);
  return Math.max(initialMs / 2, Math.round(base + jitter));
}

export class PonySession {
  readonly payload: PairingPayload;
  readonly role: "bot" | "phone";
  readonly privateKey: Uint8Array;
  readonly publicKey: Uint8Array;
  readonly clientName?: string;
  keys?: SessionKeys;
  onRequest?: (msg: AppMessage) => Promise<CommandResult> | CommandResult;
  onEvent?: (msg: AppMessage) => void;
  onProgress?: (event: ProgressEvent) => void;

  private ws?: WebSocket;
  private wsUrl = "";
  private peerKey?: string;
  private endedBy?: string;
  private pairedAt?: number;
  private established = false;
  private closing?: string;
  private alive = true;
  private heartbeat?: ReturnType<typeof setInterval>;
  private retryTimer?: ReturnType<typeof setTimeout>;
  private readonly pending = new Map<string, Pending>();
  /** Requests failed by a drop that the other side may still answer after the rejoin. */
  private readonly orphans = new Map<string, { op: CommandOp; at: number }>();
  private readonly lateListeners = new Set<(op: CommandOp, result: CommandResult) => void>();
  private readonly listeners = new Set<(status: LinkStatus) => void>();
  private readonly reconnect: Required<ReconnectOptions> | null;
  private status: LinkStatus = { state: "connecting", attempt: 0, resumed: false };

  private constructor(
    role: "bot" | "phone",
    payload: PairingPayload,
    privateKey: Uint8Array,
    publicKey: Uint8Array,
    clientName?: string,
    reconnect?: ReconnectOptions | false,
  ) {
    this.role = role;
    this.payload = payload;
    this.privateKey = privateKey;
    this.publicKey = publicKey;
    this.clientName = clientName?.trim() || undefined;
    this.reconnect = reconnect === false ? null : { ...DEFAULT_RECONNECT, ...reconnect };
  }

  static async createBot(opts: PairOptions): Promise<PonySession> {
    const httpUrl = opts.relayHttp.replace(/\/$/, "");
    const res = await fetch(`${httpUrl}/pair`, { method: "POST" });
    if (!res.ok) {
      throw new Error(`pair failed: ${res.status}`);
    }
    const body = (await res.json()) as { token: string; wsUrl: string };
    const keys = generateKeyPair();
    const advertised = relayWsOrigin(opts.publicRelay ?? opts.relayWs ?? httpUrl);
    const socket = opts.socketUrl ?? opts.relayWs ?? advertised;
    const payload: PairingPayload = {
      v: PROTOCOL_VERSION,
      relay: advertised.replace(/\/ws$/, ""),
      token: body.token,
      pk: b64urlEncode(keys.publicKey),
    };
    const session = new PonySession("bot", payload, keys.privateKey, keys.publicKey, opts.clientName, opts.reconnect);
    session.wsUrl = socketPath(socket);
    await session.dial();
    return session;
  }

  static async createPhone(
    rawPayload: string,
    keyPair = generateKeyPair(),
    opts: { socketUrl?: string; reconnect?: ReconnectOptions | false } = {},
  ): Promise<PonySession> {
    const payload = parsePairing(rawPayload);
    const session = new PonySession("phone", payload, keyPair.privateKey, keyPair.publicKey, undefined, opts.reconnect);
    session.keys = deriveSessionKeys(
      keyPair.privateKey,
      b64urlDecode(payload.pk),
      payload.token,
      "phone",
    );
    session.peerKey = payload.pk;
    session.wsUrl = socketPath(opts.socketUrl ?? payload.relay);
    await session.dial();
    return session;
  }

  /**
   * Rejoins a saved session. Returns at once and keeps dialing in the
   * background, so a relay that is briefly unreachable is not an error.
   * Watch [state]: `ended` means the relay no longer has the room.
   */
  static resumeBot(
    saved: SavedSession,
    opts: { socketUrl?: string; reconnect?: ReconnectOptions | false } = {},
  ): PonySession {
    const checked = checkSaved(saved);
    const privateKey = b64urlDecode(checked.privateKey);
    const session = new PonySession(
      "bot",
      checked.payload,
      privateKey,
      b64urlDecode(checked.publicKey),
      checked.clientName,
      opts.reconnect,
    );
    session.keys = deriveSessionKeys(privateKey, b64urlDecode(checked.peerKey), checked.payload.token, "bot");
    session.peerKey = checked.peerKey;
    session.pairedAt = checked.pairedAt;
    session.established = true;
    session.wsUrl = socketPath(opts.socketUrl ?? checked.socketUrl);
    session.dial().catch(() => undefined);
    return session;
  }

  get state(): LinkState {
    return this.status.state;
  }

  get ready(): boolean {
    return this.status.state === "ready";
  }

  link(): LinkStatus {
    return { ...this.status };
  }

  /** Called on every link change. Returns an unsubscribe function. */
  onStatus(listener: (status: LinkStatus) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * A reply to a request that already failed because the link dropped. The
   * phone may have acted on it, or handed out a request, just before the drop.
   */
  onLateReply(listener: (op: CommandOp, result: CommandResult) => void): () => void {
    this.lateListeners.add(listener);
    return () => this.lateListeners.delete(listener);
  }

  qrJson(): string {
    return encodePairing(this.payload);
  }

  safetyCode(): string {
    if (!this.keys) throw new Error("handshake not finished");
    return formatSafetyCode(this.keys.safetyCode);
  }

  /** The bot's side of a finished pairing, for [resumeBot]. */
  save(): SavedSession {
    if (this.role !== "bot" || !this.keys || !this.peerKey) {
      throw new Error("nothing to save until the phone finishes pairing");
    }
    return {
      v: 1,
      role: "bot",
      payload: this.payload,
      socketUrl: this.wsUrl,
      privateKey: b64urlEncode(this.privateKey),
      publicKey: b64urlEncode(this.publicKey),
      peerKey: this.peerKey,
      ...(this.clientName ? { clientName: this.clientName } : {}),
      pairedAt: this.pairedAt ?? Date.now(),
    };
  }

  async waitUntilReady(timeoutMs = 15_000): Promise<void> {
    if (await this.untilReady(timeoutMs)) return;
    if (this.status.state === "ended") {
      throw new Error(`session ended: ${this.status.reason ?? "closed"}`);
    }
    throw new Error("timed out waiting for peer");
  }

  /** Resolves true once the link is ready, false on timeout or when the session ends. */
  untilReady(timeoutMs: number): Promise<boolean> {
    if (this.status.state === "ready") return Promise.resolve(true);
    if (this.status.state === "ended") return Promise.resolve(false);
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        off();
        resolve(false);
      }, timeoutMs);
      timer.unref?.();
      const off = this.onStatus((status) => {
        if (status.state !== "ready" && status.state !== "ended") return;
        clearTimeout(timer);
        off();
        resolve(status.state === "ready");
      });
    });
  }

  /**
   * Sends a command and waits for the reply. Session problems come back as
   * `{ok:false}` right away: `peer_left` (the other side ended it),
   * `peer_away` (it dropped off and didn't return in time), `connection_lost`,
   * or the relay's reason. Only a reply that never arrives throws.
   */
  async request(
    op: CommandOp,
    params: CommandParams = {},
    timeoutMs = 15_000,
    opts: RequestOptions = {},
  ): Promise<CommandResult> {
    const blocked = await this.admit(timeoutMs);
    if (blocked) return { ok: false, error: blocked };
    const id = newMessageId();
    const stamped: CommandParams = {
      ...params,
      issuedAt: params.issuedAt ?? Date.now(),
      ttlMs: params.ttlMs ?? actionTtlMs(op),
    };
    return new Promise<CommandResult>((resolve, reject) => {
      const entry: Pending = { op, resolve, reject, deadline: Date.now() + timeoutMs, onProgress: opts.onProgress };
      entry.timer = this.arm(id, entry, timeoutMs);
      this.pending.set(id, entry);
      if (!this.sendEncrypted({ id, kind: "req", op, params: stamped })) {
        clearTimeout(entry.timer);
        this.pending.delete(id);
        resolve({ ok: false, error: "connection_lost" });
      }
    });
  }

  sendResponse(id: string, result: CommandResult): void {
    this.sendEncrypted({
      id,
      kind: "res",
      ok: result.ok,
      error: result.error,
      result: result.result,
    });
  }

  sendEvent(op: AppMessage["op"], result: Record<string, unknown>): void {
    this.sendEncrypted({ id: newMessageId(), kind: "evt", op, result });
  }

  /**
   * Leaves without ending the session. The relay holds the room, so the other
   * side sees `peer_away` and [resumeBot] can pick it up again.
   */
  close(): void {
    this.closing ??= "closed";
    this.finish(this.closing);
  }

  /** Ends the session for both sides. [why] tells the other side, for example `owner` or `time_limit`. */
  async end(why: string = this.role === "phone" ? "owner" : "assistant"): Promise<void> {
    if (this.status.state === "ended") return;
    this.closing = "ended";
    const ws = this.ws;
    if (ws && ws.readyState === WebSocket.OPEN) {
      if (this.status.state === "ready") this.sendEvent("ended", { reason: why });
      ws.send(JSON.stringify({ type: "bye" }));
      await new Promise<void>((resolve) => {
        const timer = setTimeout(resolve, 750);
        timer.unref?.();
        ws.once("close", () => {
          clearTimeout(timer);
          resolve();
        });
      });
    }
    this.finish("ended");
  }

  private async admit(timeoutMs: number): Promise<string | undefined> {
    if (this.status.state === "ended") return this.status.reason ?? "session_ended";
    if (!this.keys) throw new Error("not connected");
    if (this.status.state === "ready") return undefined;
    const away = this.status.state === "away";
    const grace = this.reconnect ? (away ? this.reconnect.awayGraceMs : this.reconnect.requestGraceMs) : 0;
    if (await this.untilReady(Math.min(timeoutMs, grace))) return undefined;
    const now = this.link();
    if (now.state === "ended") return now.reason ?? "session_ended";
    return now.state === "away" ? "peer_away" : "connection_lost";
  }

  private arm(id: string, entry: Pending, ms: number): ReturnType<typeof setTimeout> {
    return setTimeout(() => {
      if (this.pending.get(id) !== entry) return;
      this.pending.delete(id);
      this.sendCancel(id);
      entry.reject(new Error(`timed out waiting for ${entry.op}`));
    }, Math.max(0, ms));
  }

  /** Tells the phone to drop this command if it has not run yet. */
  private sendCancel(ref: string): void {
    this.sendEncrypted({
      id: newMessageId(),
      kind: "evt",
      op: "cancel",
      result: { ref },
    });
  }

  private failPending(error: string): void {
    const transient = error === "peer_away" || error === "connection_lost";
    const now = Date.now();
    for (const [id, orphan] of this.orphans) {
      if (now - orphan.at > ORPHAN_TTL_MS) this.orphans.delete(id);
    }
    for (const [id, entry] of this.pending) {
      this.pending.delete(id);
      clearTimeout(entry.timer);
      if (transient) this.orphans.set(id, { op: entry.op, at: now });
      entry.resolve({ ok: false, error });
    }
    while (this.orphans.size > 64) {
      const oldest = this.orphans.keys().next().value;
      if (oldest === undefined) break;
      this.orphans.delete(oldest);
    }
  }

  private setState(state: LinkState, extra: Partial<LinkStatus> = {}): void {
    const next: LinkStatus = { ...this.status, ...extra, state };
    if (state === "ready") {
      next.attempt = 0;
      next.nextRetryAt = undefined;
    }
    const same =
      next.state === this.status.state &&
      next.reason === this.status.reason &&
      next.attempt === this.status.attempt &&
      next.resumed === this.status.resumed;
    this.status = next;
    if (same) return;
    for (const listener of [...this.listeners]) listener({ ...next });
  }

  private sendEncrypted(msg: AppMessage): boolean {
    const ws = this.ws;
    if (!this.keys || !ws || ws.readyState !== WebSocket.OPEN) return false;
    ws.send(JSON.stringify({ type: "fwd", data: encryptJson(this.keys.send, msg) }));
    return true;
  }

  private sendRaw(ws: WebSocket, body: unknown): void {
    if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(body));
  }

  private dial(): Promise<void> {
    const ws = new WebSocket(this.wsUrl);
    this.ws = ws;
    return new Promise((resolve, reject) => {
      let opened = false;
      ws.once("open", () => {
        opened = true;
        const hello: Record<string, unknown> = {
          type: "hello",
          role: this.role,
          token: this.payload.token,
        };
        if (this.clientName) hello.client = this.clientName;
        if (this.reconnect) hello.resume = true;
        ws.send(JSON.stringify(hello));
        this.startHeartbeat(ws);
        resolve();
      });
      ws.on("message", (raw) => {
        this.alive = true;
        this.onRelayMessage(ws, String(raw));
      });
      ws.on("pong", () => {
        this.alive = true;
      });
      ws.on("error", (err) => {
        if (!opened) reject(err);
      });
      ws.on("close", (code, reason) => this.onSocketClosed(ws, code, reason.toString()));
    });
  }

  private startHeartbeat(ws: WebSocket): void {
    this.stopHeartbeat();
    if (!this.reconnect) return;
    this.alive = true;
    this.heartbeat = setInterval(() => {
      if (this.ws !== ws) return;
      if (!this.alive) {
        ws.terminate();
        return;
      }
      this.alive = false;
      try {
        ws.ping();
      } catch {
        ws.terminate();
      }
    }, this.reconnect.heartbeatMs);
    this.heartbeat.unref?.();
  }

  private stopHeartbeat(): void {
    if (this.heartbeat) clearInterval(this.heartbeat);
    this.heartbeat = undefined;
  }

  private onRelayMessage(ws: WebSocket, raw: string): void {
    if (ws !== this.ws) return;
    let env: RelayOutbound;
    try {
      env = JSON.parse(raw) as RelayOutbound;
    } catch {
      return;
    }
    switch (env.type) {
      case "waiting":
        this.setState(this.established ? "away" : "waiting");
        return;
      case "ready":
        this.established = true;
        if (this.role === "phone") {
          this.sendRaw(ws, {
            type: "fwd",
            data: JSON.stringify({ type: "hs", pk: b64urlEncode(this.publicKey) }),
          });
        }
        if (this.keys) {
          this.setState("ready", { resumed: env.resumed === true, reason: undefined });
        } else {
          this.setState("waiting", { resumed: false });
        }
        return;
      case "fwd":
        void this.onForward(ws, env.data);
        return;
      case "peer_away":
        this.failPending("peer_away");
        this.setState("away");
        return;
      case "peer_left":
        this.finish("peer_left");
        return;
      case "error":
        if (ENDED_REASONS.has(env.reason)) {
          this.finish(env.reason);
        } else if (env.reason === "peer_missing") {
          this.failPending("peer_away");
          this.setState("away");
        } else {
          this.setState(this.status.state, { reason: env.reason });
        }
        return;
    }
  }

  private onSocketClosed(ws: WebSocket, code: number, reason: string): void {
    if (ws !== this.ws) return;
    this.ws = undefined;
    this.stopHeartbeat();
    if (this.status.state === "ended") return;
    if (this.closing) return this.finish(this.closing);
    if (code === CLOSE_PEER_LEFT) return this.finish("peer_left");
    if (code === CLOSE_ROOM_GONE) return this.finish(reason || "room_closed");
    if (code === CLOSE_REPLACED) return this.finish("replaced");
    this.failPending("connection_lost");
    if (!this.established || !this.reconnect) return this.finish("connection_lost");
    this.scheduleReconnect();
  }

  private scheduleReconnect(): void {
    const policy = this.reconnect;
    if (!policy) return;
    const attempt = this.status.attempt + 1;
    const delay = backoffDelay(attempt, policy.initialMs, policy.maxMs);
    this.setState("reconnecting", { attempt, nextRetryAt: Date.now() + delay });
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.retryTimer = setTimeout(() => {
      this.retryTimer = undefined;
      if (this.status.state === "ended") return;
      this.dial().catch(() => undefined);
    }, delay);
  }

  private finish(reason: string): void {
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.retryTimer = undefined;
    this.stopHeartbeat();
    if (this.status.state !== "ended") {
      this.failPending(reason);
      this.setState("ended", {
        reason,
        nextRetryAt: undefined,
        ...(reason === "peer_left" && this.endedBy ? { endedBy: this.endedBy } : {}),
      });
    }
    const ws = this.ws;
    this.ws = undefined;
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      ws.close(1000, reason === "ended" ? "bye" : "detach");
    }
  }

  private async onForward(ws: WebSocket, data: string): Promise<void> {
    if (data.startsWith("{")) {
      this.onPlainFrame(ws, data);
      return;
    }
    if (!this.keys) return;

    let msg: AppMessage;
    try {
      msg = decryptJson<AppMessage>(this.keys.recv, data);
    } catch {
      return;
    }

    if (msg.kind === "res" && msg.id) {
      const reply: CommandResult = { ok: Boolean(msg.ok), error: msg.error, result: msg.result };
      const entry = this.pending.get(msg.id);
      if (entry) {
        this.pending.delete(msg.id);
        clearTimeout(entry.timer);
        entry.resolve(reply);
        return;
      }
      const orphan = this.orphans.get(msg.id);
      if (orphan) {
        this.orphans.delete(msg.id);
        for (const listener of [...this.lateListeners]) listener(orphan.op, reply);
        return;
      }
    }

    if (msg.kind === "req" && msg.id && this.onRequest) {
      let result: CommandResult;
      try {
        result = await this.onRequest(msg);
      } catch (err) {
        result = { ok: false, error: err instanceof Error ? err.message : String(err) };
      }
      this.sendResponse(msg.id, result);
      return;
    }

    if (msg.kind === "evt" && msg.op === "progress" && msg.result) {
      this.onPhoneProgress(msg.result as unknown as ProgressEvent);
    }
    if (msg.kind === "evt" && msg.op === "ended" && typeof msg.result?.reason === "string") {
      this.endedBy = msg.result.reason;
    }
    this.onEvent?.(msg);
  }

  private onPlainFrame(ws: WebSocket, data: string): void {
    if (this.role !== "bot") return;
    let frame: { type?: string; pk?: string };
    try {
      frame = JSON.parse(data) as { type?: string; pk?: string };
    } catch {
      return;
    }
    if (frame.type !== "hs" || typeof frame.pk !== "string") return;
    if (!this.keys) {
      this.keys = deriveSessionKeys(this.privateKey, b64urlDecode(frame.pk), this.payload.token, "bot");
      this.peerKey = frame.pk;
      this.pairedAt = Date.now();
    } else if (frame.pk !== this.peerKey) {
      this.setState(this.status.state, { reason: "peer_key_changed" });
      return;
    }
    if (this.clientName) {
      this.sendRaw(ws, { type: "fwd", data: JSON.stringify({ type: "intro", client: this.clientName }) });
    }
    this.setState("ready", { reason: undefined });
  }

  private onPhoneProgress(event: ProgressEvent): void {
    const entry = typeof event.ref === "string" ? this.pending.get(event.ref) : undefined;
    if (entry) {
      const left = Math.max(0, Number(event.limitMs ?? 0) - Number(event.waitedMs ?? 0));
      const deadline = Date.now() + left + PROGRESS_SLACK_MS;
      if (deadline > entry.deadline) {
        clearTimeout(entry.timer);
        entry.deadline = deadline;
        entry.timer = this.arm(event.ref, entry, deadline - Date.now());
      }
      entry.onProgress?.(event);
    }
    this.onProgress?.(event);
  }
}

function socketPath(url: string): string {
  const origin = relayWsOrigin(url);
  return `${origin}/ws`;
}

function checkSaved(saved: SavedSession): SavedSession {
  if (!saved || saved.v !== 1 || saved.role !== "bot") throw new Error("not a saved Pony session");
  const payload = parsePairing(JSON.stringify(saved.payload));
  for (const field of ["socketUrl", "privateKey", "publicKey", "peerKey"] as const) {
    if (typeof saved[field] !== "string" || !saved[field]) throw new Error(`saved session is missing ${field}`);
  }
  return { ...saved, payload };
}
