/** Wire protocol for Pony Companion. Keep in sync with the Kotlin `proto` package. */

export const PROTOCOL_VERSION = 2;
/** Oldest pairing payload this tree still accepts (0.6.4 and earlier). */
export const MIN_SUPPORTED_PROTOCOL = 1;
/** Shown when the other side is too old for this protocol. */
export const UPDATE_PONY = "Please update Pony to 0.6.5 or later.";
/** How long a pairing token stays valid after it's shown. Raised to 15 minutes so there's time to scan. */
export const PAIRING_TTL_MS = 15 * 60 * 1000;
export const SESSION_TTL_MS = 30 * 60 * 1000;
export const HKDF_INFO = "pony-companion-v1";
export const AEAD_AAD = "pony-v1";
export const AEAD_AAD_V2 = "pony-v2";
export const SAFETY_INFO = "pony-safety";
/**
 * Default long-poll window for `wait_for_request`. Kept short so a missed reply
 * re-polls in seconds instead of after a minute (a stale 65 s wait looked like a
 * dropped link and triggered a reconnect). The phone also heartbeats during the
 * wait so the client can tell a quiet owner from a dead link.
 */
export const REQUEST_POLL_MS = 25_000;
/** Permanent default relay. Override with `--relay` or `PONY_RELAY` for a private or Tailscale relay. */
export const DEFAULT_RELAY = "https://relay.pony.karlmagendavid.com";
/** Host serving the pairing hand-off page, for when the QR is on the phone itself. */
export const DEFAULT_PAIR_PAGE = "https://download.pony.karlmagendavid.com/pair.html";

export type Role = "bot" | "phone";

export interface PairingPayload {
  v: number;
  relay: string;
  token: string;
  pk: string;
}

/**
 * `resume: true` says this peer can rejoin. When both peers say so, an
 * established room survives a dropped socket: the other side gets `peer_away`
 * instead of `peer_left`, and a later hello with the same token rejoins it.
 * `bye` ends the room for both sides.
 */
export type RelayInbound =
  | { type: "hello"; role: Role; token: string; client?: string; resume?: boolean }
  | { type: "fwd"; data: string }
  | { type: "bye" };

export type RelayOutbound =
  | { type: "waiting" }
  | { type: "ready"; role: Role; client?: string; resumed?: boolean }
  | { type: "fwd"; data: string }
  | { type: "peer_away" }
  | { type: "peer_left" }
  | { type: "error"; reason: string };

export type CommandOp =
  | "screenshot"
  | "tap"
  | "swipe"
  | "long_press"
  | "drag"
  | "pinch"
  | "type"
  | "press"
  | "open_app"
  | "open_settings"
  | "ui_tree"
  | "wait_idle"
  | "ping"
  | "disconnect"
  | "wait_for_request"
  | "speak"
  | "ask_user"
  | "confirm"
  | "done"
  | "info"
  | "cancel"
  | "confirmed";

export type PressKey = "back" | "home" | "recents" | "enter" | "search" | "go" | "send" | "next" | "done";

/**
 * `type` replaces the whole field by default. Pass `mode: "insert"` to type at the
 * caret, or `mode: "append"` to add at the end.
 * The phone replies with result.method: `ime` (Pony keyboard), `set_text`, `paste`, or `key_events`.
 * `paste` happens only when the owner opted in. `key_events` is a last resort and includes result.warn.
 * Password fields return error `password_field`. `ime_disabled` and `ime_required` mean the Pony keyboard is not ready.
 * `press` with an IME-action key (`enter`, `search`, `go`, `send`, `next`, `done`) submits the focused field
 * through its editor action (search/go/send/…), falling back to the Enter or Search key.
 * `long_press` holds at `(x, y)`; `drag` presses at `(x1, y1)`, moves to `(x2, y2)`, and releases as one slow stroke;
 * `pinch` moves two fingers around `(x, y)` from `fromDistance` to `toDistance` apart to zoom.
 */
export interface CommandParams {
  x?: number;
  y?: number;
  x1?: number;
  y1?: number;
  x2?: number;
  y2?: number;
  durationMs?: number;
  /** For `pinch`: how far apart the two fingers start, in pixels. */
  fromDistance?: number;
  /** For `pinch`: how far apart the two fingers end, in pixels. Larger than `fromDistance` zooms in. */
  toDistance?: number;
  text?: string;
  /** For `type`: replace the whole field (default), insert at the caret, or append at the end. */
  mode?: "insert" | "replace" | "append";
  /**
   * When the connector sent this command (unix ms). The phone drops it once
   * [ttlMs] has passed, so a tap that timed out on this side cannot run later.
   */
  issuedAt?: number;
  /** How long the phone may hold this command before reporting `expired`. */
  ttlMs?: number;
  /**
   * Foreground package the tap was aimed at (from the last screenshot / ui_tree).
   * The phone refuses with `screen_changed` if something else is in front.
   */
  screenPkg?: string;
  screenActivity?: string;
  key?: PressKey;
  packageName?: string;
  /**
   * For `open_settings`: the settings screen to jump to — a friendly name like
   * `input_method`, `keyboard_settings`, `languages`, `voice_input`, or
   * `app_details` (with `packageName`), or a raw `android.settings.*` action.
   */
  name?: string;
  /** Phone-side wait for `wait_for_request`, or the settle timeout for `wait_idle`. For `wait_for_request` the client timeout is longer than this. */
  timeoutMs?: number;
  /**
   * Where the action runs. Omitted means the phone setting (background on by default).
   * `display` wins over `background` when both are set.
   */
  background?: boolean;
  display?: "main" | "background";
  /** For `done`: false when the task didn't succeed. */
  ok?: boolean;
  /** For `done`: the requestId it finishes. The phone ignores a `done` for a task that is no longer current. */
  ref?: string;
  /**
   * For `wait_for_request`: a standing listener rather than the assistant
   * coming back for more. It doesn't finish the current task or lift a Stop.
   */
  listen?: boolean;
  /**
   * For a standing `wait_for_request`: the last requestId this listener
   * received. If the phone handed out a later one that never got here, it
   * hands that one out again. Listeners drop repeats by requestId.
   */
  ack?: string;
}

/**
 * The phone sends `{kind:"evt", op:"progress", result: ProgressEvent}` while a
 * command waits, for example for a call screen to close. A client extends that
 * request's timeout to at least `limitMs` instead of giving up.
 */
export interface ProgressEvent {
  ref: string;
  state: "deferred";
  reason: "call_ui_foreground" | "covered_by_popup" | string;
  waitedMs: number;
  limitMs: number;
}

/**
 * Sent as `{kind:"evt", op:"ended", result: EndedEvent}` just before a side
 * says `bye`, so the other side can tell the owner why the session ended.
 */
export interface EndedEvent {
  reason: "time_limit" | "owner" | "assistant" | string;
}

export interface AppMessage {
  id: string;
  kind: "req" | "res" | "evt";
  op?: CommandOp | "progress" | "ended" | "confirmed";
  params?: CommandParams;
  ok?: boolean;
  error?: string;
  result?: Record<string, unknown>;
  /** Bound into the v2 AEAD AAD. Omitted on protocol v1 frames. */
  seq?: number;
}

export function encodePairing(payload: PairingPayload): string {
  return JSON.stringify(payload);
}

/** Deep link the phone can open or paste. The QR itself stays the JSON payload. */
export function pairingLink(payload: PairingPayload, client?: string): string {
  return `pony://pair?${pairingQuery(payload, client)}`;
}

/**
 * An https link to the pairing hand-off page, for when the QR is on the same
 * phone that would scan it. The page re-opens these fields in Pony through an
 * `intent://` hand-off, with the `pony://pair` link as a backup. Carries the
 * same fields as {@link pairingLink}, so either link pairs the same session.
 */
export function pairPageLink(payload: PairingPayload, client?: string, base: string = DEFAULT_PAIR_PAGE): string {
  const trimmed = base.trim();
  const hash = pairingQuery(payload, client);
  try {
    const url = new URL(trimmed);
    url.hash = hash;
    return url.toString();
  } catch {
    const without = trimmed.replace(/#.*$/, "").replace(/\?.*$/, "");
    return `${without}#${hash}`;
  }
}

function pairingQuery(payload: PairingPayload, client?: string): string {
  const params = new URLSearchParams();
  params.set("v", String(payload.v));
  params.set("relay", payload.relay);
  params.set("token", payload.token);
  params.set("pk", payload.pk);
  if (client) params.set("client", client);
  return params.toString();
}

/** `wss://host` from an http(s) or ws(s) origin, without a trailing `/ws`. */
export function relayWsOrigin(relay: string): string {
  const trimmed = relay.trim().replace(/\/$/, "").replace(/\/ws$/, "");
  if (trimmed.startsWith("https://")) return `wss://${trimmed.slice("https://".length)}`;
  if (trimmed.startsWith("http://")) return `ws://${trimmed.slice("http://".length)}`;
  if (trimmed.startsWith("wss://") || trimmed.startsWith("ws://")) return trimmed;
  return `wss://${trimmed}`;
}

export function parsePairingInput(raw: string): PairingPayload {
  const trimmed = raw.trim();
  if (trimmed.startsWith("pony:")) return parsePairingLink(trimmed);
  if (/^https?:\/\//i.test(trimmed)) return parseHttpPairLink(trimmed);
  return parsePairing(trimmed);
}

export function parsePairingLink(raw: string): PairingPayload {
  let url: URL;
  try {
    url = new URL(raw.trim());
  } catch {
    throw new Error("not a pony pairing link");
  }
  if (url.protocol !== "pony:" || url.hostname !== "pair") {
    throw new Error("not a pony pairing link");
  }
  return parsePairing(
    JSON.stringify({
      v: Number(url.searchParams.get("v")),
      relay: url.searchParams.get("relay"),
      token: url.searchParams.get("token"),
      pk: url.searchParams.get("pk"),
    }),
  );
}

/**
 * Parses the https hand-off link, e.g.
 * `https://download.pony…/pair#v=1&relay=…&token=…&pk=…&exp=…`.
 * The tap link carries its fields in the fragment; a `?query` form works too.
 * An extra `exp` is ignored here — callers that care about expiry read it
 * separately — so a link with a future or no expiry parses the same.
 */
export function parseHttpPairLink(raw: string): PairingPayload {
  let url: URL;
  try {
    url = new URL(raw.trim());
  } catch {
    throw new Error("not a pony pairing link");
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("not a pony pairing link");
  }
  // The tap link is `/pair`; the hand-off page is served as `/pair.html`. Treat
  // both (with any trailing slash) as the pairing route.
  if (url.pathname.replace(/\/+$/, "").replace(/\.html?$/i, "") !== "/pair") {
    throw new Error("not a pony pairing link");
  }
  const fields = new URLSearchParams(url.search);
  if (url.hash) {
    for (const [key, value] of new URLSearchParams(url.hash.replace(/^#/, ""))) {
      fields.set(key, value);
    }
  }
  return parsePairing(
    JSON.stringify({
      v: Number(fields.get("v")),
      relay: fields.get("relay"),
      token: fields.get("token"),
      pk: fields.get("pk"),
    }),
  );
}

export function parsePairing(raw: string): PairingPayload {
  const parsed = JSON.parse(raw) as Partial<PairingPayload>;
  if (typeof parsed.v !== "number" || !Number.isInteger(parsed.v) || parsed.v < MIN_SUPPORTED_PROTOCOL) {
    throw new Error("unsupported pairing version");
  }
  if (parsed.v > PROTOCOL_VERSION) {
    throw new Error(UPDATE_PONY);
  }
  if (!parsed.relay || !parsed.token || !parsed.pk) {
    throw new Error("pairing payload missing relay, token, or pk");
  }
  if (!/^[0-9a-f]{64}$/i.test(parsed.token)) {
    throw new Error("pairing token must be 32 bytes hex");
  }
  return {
    v: parsed.v,
    relay: parsed.relay,
    token: parsed.token.toLowerCase(),
    pk: parsed.pk,
  };
}

/** True when this pairing / handshake uses v2 AEAD counters. */
export function usesReplayProtection(version: number | undefined | null): boolean {
  return (version ?? 0) >= 2;
}

export function newMessageId(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
