import QRCode from "qrcode";

import type { CommandOp, CommandParams, PressKey } from "@pony/shared";
import {
  OWNER_CLIENT_WAIT_MS,
  PAIRING_TTL_MS,
  REQUEST_POLL_MS,
  SESSION_TTL_MS,
  pairPageLink,
  pairingLink,
} from "@pony/shared";
import {
  ENDED_REASONS,
  PonySession,
  type CommandResult,
  type LinkState,
  type ReconnectOptions,
  type RequestOptions,
} from "@pony/client";
import { RequestListener, SessionFile, type OwnerRequest } from "@pony/client/listen";

export interface ActionLogEntry {
  at: number;
  action: string;
  detail: string;
  ok: boolean;
}

export interface PhoneStatus {
  /** The phone is on the relay with this session right now. */
  connected: boolean;
  /** A pairing code is out and the phone hasn't finished pairing. */
  waiting: boolean;
  /** `away`: the phone dropped off and Pony is holding the session. `reconnecting`: this side lost the relay. */
  link: LinkState | "unpaired";
  safetyCode: string | null;
  token: string | null;
  pairingLink: string | null;
  /** Hand-off page for when the QR is on the phone itself. Null until a code is out. */
  pairPageLink: string | null;
  relay: string;
  sessionTtlMs: number;
  connectedAt: number | null;
  /** Null when the session lasts until someone disconnects. */
  expiresAt: number | null;
  remainingMs: number | null;
  resumed: boolean;
  reconnectAttempt: number;
  /** Listen mode: a standing wait is open on the phone. */
  listening: boolean;
  /** Listen mode: requests heard and not yet handed out. */
  queued: number;
  lastError: string | null;
  log: ActionLogEntry[];
  /** Active IME id and whether the Pony keyboard can commit text. Null until the phone answers `info`. */
  ime: ImeStatusInfo | null;
  imeCurrent: string | null;
  ponyKeyboardUsable: boolean | null;
  /** The owner tapped It matches on the phone. Acting tools stay dark until this is true. */
  ownerConfirmed: boolean;
  /** Set when the phone is too old for this protocol. */
  updateRequired: string | null;
}

export interface ImeStatusInfo {
  current: string | null;
  ponyEnabled: boolean;
  ponySelected: boolean;
  ponyActive: boolean;
  ponyUsable: boolean;
}

export interface PairInfo {
  token: string;
  pairingLink: string;
  /** Hand-off page link, for when the QR shows on the same phone that would scan it. */
  pairPageLink: string;
  payload: string;
  qrPngDataUrl: string;
  qrPngBase64: string;
  safetyCode: string | null;
  expiresInMs: number;
  sessionTtlMs: number;
}

export interface DisplayOpts {
  background?: boolean;
  display?: "main" | "background";
}

export interface ControllerOptions {
  relayHttp: string;
  /** Bot-side session limit. Omit to follow the phone's own setting. */
  sessionTtlMs?: number;
  now?: () => number;
  /** Shown to the phone. The MCP default is Grok Bot. */
  clientName?: string;
  /** Advertised in the QR. Tests use this for a Tailscale URL while dialing locally. */
  publicRelay?: string;
  /** Socket the bot dials. Defaults to the advertised relay. */
  socketUrl?: string;
  /**
   * Keep a standing `wait_for_request` open while the assistant is active and
   * hold what the owner says until `wait_for_request` is called.
   */
  listen?: boolean;
  /** Listen mode: how long after the last tool call the assistant still counts as active. */
  attendMs?: number;
  /** Keep the pairing in this file so a restart rejoins it. It holds a private key and is written 0600. */
  statePath?: string;
  reconnect?: ReconnectOptions;
}

const INBOX_LIMIT = 20;

/**
 * One bot session. The phone enforces its session length, password refusal,
 * confirmations, and its own action log. This side mirrors the log, follows
 * the phone's session length, and rejoins after dropped connections.
 */
export class PhoneController {
  private session: PonySession | null = null;
  private connectedAt: number | null = null;
  private lastError: string | null = null;
  private relayHttp: string;
  /** Local-clock end of the phone's session. Null: until disconnect. Undefined: the phone didn't say. */
  private phoneEndsAt: number | null | undefined = undefined;
  private listener: RequestListener | null = null;
  private inbox: OwnerRequest[] = [];
  private readonly inboxWaiters = new Set<(request?: OwnerRequest) => void>();
  private claimed: OwnerRequest | null = null;
  private attendedUntil = 0;
  private waitsInFlight = 0;
  private attendTimer?: ReturnType<typeof setTimeout>;
  private unsubscribe?: () => void;
  private readonly store: SessionFile | null;
  readonly log: ActionLogEntry[] = [];
  private phoneIme: ImeStatusInfo | null = null;
  private lastScreenPkg?: string;
  private ownerConfirmed = false;

  constructor(private readonly options: ControllerOptions) {
    this.relayHttp = options.relayHttp.replace(/\/$/, "");
    this.store = options.statePath ? new SessionFile(options.statePath) : null;
    this.restore();
  }

  private now(): number {
    return this.options.now?.() ?? Date.now();
  }

  private ttl(): number {
    return this.options.sessionTtlMs ?? SESSION_TTL_MS;
  }

  get listenMode(): boolean {
    return Boolean(this.options.listen);
  }

  /** The assistant called a tool. In listen mode that keeps the standing wait open. */
  touch(): void {
    this.attend();
  }

  status(): PhoneStatus {
    const session = this.session;
    const link = session?.link();
    const hasKeys = Boolean(session?.keys);
    const expiresAt = this.expiresAt();
    return {
      connected: hasKeys && link?.state === "ready",
      waiting: Boolean(session) && !hasKeys && link?.state !== "ended",
      link: link?.state ?? "unpaired",
      safetyCode: hasKeys ? session!.safetyCode() : null,
      token: session?.payload.token ?? null,
      pairingLink: session ? pairingLink(session.payload, linkClient(this.clientLabel())) : null,
      pairPageLink: session ? pairPageLink(session.payload, linkClient(this.clientLabel())) : null,
      relay: this.relayHttp,
      sessionTtlMs: this.ttl(),
      connectedAt: this.connectedAt,
      expiresAt,
      remainingMs: expiresAt === null ? null : Math.max(0, expiresAt - this.now()),
      resumed: link?.resumed ?? false,
      reconnectAttempt: link?.attempt ?? 0,
      listening: Boolean(this.listener?.running),
      queued: this.inbox.length,
      lastError: this.lastError ?? (link?.state === "ended" ? link.reason ?? null : null),
      log: this.log.slice(0, 50),
      ime: this.phoneIme,
      imeCurrent: this.phoneIme?.current ?? null,
      ponyKeyboardUsable: this.phoneIme?.ponyUsable ?? null,
      ownerConfirmed: this.ownerConfirmed || Boolean(session?.ownerConfirmed),
      updateRequired: session?.protocolTooOld ? "Please update Pony to 0.6.5 or later." : null,
    };
  }

  async pair(input: { waitMs?: number; relay?: string } = {}): Promise<PairInfo> {
    this.attend();
    const waitMs = input.waitMs ?? 0;
    if (input.relay) this.relayHttp = input.relay.replace(/\/$/, "");
    await this.dropSession(true);
    const session = await PonySession.createBot({
      relayHttp: this.relayHttp,
      clientName: this.clientLabel(),
      publicRelay: this.options.publicRelay,
      socketUrl: this.options.socketUrl,
      reconnect: this.options.reconnect,
    });
    this.adopt(session);
    this.lastError = null;
    void session.waitUntilReady(PAIRING_TTL_MS).catch((err: unknown) => {
      if (this.session === session && !session.keys) {
        this.lastError = err instanceof Error ? err.message : String(err);
      }
    });

    if (waitMs > 0) {
      await session.waitUntilReady(waitMs).catch(() => undefined);
    }

    const png = await QRCode.toBuffer(session.qrJson(), { type: "png", width: 360, margin: 2 });
    const qrPngBase64 = png.toString("base64");
    this.append("pair", "token issued", true);
    return {
      token: session.payload.token,
      pairingLink: pairingLink(session.payload, linkClient(this.clientLabel())),
      pairPageLink: pairPageLink(session.payload, linkClient(this.clientLabel())),
      payload: session.qrJson(),
      qrPngDataUrl: `data:image/png;base64,${qrPngBase64}`,
      qrPngBase64,
      safetyCode: session.keys ? session.safetyCode() : null,
      expiresInMs: PAIRING_TTL_MS,
      sessionTtlMs: this.ttl(),
    };
  }

  async tap(x: number, y: number, opts?: DisplayOpts, req?: RequestOptions): Promise<CommandResult> {
    return this.command("tap", withDisplay(this.withScreen({ x, y }), opts), 20_000, req);
  }

  async swipe(
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    durationMs?: number,
    opts?: DisplayOpts,
    req?: RequestOptions,
  ): Promise<CommandResult> {
    return this.command("swipe", withDisplay(this.withScreen({ x1, y1, x2, y2, durationMs }), opts), 15_000, req);
  }

  async longPress(
    x: number,
    y: number,
    durationMs?: number,
    opts?: DisplayOpts,
    req?: RequestOptions,
  ): Promise<CommandResult> {
    return this.command("long_press", withDisplay(this.withScreen({ x, y, durationMs }), opts), 15_000, req);
  }

  async drag(
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    durationMs?: number,
    opts?: DisplayOpts,
    req?: RequestOptions,
  ): Promise<CommandResult> {
    return this.command("drag", withDisplay(this.withScreen({ x1, y1, x2, y2, durationMs }), opts), 15_000, req);
  }

  async pinch(
    x: number,
    y: number,
    fromDistance: number,
    toDistance: number,
    durationMs?: number,
    opts?: DisplayOpts,
    req?: RequestOptions,
  ): Promise<CommandResult> {
    return this.command("pinch", withDisplay(this.withScreen({ x, y, fromDistance, toDistance, durationMs }), opts), 15_000, req);
  }

  async typeText(
    text: string,
    opts?: DisplayOpts & { mode?: "insert" | "replace" | "append" },
    req?: RequestOptions,
  ): Promise<CommandResult> {
    const base = { text, mode: opts?.mode ?? "replace" };
    return this.command("type", withDisplay(base, opts), 45_000, req);
  }

  async key(key: PressKey, opts?: DisplayOpts, req?: RequestOptions): Promise<CommandResult> {
    return this.command("press", withDisplay({ key }, opts), 15_000, req);
  }

  async openApp(packageName: string, opts?: DisplayOpts, req?: RequestOptions): Promise<CommandResult> {
    return this.command("open_app", withDisplay({ packageName }, opts), 30_000, req);
  }

  async openSettings(
    name: string,
    packageName?: string,
    opts?: DisplayOpts,
    req?: RequestOptions,
  ): Promise<CommandResult> {
    const base: CommandParams = packageName ? { name, packageName } : { name };
    return this.command("open_settings", withDisplay(base, opts), 30_000, req);
  }

  async uiTree(opts?: DisplayOpts): Promise<CommandResult> {
    return this.command("ui_tree", withDisplay({}, opts));
  }

  async screenshot(opts?: DisplayOpts): Promise<CommandResult> {
    return this.command("screenshot", withDisplay({}, opts));
  }

  async waitIdle(timeoutMs = 4_000, opts?: DisplayOpts, req?: RequestOptions): Promise<CommandResult> {
    return this.command("wait_idle", withDisplay({ timeoutMs }, opts), timeoutMs + 5_000, req);
  }

  private clientLabel(): string {
    const name = this.options.clientName?.trim();
    return name ? name.slice(0, 40) : "Grok Bot";
  }

  /**
   * The next thing the owner asked for. In listen mode this comes from what
   * the standing wait already heard, so nothing said between calls is lost.
   */
  async waitForRequest(timeoutMs = REQUEST_POLL_MS, signal?: AbortSignal): Promise<CommandResult> {
    if (!this.listenMode) {
      this.claimed = null;
      const result = await this.command("wait_for_request", { timeoutMs }, timeoutMs + 15_000);
      const heard = requestOf(result);
      if (heard) this.claimed = heard;
      return result;
    }
    this.attend();
    this.ensureLive();
    await this.finishClaimed();
    this.waitsInFlight += 1;
    try {
      this.startListening();
      const request = this.inbox.shift() ?? (await this.nextHeard(timeoutMs, signal));
      if (!request) {
        this.ensureLive();
        this.append("wait_for_request", "empty", true);
        return { ok: true, result: { empty: true } };
      }
      this.claimed = request;
      this.append("wait_for_request", `${request.text.length} chars`, true);
      return {
        ok: true,
        result: {
          requestId: request.requestId,
          text: request.text,
          source: request.source,
          heardMsAgo: Math.max(0, Date.now() - request.receivedAt),
        },
      };
    } finally {
      this.waitsInFlight -= 1;
      this.attend();
    }
  }

  async speak(text: string): Promise<CommandResult> {
    return this.command("speak", { text }, 30_000);
  }

  async askUser(text: string): Promise<CommandResult> {
    return this.command("ask_user", { text }, OWNER_CLIENT_WAIT_MS);
  }

  async confirm(text: string): Promise<CommandResult> {
    return this.command("confirm", { text }, OWNER_CLIENT_WAIT_MS);
  }

  /** Finishes the owner's current request with a short result the phone shows and, for spoken requests, says. */
  async done(text = "", ok = true): Promise<CommandResult> {
    const ref = this.claimed?.requestId;
    const result = await this.command("done", { text, ok, ...(ref ? { ref } : {}) });
    if (result.ok) this.claimed = null;
    return result;
  }

  async disconnect(): Promise<CommandResult> {
    const session = this.session;
    this.disposeListener();
    this.session = null;
    this.connectedAt = null;
    this.phoneEndsAt = undefined;
    this.claimed = null;
    this.inbox = [];
    this.unsubscribe?.();
    this.releaseWaiters();
    if (session?.ready) {
      await session.request("disconnect", {}, 3_000).catch(() => undefined);
    }
    await session?.end();
    this.store?.clear();
    const result = { ok: true, result: { disconnected: true } };
    this.append("disconnect", "session closed", true);
    return result;
  }

  /** Leaves the relay without ending the session. With a state file, the next start rejoins it. */
  detach(): void {
    this.disposeListener();
    this.releaseWaiters();
    this.unsubscribe?.();
    this.session?.close();
    this.session = null;
    if (this.attendTimer) clearTimeout(this.attendTimer);
  }

  /** Ends the session for both sides. Used on shutdown when there is nothing to rejoin later. */
  async shutdown(): Promise<void> {
    if (this.store) {
      this.detach();
      return;
    }
    const session = this.session;
    this.detach();
    await session?.end();
  }

  private async command(
    op: CommandOp,
    params: CommandParams = {},
    timeoutMs = 15_000,
    req?: RequestOptions,
  ): Promise<CommandResult> {
    this.attend();
    this.ensureLive();
    const session = this.session!;
    if (session.protocolTooOld) {
      return { ok: false, error: "Please update Pony to 0.6.5 or later." };
    }
    if (needsConfirm(op) && !this.isConfirmed()) {
      return { ok: false, error: "not_confirmed" };
    }
    const result = await session.request(op, params, timeoutMs, req);
    this.rememberScreen(result);
    this.append(op, describe(op, params, result), result.ok);
    if (!result.ok && result.error) this.lastError = result.error;
    if (!result.ok && session.state === "ended" && result.error === session.link().reason) {
      const link = session.link();
      if (this.session === session) this.forgetSession(link.reason ?? "ended");
      throw new Error(endedMessage(link.reason ?? "ended", link.endedBy));
    }
    return result;
  }

  private isConfirmed(): boolean {
    return this.ownerConfirmed || Boolean(this.session?.ownerConfirmed);
  }

  private ensureLive(): void {
    const session = this.session;
    if (!session?.keys) {
      throw new Error("Not paired. Call pair, then connect the phone.");
    }
    const link = session.link();
    if (link.state === "ended") {
      const reason = link.reason ?? "ended";
      this.forgetSession(reason);
      throw new Error(endedMessage(reason, link.endedBy));
    }
    const limit = this.options.sessionTtlMs ?? (this.phoneEndsAt === undefined ? SESSION_TTL_MS : null);
    if (limit !== null && this.connectedAt !== null && this.now() - this.connectedAt >= limit) {
      this.forgetSession("session_expired");
      void session
        .request("disconnect", {}, 2_000)
        .catch(() => undefined)
        .finally(() => void session.end());
      this.append("disconnect", "session time limit", false);
      throw new Error("Session time limit reached. Pair again.");
    }
  }

  private forgetSession(reason: string): void {
    this.disposeListener();
    this.unsubscribe?.();
    this.releaseWaiters();
    this.session = null;
    this.connectedAt = null;
    this.phoneEndsAt = undefined;
    this.claimed = null;
    this.inbox = [];
    this.lastError = reason;
    if (ENDED_REASONS.has(reason) || reason === "session_expired") this.store?.clear();
  }

  private expiresAt(): number | null {
    if (this.connectedAt === null) return null;
    if (this.options.sessionTtlMs !== undefined) return this.connectedAt + this.options.sessionTtlMs;
    if (this.phoneEndsAt === undefined) return this.connectedAt + SESSION_TTL_MS;
    return this.phoneEndsAt;
  }

  private restore(): void {
    const saved = this.store?.load();
    if (!saved) return;
    let session: PonySession;
    try {
      session = PonySession.resumeBot(saved, { reconnect: this.options.reconnect, socketUrl: this.options.socketUrl });
    } catch {
      this.store?.clear();
      return;
    }
    this.relayHttp = httpOrigin(saved.payload.relay) ?? this.relayHttp;
    this.adopt(session);
    this.append("pair", "rejoined the saved session", true);
  }

  private adopt(session: PonySession): void {
    this.unsubscribe?.();
    this.disposeListener();
    this.session = session;
    this.connectedAt = null;
    this.phoneEndsAt = undefined;
    this.ownerConfirmed = session.ownerConfirmed;
    session.onEvent = (msg) => {
      if (msg.op === "confirmed") {
        this.ownerConfirmed = true;
        session.ownerConfirmed = true;
        if (this.store && session.keys) {
          try {
            this.store.save(session.save());
          } catch {
            /* session still works */
          }
        }
      }
    };
    const onChange = () => {
      if (this.session !== session) return;
      const link = session.link();
      if (link.state === "ready") {
        if (this.connectedAt === null) this.connectedAt = this.now();
        if (this.store && session.keys) {
          try {
            this.store.save(session.save());
          } catch {
            // The session still works; it just won't survive a restart.
          }
        }
        void this.learnPhone(session);
        if (this.isAttended()) this.startListening();
      } else if (link.state === "ended") {
        const reason = link.reason ?? "ended";
        this.lastError = reason;
        this.disposeListener();
        this.releaseWaiters();
        if (ENDED_REASONS.has(reason)) this.store?.clear();
        this.append("disconnect", reasonText(reason), false);
      }
    };
    this.unsubscribe = session.onStatus(onChange);
    onChange();
  }

  /** Reads the phone's own session length so this side doesn't cut it short. Older phones don't answer. */
  private async learnPhone(session: PonySession): Promise<void> {
    const info = await session.request("info", {}, 5_000).catch(() => null);
    if (this.session !== session || !info?.ok) return;
    const endsAt = info.result?.sessionEndsAt;
    const phoneNow = info.result?.now;
    if (typeof endsAt === "number") {
      const skew = typeof phoneNow === "number" ? Date.now() - phoneNow : 0;
      this.phoneEndsAt = endsAt + skew;
    } else {
      this.phoneEndsAt = null;
    }
    const ime = info.result?.ime;
    if (ime && typeof ime === "object") {
      const body = ime as Record<string, unknown>;
      this.phoneIme = {
        current: typeof body.current === "string" ? body.current : null,
        ponyEnabled: body.ponyEnabled === true,
        ponySelected: body.ponySelected === true,
        ponyActive: body.ponyActive === true,
        ponyUsable: body.ponyUsable === true,
      };
    }
    this.rememberScreen(info);
  }

  private rememberScreen(result: CommandResult): void {
    const pkg = result.result?.foreground ?? result.result?.windowPkg ?? result.result?.packageName;
    if (typeof pkg === "string" && pkg) this.lastScreenPkg = pkg;
  }

  private withScreen(params: CommandParams): CommandParams {
    if (params.screenPkg || !this.lastScreenPkg) return params;
    return { ...params, screenPkg: this.lastScreenPkg };
  }

  private isAttended(): boolean {
    return this.waitsInFlight > 0 || Date.now() < this.attendedUntil;
  }

  /** Any tool call means the assistant is around. The standing wait only runs while it is. */
  private attend(): void {
    if (!this.listenMode) return;
    const window = this.options.attendMs ?? 120_000;
    this.attendedUntil = Date.now() + window;
    if (this.session?.ready) this.startListening();
    if (this.attendTimer) clearTimeout(this.attendTimer);
    this.attendTimer = setTimeout(() => this.checkAttendance(), window + 50);
    this.attendTimer.unref?.();
  }

  private checkAttendance(): void {
    if (this.isAttended()) {
      const wait = Math.max(50, this.attendedUntil - Date.now() + 50);
      this.attendTimer = setTimeout(() => this.checkAttendance(), wait);
      this.attendTimer.unref?.();
      return;
    }
    this.stopListening();
  }

  private startListening(): void {
    if (!this.listenMode || !this.session?.keys) return;
    const session = this.session;
    if (!this.listener) {
      this.listener = new RequestListener(session, {
        onRequest: (request) => {
          if (this.session === session) this.heard(request);
        },
        onProblem: (error) => {
          this.lastError = error;
        },
      });
    }
    this.listener.start();
  }

  /** Pauses the standing wait. What an open wait still hears is kept. */
  private stopListening(): void {
    this.listener?.stop();
  }

  private disposeListener(): void {
    this.listener?.dispose();
    this.listener = null;
  }

  private heard(request: OwnerRequest): void {
    this.append("heard", `${request.text.length} chars`, true);
    const waiter = this.inboxWaiters.values().next().value;
    if (waiter) {
      this.inboxWaiters.delete(waiter);
      waiter(request);
      return;
    }
    this.inbox.push(request);
    if (this.inbox.length > INBOX_LIMIT) this.inbox.shift();
  }

  private nextHeard(timeoutMs: number, signal?: AbortSignal): Promise<OwnerRequest | undefined> {
    return new Promise((resolve) => {
      const finish = (request?: OwnerRequest) => {
        clearTimeout(timer);
        this.inboxWaiters.delete(finish);
        signal?.removeEventListener("abort", onAbort);
        resolve(request);
      };
      const onAbort = () => finish(undefined);
      const timer = setTimeout(() => finish(undefined), timeoutMs);
      timer.unref?.();
      this.inboxWaiters.add(finish);
      signal?.addEventListener("abort", onAbort, { once: true });
    });
  }

  private releaseWaiters(): void {
    for (const waiter of [...this.inboxWaiters]) waiter(undefined);
    this.inboxWaiters.clear();
  }

  /** The assistant came back for more, so the request it had is finished. */
  private async finishClaimed(): Promise<void> {
    const claimed = this.claimed;
    if (!claimed || !this.session) return;
    this.claimed = null;
    await this.session.request("done", { ref: claimed.requestId, ok: true }, 5_000).catch(() => undefined);
  }

  private async dropSession(end: boolean): Promise<void> {
    const session = this.session;
    this.disposeListener();
    this.unsubscribe?.();
    this.releaseWaiters();
    this.session = null;
    this.connectedAt = null;
    this.phoneEndsAt = undefined;
    this.claimed = null;
    this.inbox = [];
    this.store?.clear();
    if (!session) return;
    if (end && session.state !== "ended") await session.end();
    else session.close();
  }

  private append(action: string, detail: string, ok: boolean): void {
    this.log.unshift({ at: this.now(), action, detail, ok });
    if (this.log.length > 100) this.log.length = 100;
  }
}

function requestOf(result: CommandResult): OwnerRequest | null {
  const body = result.result;
  if (!result.ok || !body || typeof body.text !== "string" || typeof body.requestId !== "string") return null;
  return {
    requestId: body.requestId,
    text: body.text,
    source: typeof body.source === "string" ? body.source : "phone",
    receivedAt: Date.now(),
  };
}

function withDisplay(params: CommandParams, opts?: DisplayOpts): CommandParams {
  if (!opts) return params;
  const next: CommandParams = { ...params };
  if (opts.background !== undefined) next.background = opts.background;
  if (opts.display !== undefined) next.display = opts.display;
  return next;
}

function linkClient(name: string): string | undefined {
  const trimmed = name.trim();
  if (!trimmed) return undefined;
  if (trimmed.toLowerCase() === "grok bot") return "grokbot";
  return trimmed.slice(0, 40);
}

function httpOrigin(relay: string): string | undefined {
  const trimmed = relay.trim().replace(/\/$/, "").replace(/\/ws$/, "");
  if (trimmed.startsWith("wss://")) return `https://${trimmed.slice("wss://".length)}`;
  if (trimmed.startsWith("ws://")) return `http://${trimmed.slice("ws://".length)}`;
  if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
  return undefined;
}

function reasonText(reason: string): string {
  switch (reason) {
    case "peer_left":
      return "the phone ended the session";
    case "unknown_token":
    case "expired_token":
    case "room_closed":
      return "the relay no longer has this session";
    case "replaced":
      return "another client took over this session";
    case "ended":
    case "closed":
      return "session closed";
    default:
      return reason;
  }
}

export function endedMessage(reason: string, endedBy?: string): string {
  switch (reason) {
    case "peer_left":
      if (endedBy === "time_limit") {
        return "The phone's session time ran out (peer_left). Call pair to connect again. The owner can pick a longer session under Stay connected for.";
      }
      if (endedBy === "owner") return "The owner ended the session on the phone (peer_left). Call pair to connect again.";
      return "The phone ended the session (peer_left). Call pair to connect again.";
    case "unknown_token":
    case "expired_token":
    case "room_closed":
      return `The relay no longer has this session (${reason}). Call pair to connect again.`;
    case "replaced":
      return "Another client took over this session (replaced). Call pair to connect again.";
    case "connection_lost":
      return "Lost the relay before the phone finished pairing (connection_lost). Call pair again.";
    default:
      return `The session ended (${reason}). Call pair to connect again.`;
  }
}

const OPEN_UNTIL_CONFIRMED = new Set(["wait_for_request", "disconnect", "info", "ping", "done"]);

function needsConfirm(op: CommandOp): boolean {
  return !OPEN_UNTIL_CONFIRMED.has(op);
}

function describe(op: string, params: CommandParams, result: CommandResult): string {
  if (!result.ok) return result.error ?? "failed";
  const deferred = typeof result.result?.deferredMs === "number" && result.result.deferredMs > 0
    ? ` after waiting ${Math.round(Number(result.result.deferredMs) / 1000)} s`
    : "";
  switch (op) {
    case "type":
      return `${params.text?.length ?? 0} chars via ${String(result.result?.method ?? "set_text")}${deferred}`;
    case "tap":
      return `${params.x},${params.y}${deferred}`;
    case "swipe":
      return `${params.x1},${params.y1} → ${params.x2},${params.y2}${deferred}`;
    case "long_press":
      return `${params.x},${params.y}${deferred}`;
    case "drag":
      return `${params.x1},${params.y1} → ${params.x2},${params.y2}${deferred}`;
    case "pinch":
      return `${params.fromDistance}→${params.toDistance} px${deferred}`;
    case "press":
      return `${String(params.key)}${deferred}`;
    case "open_app":
      return `${String(params.packageName)}${deferred}`;
    case "open_settings":
      return `${String(params.name)}${deferred}`;
    case "screenshot":
      return "jpeg";
    case "ui_tree":
      return "tree";
    case "wait_idle":
      return `${result.result?.settled === true ? "settled" : "busy"} ${Number(result.result?.waitedMs ?? 0)} ms`;
    case "speak":
    case "ask_user":
    case "done":
      return `${params.text?.length ?? 0} chars`;
    case "confirm":
      return result.result?.accepted === true ? "accepted" : "declined";
    case "wait_for_request": {
      const heard = result.result?.text;
      return typeof heard === "string" ? `${heard.length} chars` : "empty";
    }
    default:
      return op;
  }
}
