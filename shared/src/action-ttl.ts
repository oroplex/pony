/** Per-action time-to-live. A command older than this must never run on the phone. */

export const ACTION_TTL_DEFAULT_MS = 15_000;
export const ACTION_TTL_OPEN_MS = 30_000;
export const ACTION_TTL_TYPE_MS = 45_000;
export const ACTION_TTL_IDLE_MS = 20_000;
/** How long the phone keeps a confirm/ask prompt up. */
export const OWNER_PROMPT_MS = 60_000;
/** Connector wait: longer than the phone prompt so a late "no" is never a timeout. */
export const OWNER_CLIENT_WAIT_MS = 70_000;
/** Ignore a client issuedAt that is more than this far from receive time (clock skew). */
export const ACTION_CLOCK_SKEW_MS = 120_000;

const TTL_BY_OP: Record<string, number> = {
  tap: ACTION_TTL_DEFAULT_MS,
  swipe: ACTION_TTL_DEFAULT_MS,
  long_press: ACTION_TTL_DEFAULT_MS,
  drag: ACTION_TTL_DEFAULT_MS,
  pinch: ACTION_TTL_DEFAULT_MS,
  press: ACTION_TTL_DEFAULT_MS,
  screenshot: ACTION_TTL_DEFAULT_MS,
  ui_tree: ACTION_TTL_DEFAULT_MS,
  ping: ACTION_TTL_DEFAULT_MS,
  info: ACTION_TTL_DEFAULT_MS,
  done: ACTION_TTL_DEFAULT_MS,
  speak: ACTION_TTL_DEFAULT_MS,
  disconnect: ACTION_TTL_DEFAULT_MS,
  type: ACTION_TTL_TYPE_MS,
  open_app: ACTION_TTL_OPEN_MS,
  open_settings: ACTION_TTL_OPEN_MS,
  wait_idle: ACTION_TTL_IDLE_MS,
  confirm: OWNER_CLIENT_WAIT_MS,
  ask_user: OWNER_CLIENT_WAIT_MS,
  wait_for_request: 0,
};

export function actionTtlMs(op: string | undefined | null): number {
  if (!op) return ACTION_TTL_DEFAULT_MS;
  return TTL_BY_OP[op] ?? ACTION_TTL_DEFAULT_MS;
}

/**
 * When the client didn't stamp the command, the phone uses the receive time
 * so a command that sat in its queue still expires.
 */
export function effectiveIssuedAt(clientIssuedAt: number | undefined | null, receivedAt: number): number {
  if (clientIssuedAt == null || !Number.isFinite(clientIssuedAt) || clientIssuedAt <= 0) return receivedAt;
  if (Math.abs(clientIssuedAt - receivedAt) > ACTION_CLOCK_SKEW_MS) return receivedAt;
  return clientIssuedAt;
}

export function actionExpired(
  issuedAt: number | undefined | null,
  ttlMs: number | undefined | null,
  now: number,
  fallbackTtlMs: number,
): boolean {
  const ttl = ttlMs == null || !Number.isFinite(ttlMs) ? fallbackTtlMs : ttlMs;
  if (ttl <= 0) return false;
  if (issuedAt == null || !Number.isFinite(issuedAt) || issuedAt <= 0) return false;
  return now - issuedAt > ttl;
}

/** True when a tap named a foreground package and the phone is now showing something else. */
export function screenChanged(expectedPkg: string | undefined | null, actualPkg: string | undefined | null): boolean {
  if (!expectedPkg || !actualPkg) return false;
  return expectedPkg !== actualPkg;
}
