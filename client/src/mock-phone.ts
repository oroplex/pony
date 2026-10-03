import { actionExpired, actionTtlMs, displayChoice, effectiveIssuedAt, screenChanged, type CommandParams } from "@pony/shared";

import type { AppMessage, CommandResult, PonySession } from "./session.ts";

export interface MockRequest {
  id: string;
  text: string;
  source: string;
}

export interface MockDone {
  ref?: string;
  text: string;
  ok: boolean;
}

/** In-memory phone used by tests and the scripted demo. */
export class MockPhone {
  focusedIsPassword = false;
  /** When set, screenshot replies ok with no image, to prove the harness never reports success without one. */
  screenshotBlank = false;
  /** When set, screenshot on the background display reports an empty hidden screen, as the phone does when nothing has launched there yet. */
  screenshotEmptyBackground = false;
  lastTyped?: string;
  lastSpoken?: string;
  lastQuestion?: string;
  /** What the owner said, oldest first. `wait_for_request` hands these out one at a time. */
  requests: MockRequest[] = [{ id: "req-1", text: "lock the door", source: "voice" }];
  finished: MockDone[] = [];
  /** Every command with its params, oldest first. */
  calls: Array<{ op: string; params: CommandParams }> = [];
  /** Reported by `info`. Null means the session lasts until someone disconnects. */
  sessionEndsAt: number | null = null;
  log: string[] = [];
  private readonly waiters = new Map<() => void, () => void>();
  private serial = 1;
  /** Handed to a standing listener and not acknowledged yet. */
  private unconfirmed?: MockRequest;

  /**
   * Answers the session's commands. Like the real phone, open waits stand
   * down when the assistant drops off, so they can't claim a request nobody
   * will receive.
   */
  attach(session: PonySession): void {
    session.onRequest = (msg) => this.handle(msg);
    session.onStatus((status) => {
      if (status.state !== "ready") this.cancelWaits();
    });
  }

  /** The owner asks for something, as if they typed or said it on the phone. */
  say(text: string, source = "typed"): MockRequest {
    this.serial += 1;
    const request = { id: `req-${this.serial}`, text, source };
    this.requests.push(request);
    for (const wake of [...this.waiters.keys()]) wake();
    return request;
  }

  /** Number of `wait_for_request` calls blocked right now. */
  get waiting(): number {
    return this.waiters.size;
  }

  /** Ends open waits as empty without handing out a request. */
  cancelWaits(): void {
    for (const cancel of [...this.waiters.values()]) cancel();
  }

  handle(msg: AppMessage): CommandResult | Promise<CommandResult> {
    const op = msg.op;
    const p = msg.params ?? {};
    this.log.push(op ?? "unknown");
    this.calls.push({ op: op ?? "unknown", params: p });
    if (op && op !== "wait_for_request" && actionExpired(effectiveIssuedAt(p.issuedAt, Date.now()), p.ttlMs, Date.now(), actionTtlMs(op))) {
      return { ok: false, error: "expired" };
    }

    switch (op) {
      case "ping":
        return { ok: true, result: { pong: true } };
      case "info":
        return {
          ok: true,
          result: {
            app: "Pony Companion (mock)",
            version: "0.6.3",
            features: ["resume", "tasks", "done", "deferral", "cover_check", "listen", "action_ttl"],
            sessionEndsAt: this.sessionEndsAt,
            now: Date.now(),
            ime: {
              current: "app.pony.companion/.input.PonyInputMethodService",
              ponyEnabled: true,
              ponySelected: true,
              ponyActive: false,
              ponyUsable: true,
            },
          },
        };
      case "screenshot":
        if (this.screenshotEmptyBackground && place(p).display === "background") {
          return { ok: false, error: "background_empty", result: { ...place(p) } };
        }
        if (this.screenshotBlank) {
          return { ok: true, result: { width: 1080, height: 2400, source: "mock", ...place(p) } };
        }
        return {
          ok: true,
          result: {
            jpeg_b64: tinyJpegB64(),
            width: 1080,
            height: 2400,
            source: "mock",
            foreground: "com.android.settings",
            ...place(p),
          },
        };
      case "tap":
        if (screenChanged(p.screenPkg, "com.android.settings")) {
          return { ok: false, error: "screen_changed", result: { foreground: "com.android.settings", screenPkg: p.screenPkg } };
        }
        return { ok: true, result: { x: p.x, y: p.y, foreground: "com.android.settings", ...place(p) } };
      case "swipe":
        return {
          ok: true,
          result: { x1: p.x1, y1: p.y1, x2: p.x2, y2: p.y2, durationMs: p.durationMs ?? 250, ...place(p) },
        };
      case "long_press":
        return { ok: true, result: { x: p.x, y: p.y, durationMs: p.durationMs ?? 600, ...place(p) } };
      case "drag":
        return {
          ok: true,
          result: { x1: p.x1, y1: p.y1, x2: p.x2, y2: p.y2, durationMs: p.durationMs ?? 600, ...place(p) },
        };
      case "pinch":
        return {
          ok: true,
          result: {
            x: p.x,
            y: p.y,
            fromDistance: p.fromDistance,
            toDistance: p.toDistance,
            durationMs: p.durationMs ?? 300,
            ...place(p),
          },
        };
      case "type":
        if (this.focusedIsPassword) {
          return { ok: false, error: "password_field" };
        }
        this.lastTyped =
          p.mode === "append" ? (this.lastTyped ?? "") + (p.text ?? "") : p.text;
        return {
          ok: true,
          result: { length: p.text?.length ?? 0, method: "set_text", mode: p.mode ?? "insert", ...place(p) },
        };
      case "press":
        return { ok: true, result: { key: p.key, ...place(p) } };
      case "open_app":
        return { ok: true, result: { packageName: p.packageName, ...place(p) } };
      case "open_settings":
        return { ok: true, result: { settings: p.name, packageName: p.packageName, ...place(p) } };
      case "ui_tree":
        return {
          ok: true,
          result: {
            ...place(p),
            foreground: "com.android.settings",
            tree: [
              "FrameLayout bounds=0,0,1080,2400",
              "  TextView \"Messages\" clickable bounds=40,80,400,140",
              this.focusedIsPassword
                ? "  EditText [password] focused bounds=40,200,1040,280"
                : "  EditText \"search\" focused bounds=40,200,1040,280",
            ].join("\n"),
          },
        };
      case "wait_idle":
        return { ok: true, result: { settled: true, waitedMs: 0, reason: "settled", ...place(p) } };
      case "disconnect":
        return { ok: true, result: { disconnected: true } };
      case "wait_for_request": {
        const standing = p.listen === true;
        if (standing && this.unconfirmed) {
          if (p.ack === this.unconfirmed.id) this.unconfirmed = undefined;
          else return handOut(this.unconfirmed);
        }
        return this.nextRequest(Math.min(Math.max(p.timeoutMs ?? 50_000, 0), 55_000), standing);
      }
      case "speak":
        this.lastSpoken = p.text;
        return { ok: true, result: { spoken: true } };
      case "ask_user":
        this.lastQuestion = p.text;
        return { ok: true, result: { text: "the blue one" } };
      case "confirm": {
        const accepted = !String(p.text ?? "").toLowerCase().includes("pay");
        return { ok: true, result: { accepted } };
      }
      case "done":
        this.finished.push({ ref: p.ref, text: p.text ?? "", ok: p.ok !== false });
        return { ok: true, result: { done: true } };
      default:
        return { ok: false, error: `unknown_op:${op}` };
    }
  }

  private nextRequest(timeoutMs: number, standing: boolean): CommandResult | Promise<CommandResult> {
    const take = (): CommandResult | undefined => {
      const request = this.requests.shift();
      if (!request) return undefined;
      if (standing) this.unconfirmed = request;
      return handOut(request);
    };
    const now = take();
    if (now) return now;
    return new Promise((resolve) => {
      const settle = (result: CommandResult) => {
        clearTimeout(timer);
        this.waiters.delete(wake);
        resolve(result);
      };
      const wake = () => {
        const got = take();
        if (got) settle(got);
      };
      const empty = () => settle({ ok: true, result: { empty: true } });
      const timer = setTimeout(empty, timeoutMs);
      timer.unref?.();
      this.waiters.set(wake, empty);
    });
  }
}

function handOut(request: MockRequest): CommandResult {
  return { ok: true, result: { requestId: request.id, text: request.text, source: request.source } };
}

/** Phone default is background on. The relay URL is not consulted. */
function place(p: CommandParams): { display: "main" | "background" } {
  return { display: displayChoice(true, p.background, p.display) };
}

function tinyJpegB64(): string {
  // 1x1 JPEG so demos have a real image payload without shipping fixtures.
  return "/9j/4AAQSkZJRgABAQAAAQABAAD/2wAAAQEB/9oADAMBAAIQAxAAAAGf/9k=";
}
