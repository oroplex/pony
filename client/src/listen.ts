import { spawn } from "node:child_process";
import { chmodSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";

import { PAIRING_TTL_MS, REQUEST_POLL_MS, pairPageLink, pairingLink } from "@pony/shared";

import {
  ENDED_REASONS,
  PonySession,
  type CommandResult,
  type LinkState,
  type LinkStatus,
  type ReconnectOptions,
  type SavedSession,
} from "./session.ts";

export interface OwnerRequest {
  requestId: string;
  text: string;
  source: string;
  receivedAt: number;
}

/** `~/.pony/<name>`, or under `PONY_HOME`. */
export function defaultStatePath(name: string): string {
  const base = process.env.PONY_HOME?.trim() || join(homedir(), ".pony");
  return join(base, name);
}

/** A saved pairing on disk. The file holds the bot's private key, so it is written 0600. */
export class SessionFile {
  constructor(readonly path: string) {}

  load(): SavedSession | null {
    let raw: string;
    try {
      raw = readFileSync(this.path, "utf8");
    } catch {
      return null;
    }
    try {
      const parsed = JSON.parse(raw) as SavedSession;
      return parsed?.v === 1 && parsed.role === "bot" ? parsed : null;
    } catch {
      return null;
    }
  }

  save(saved: SavedSession): void {
    const dir = dirname(this.path);
    mkdirSync(dir, { recursive: true, mode: 0o700 });
    const tmp = `${this.path}.${process.pid}.tmp`;
    writeFileSync(tmp, `${JSON.stringify(saved, null, 2)}\n`, { mode: 0o600 });
    renameSync(tmp, this.path);
    try {
      chmodSync(this.path, 0o600);
    } catch {
      // Not every filesystem has POSIX modes.
    }
  }

  clear(): void {
    rmSync(this.path, { force: true });
  }
}

export interface ListenerOptions {
  onRequest: (request: OwnerRequest) => void | Promise<void>;
  /** How long each phone-side wait lasts. */
  pollMs?: number;
  onProblem?: (error: string) => void;
}

/**
 * Keeps one `wait_for_request` open on the phone at all times, so the phone
 * knows an assistant is listening and nothing the owner says is missed. The
 * polls are marked `listen`, so they don't finish tasks or lift a Stop.
 */
export class RequestListener {
  private active = false;
  private loop?: Promise<void>;
  private wake?: () => void;
  private readonly offLate: () => void;
  private lastId?: string;
  private readonly seen: string[] = [];

  constructor(
    private readonly session: PonySession,
    private readonly options: ListenerOptions,
  ) {
    this.offLate = session.onLateReply((op, result) => {
      if (op === "wait_for_request") void this.deliver(result);
    });
  }

  /** Stops for good, including replies that arrive late. */
  dispose(): void {
    this.stop();
    this.offLate();
  }

  get running(): boolean {
    return this.active;
  }

  start(): void {
    if (this.active) return;
    this.active = true;
    if (!this.loop) {
      this.loop = this.run().finally(() => {
        this.loop = undefined;
      });
    }
  }

  /** Stops asking for more. A poll already on the phone still delivers what it gets. */
  stop(): void {
    this.active = false;
    this.wake?.();
  }

  /** Resolves when the loop has exited. */
  async settled(): Promise<void> {
    await this.loop;
  }

  private async run(): Promise<void> {
    const pollMs = this.options.pollMs ?? REQUEST_POLL_MS;
    let failures = 0;
    while (this.active && this.session.state !== "ended") {
      if (!this.session.ready) {
        await this.session.untilReady(30_000);
        continue;
      }
      let result: CommandResult;
      try {
        result = await this.session.request(
          "wait_for_request",
          { timeoutMs: pollMs, listen: true, ...(this.lastId ? { ack: this.lastId } : {}) },
          pollMs + 15_000,
        );
      } catch (err) {
        failures += 1;
        this.options.onProblem?.(err instanceof Error ? err.message : String(err));
        await this.pause(Math.min(30_000, 1_000 * 2 ** failures));
        continue;
      }
      if (!result.ok) {
        if (result.error === "peer_away" || result.error === "connection_lost") continue;
        if (this.session.link().state === "ended") break;
        failures += 1;
        this.options.onProblem?.(result.error ?? "wait_for_request failed");
        await this.pause(Math.min(30_000, 1_000 * 2 ** failures));
        continue;
      }
      failures = 0;
      await this.deliver(result);
    }
  }

  private async deliver(result: CommandResult): Promise<void> {
    const body = result.result ?? {};
    if (!result.ok || body.empty === true || typeof body.text !== "string") return;
    const requestId = typeof body.requestId === "string" ? body.requestId : `req-${Date.now()}`;
    this.lastId = requestId;
    if (this.seen.includes(requestId)) return;
    this.seen.push(requestId);
    if (this.seen.length > 100) this.seen.shift();
    await this.options.onRequest({
      requestId,
      text: body.text,
      source: typeof body.source === "string" ? body.source : "phone",
      receivedAt: Date.now(),
    });
  }

  private pause(ms: number): Promise<void> {
    return new Promise((resolve) => {
      const timer = setTimeout(done, ms);
      timer.unref?.();
      function done() {
        clearTimeout(timer);
        resolve();
      }
      this.wake = done;
    });
  }
}

export type ListenEvent =
  | { type: "pairing"; link: string; pageLink: string; payload: string; expiresInMs: number }
  | { type: "paired"; safetyCode: string; resumed: boolean }
  | { type: "link"; state: LinkState; attempt?: number; retryInMs?: number; reason?: string }
  | { type: "request"; requestId: string; text: string; source: string; at: number }
  | { type: "reply"; requestId: string; ok: boolean; chars: number }
  | { type: "ended"; reason: string; endedBy?: string };

export interface ListenOptions {
  relayHttp: string;
  statePath: string;
  clientName?: string;
  /** Shell command run once per request. See [runExec]. */
  exec?: string;
  execTimeoutMs?: number;
  /** End any saved pairing and pair again. */
  fresh?: boolean;
  pollMs?: number;
  reconnect?: ReconnectOptions;
  /** Advertised in the pairing code. Defaults to [relayHttp]. */
  publicRelay?: string;
  /** Socket to dial. Defaults to the advertised relay. */
  socketUrl?: string;
  /** Machine-readable events. */
  emit?: (event: ListenEvent) => void;
  /** Lines for a person. */
  say?: (line: string) => void;
  /** Draws the pairing code, for example as a terminal QR. */
  showPairing?: (info: { payload: string; link: string; pageLink: string }) => void | Promise<void>;
}

export interface ListenHandle {
  readonly session: () => PonySession | undefined;
  /** Stops listening and leaves the pairing in place, so the next run picks it up. */
  stop(): Promise<void>;
  /** Ends the session on the phone and deletes the saved pairing. */
  forget(): Promise<void>;
  /** Resolves with the reason the session ended, or `stopped`. */
  readonly finished: Promise<string>;
}

/** Reasons that mean the saved pairing can never be used again. */
const GONE = new Set([...ENDED_REASONS, "ended"]);

/**
 * `pony-phone listen`: pairs once (or rejoins the saved pairing), stays
 * connected through network changes and restarts, and hands every request the
 * owner makes to [ListenOptions.exec] or to the event stream.
 */
export async function runListen(options: ListenOptions): Promise<ListenHandle> {
  const emit = options.emit ?? (() => undefined);
  const say = options.say ?? (() => undefined);
  const store = new SessionFile(options.statePath);
  let stopped = false;
  let session: PonySession | undefined;
  let listener: RequestListener | undefined;

  if (options.fresh) {
    const old = store.load();
    if (old) {
      const previous = PonySession.resumeBot(old, { reconnect: options.reconnect, socketUrl: options.socketUrl });
      await Promise.race([settle(previous), delay(4_000)]);
      await previous.end();
    }
    store.clear();
  }

  const report = (status: LinkStatus) => {
    emit(linkEvent(status));
    const line = statusLine(status);
    if (line) say(line);
  };

  const saved = store.load();
  if (saved) {
    session = PonySession.resumeBot(saved, { reconnect: options.reconnect, socketUrl: options.socketUrl });
    say("Rejoining your phone…");
    const early = session.onStatus((status) => {
      if (status.state === "reconnecting") report(status);
    });
    const first = await settle(session);
    early();
    if (first === "ended") {
      const reason = session.link().reason ?? "ended";
      store.clear();
      say(endedLine(reason));
      emit({ type: "ended", reason });
      return finishedHandle(reason);
    }
    emit({ type: "paired", safetyCode: session.safetyCode(), resumed: true });
    say(
      first === "ready"
        ? `Back with your phone. Safety code ${session.safetyCode()}.`
        : `Rejoined the session (safety code ${session.safetyCode()}). The phone isn't online right now; Pony will pick up when it is.`,
    );
  } else {
    session = await PonySession.createBot({
      relayHttp: options.relayHttp,
      clientName: options.clientName,
      reconnect: options.reconnect,
      publicRelay: options.publicRelay,
      socketUrl: options.socketUrl,
    });
    const payload = session.qrJson();
    const link = pairingLink(session.payload, linkClient(options.clientName));
    const pageLink = pairPageLink(session.payload, linkClient(options.clientName));
    emit({ type: "pairing", link, pageLink, payload, expiresInMs: PAIRING_TTL_MS });
    await options.showPairing?.({ payload, link, pageLink });
    try {
      await session.waitUntilReady(PAIRING_TTL_MS);
    } catch {
      session.close();
      const reason = "pairing_expired";
      say(`Nobody scanned the code in ${Math.round(PAIRING_TTL_MS / 60_000)} minutes. Run the command again for a new one.`);
      emit({ type: "ended", reason });
      return finishedHandle(reason);
    }
    store.save(session.save());
    emit({ type: "paired", safetyCode: session.safetyCode(), resumed: false });
    say(`Paired. Safety code ${session.safetyCode()}. Check that the phone shows the same code.`);
  }

  const live = session;
  const off = live.onStatus(report);

  let queue = Promise.resolve();
  listener = new RequestListener(live, {
    pollMs: options.pollMs,
    onProblem: (error) => say(`The phone didn't answer (${error}). Trying again.`),
    onRequest: (request) => {
      emit({ type: "request", requestId: request.requestId, text: request.text, source: request.source, at: request.receivedAt });
      say(`→ “${request.text}” (${request.source})`);
      if (!options.exec) return;
      const command = options.exec;
      queue = queue.then(() => answer(live, command, request, options.execTimeoutMs ?? 120_000, emit, say));
      return queue;
    },
  });
  listener.start();
  say(options.exec ? "Listening. Ask Pony something on your phone." : "Listening. What the owner asks for shows up here.");

  const finished = new Promise<string>((resolve) => {
    const check = (status: LinkStatus) => {
      if (status.state !== "ended") return;
      unsubscribe();
      off();
      listener?.dispose();
      const reason = stopped ? "stopped" : status.reason ?? "ended";
      if (!stopped && GONE.has(reason)) store.clear();
      if (!stopped) {
        say(endedLine(reason, status.endedBy));
        emit({ type: "ended", reason, ...(status.endedBy ? { endedBy: status.endedBy } : {}) });
      }
      resolve(reason);
    };
    const unsubscribe = live.onStatus(check);
    check(live.link());
  });

  return {
    session: () => live,
    finished,
    async stop() {
      stopped = true;
      listener?.dispose();
      live.close();
      await finished;
    },
    async forget() {
      stopped = true;
      listener?.dispose();
      await live.end();
      store.clear();
      await finished;
    },
  };
}

function finishedHandle(reason: string): ListenHandle {
  return {
    session: () => undefined,
    finished: Promise.resolve(reason),
    async stop() {},
    async forget() {},
  };
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms).unref?.());
}

/** First of ready, away, or ended. */
function settle(session: PonySession): Promise<LinkState> {
  const now = session.state;
  if (now === "ready" || now === "away" || now === "ended") return Promise.resolve(now);
  return new Promise((resolve) => {
    const off = session.onStatus((status) => {
      if (status.state === "ready" || status.state === "away" || status.state === "ended") {
        off();
        resolve(status.state);
      }
    });
  });
}

async function answer(
  session: PonySession,
  command: string,
  request: OwnerRequest,
  timeoutMs: number,
  emit: (event: ListenEvent) => void,
  say: (line: string) => void,
): Promise<void> {
  const reply = await runExec(command, request, timeoutMs);
  const result = await session
    .request("done", { ref: request.requestId, ok: reply.ok, text: reply.text }, 15_000)
    .catch((err: unknown) => ({ ok: false, error: err instanceof Error ? err.message : String(err) }) as CommandResult);
  emit({ type: "reply", requestId: request.requestId, ok: reply.ok, chars: reply.text.length });
  if (!result.ok) say(`Couldn't send the reply to the phone (${result.error ?? "failed"}).`);
  else say(reply.ok ? `← ${reply.text ? `“${reply.text}”` : "done"}` : `← failed: ${reply.text}`);
}

export interface ExecReply {
  ok: boolean;
  /** What the phone shows and, for spoken requests, says. */
  text: string;
}

/**
 * Runs [command] through the shell with the request in `PONY_REQUEST_TEXT`,
 * `PONY_REQUEST_ID`, and `PONY_REQUEST_SOURCE`, and on stdin. The text is
 * never pasted into the command line. Exit 0 is success. The reply is stdout,
 * or stderr on failure, trimmed to one short paragraph.
 */
export function runExec(command: string, request: OwnerRequest, timeoutMs = 120_000): Promise<ExecReply> {
  return new Promise((resolve) => {
    const child = spawn(command, {
      shell: true,
      env: {
        ...process.env,
        PONY_REQUEST_ID: request.requestId,
        PONY_REQUEST_TEXT: request.text,
        PONY_REQUEST_SOURCE: request.source,
      },
      stdio: ["pipe", "pipe", "pipe"],
    });
    let out = "";
    let err = "";
    let settled = false;
    const finish = (reply: ExecReply) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolve(reply);
    };
    const timer = setTimeout(() => {
      child.kill("SIGTERM");
      finish({ ok: false, text: "That took too long, so Pony stopped waiting." });
    }, timeoutMs);
    child.stdout.on("data", (chunk: Buffer) => {
      if (out.length < 64_000) out += chunk.toString("utf8");
    });
    child.stderr.on("data", (chunk: Buffer) => {
      if (err.length < 16_000) err += chunk.toString("utf8");
    });
    child.on("error", (e) => finish({ ok: false, text: `Couldn't run the command: ${e.message}` }));
    child.on("close", (code) => {
      const ok = code === 0;
      const text = summarize(ok ? out : err || out) || (ok ? "" : `The command failed (exit ${code}).`);
      finish({ ok, text });
    });
    child.stdin.on("error", () => undefined);
    child.stdin.end(`${request.text}\n`);
  });
}

function summarize(text: string): string {
  const flat = text.replace(/\s+/g, " ").trim();
  return flat.length > 600 ? `${flat.slice(0, 597)}…` : flat;
}

function linkEvent(status: LinkStatus): ListenEvent {
  return {
    type: "link",
    state: status.state,
    ...(status.attempt ? { attempt: status.attempt } : {}),
    ...(status.nextRetryAt ? { retryInMs: Math.max(0, status.nextRetryAt - Date.now()) } : {}),
    ...(status.reason ? { reason: status.reason } : {}),
  };
}

function statusLine(status: LinkStatus): string | undefined {
  switch (status.state) {
    case "ready":
      return status.resumed ? "Your phone is back." : undefined;
    case "away":
      return "The phone dropped off. Pony is holding the session until it's back.";
    case "reconnecting": {
      const secs = status.nextRetryAt ? Math.max(1, Math.round((status.nextRetryAt - Date.now()) / 1000)) : 1;
      return `Lost the relay. Trying again in ${secs} s (attempt ${status.attempt}).`;
    }
    default:
      return undefined;
  }
}

function endedLine(reason: string, endedBy?: string): string {
  switch (reason) {
    case "peer_left":
      if (endedBy === "time_limit") {
        return "The phone's session time ran out. Run pony-phone listen again to pair. To stay paired, set Stay connected for to Until I disconnect on the phone.";
      }
      return "The phone ended the session. Run pony-phone listen again to pair.";
    case "unknown_token":
    case "expired_token":
    case "room_closed":
      return "The relay no longer has this session. Run pony-phone listen again to pair.";
    case "replaced":
      return "Another copy of this listener took over the session.";
    case "ended":
      return "Session ended.";
    default:
      return `The session ended (${reason}).`;
  }
}

function linkClient(name?: string): string | undefined {
  const trimmed = name?.trim();
  if (!trimmed) return undefined;
  if (trimmed.toLowerCase() === "grok bot") return "grokbot";
  return trimmed.slice(0, 40);
}
