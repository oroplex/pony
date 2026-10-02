// Pure helpers for the Pony pairing hand-off page. No DOM here, so they can be
// unit-tested and the page (pair.html) can import them unchanged in the browser.
// Kept as plain ESM JavaScript so the download host serves it with no build step.

/** The Android package Pony pairing links hand off to. */
export const ANDROID_PACKAGE = "app.pony.companion";

/** The pairing fields carried in the page URL, in a stable order. */
export const PAIR_FIELDS = ["v", "relay", "token", "pk", "client"];

/**
 * Read the pairing fields out of a query string or a URL fragment. A leading
 * `?` or `#` is tolerated. Missing or empty values are dropped.
 */
export function readParams(source) {
  const params = new URLSearchParams(String(source ?? "").replace(/^[?#]/, ""));
  const out = {};
  for (const key of PAIR_FIELDS) {
    const value = params.get(key);
    if (value !== null && value !== "") out[key] = value;
  }
  return out;
}

/**
 * Merge the pairing fields from a location's query string and fragment. The
 * tap link carries them in the fragment, the hand-off page in the query; the
 * fragment wins when a field is in both.
 */
export function readLocationParams(loc) {
  return { ...readParams(loc?.search), ...readParams(loc?.hash) };
}

/**
 * The link's `exp` (epoch seconds or millis), or null. Read separately from the
 * pairing fields so an older link with no expiry still pairs.
 */
export function readExp(source) {
  const value = new URLSearchParams(String(source ?? "").replace(/^[?#]/, "")).get("exp");
  if (value === null || value === "") return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

/** The expiry from a location's fragment, falling back to its query string. */
export function readLocationExp(loc) {
  const fromHash = readExp(loc?.hash);
  return fromHash !== null ? fromHash : readExp(loc?.search);
}

/** True when `exp` (seconds or millis) is already in the past. */
export function isExpired(exp, nowMs = Date.now()) {
  if (exp === null || exp === undefined) return false;
  const ms = exp < 1e12 ? exp * 1000 : exp;
  return ms <= nowMs;
}

/** The Play Store page for Pony, used as the fallback when the app isn't installed. */
export function playStoreUrl() {
  return `https://play.google.com/store/apps/details?id=${ANDROID_PACKAGE}`;
}

/** The `pony://pair?…` deep link — the same scheme the QR uses, as a backup. */
export function backupLink(fields) {
  return `pony://pair?${pairQuery(fields)}`;
}

/**
 * An Android `intent://` URL that hands the pairing straight to Pony by package
 * name, so a tap opens the app's `pony://pair` handler directly. If Pony isn't
 * installed, Android follows the Play Store fallback instead of a dead link.
 */
export function intentUrl(fields) {
  const fallback = encodeURIComponent(playStoreUrl());
  return (
    `intent://pair?${pairQuery(fields)}` +
    `#Intent;scheme=pony;package=${ANDROID_PACKAGE};` +
    `S.browser_fallback_url=${fallback};end`
  );
}

/** The query string shared by both links, in [PAIR_FIELDS] order. */
export function pairQuery(fields) {
  const params = new URLSearchParams();
  for (const key of PAIR_FIELDS) {
    const value = fields?.[key];
    if (value !== undefined && value !== null && value !== "") params.set(key, String(value));
  }
  return params.toString();
}

/** True when the page has the minimum it needs to pair (relay, token, pk). */
export function hasPairing(fields) {
  return Boolean(fields && fields.relay && fields.token && fields.pk);
}
