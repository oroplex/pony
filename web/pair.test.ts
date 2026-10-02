import { describe, expect, it } from "vitest";

import { pairPageLink, parsePairingInput, type PairingPayload } from "@pony/shared";

import {
  ANDROID_PACKAGE,
  backupLink,
  hasPairing,
  intentUrl,
  isExpired,
  playStoreUrl,
  readExp,
  readLocationExp,
  readLocationParams,
  readParams,
} from "./pair.js";

const payload: PairingPayload = {
  v: 1,
  relay: "wss://relay.pony.karlmagendavid.com",
  token: "cd".repeat(32),
  pk: "z_0qekXbJeYuSG-eeLL65tW9xFgQxAHZFosjY6MRlSk",
};

/** The page only ever sees the query string the shared link produced. */
function queryFrom(link: string): string {
  return new URL(link).search;
}

describe("pair page builder", () => {
  it("reads the fields the shared pairPageLink wrote", () => {
    const fields = readParams(queryFrom(pairPageLink(payload, "grokbot")));
    expect(fields).toEqual({
      v: "1",
      relay: payload.relay,
      token: payload.token,
      pk: payload.pk,
      client: "grokbot",
    });
  });

  it("drops missing and empty fields", () => {
    const fields = readParams("?v=1&relay=&token=cd");
    expect(fields).toEqual({ v: "1", token: "cd" });
    expect(hasPairing(fields)).toBe(false);
  });

  it("builds an intent:// that hands off to the Pony package with a store fallback", () => {
    const fields = readParams(queryFrom(pairPageLink(payload)));
    const url = intentUrl(fields);
    expect(url.startsWith("intent://pair?")).toBe(true);
    expect(url).toContain(`package=${ANDROID_PACKAGE}`);
    expect(url).toContain("scheme=pony");
    expect(url).toContain(`S.browser_fallback_url=${encodeURIComponent(playStoreUrl())}`);
    expect(url.endsWith(";end")).toBe(true);
  });

  it("backup pony:// link parses back to the original payload via the real protocol", () => {
    const fields = readParams(queryFrom(pairPageLink(payload)));
    expect(parsePairingInput(backupLink(fields))).toEqual(payload);
  });

  it("recognizes a complete pairing", () => {
    expect(hasPairing(readParams(queryFrom(pairPageLink(payload))))).toBe(true);
  });
});

describe("pair page fragment and expiry", () => {
  const token = payload.token;
  const tapLink =
    `https://download.pony.karlmagendavid.com/pair#v=1` +
    `&relay=${encodeURIComponent(payload.relay)}&token=${token}&pk=${payload.pk}`;

  it("reads the pairing fields out of the fragment the tap link uses", () => {
    const loc = new URL(`${tapLink}&exp=${Date.now() + 60_000}`);
    const fields = readLocationParams(loc);
    expect(hasPairing(fields)).toBe(true);
    expect(fields).toEqual({ v: "1", relay: payload.relay, token, pk: payload.pk });
  });

  it("merges query and fragment, letting the fragment win", () => {
    const loc = { search: "?v=1&relay=ws://old", hash: `#relay=${encodeURIComponent(payload.relay)}&token=${token}&pk=${payload.pk}` };
    expect(readLocationParams(loc)).toEqual({ v: "1", relay: payload.relay, token, pk: payload.pk });
  });

  it("reads exp from either the fragment or the query", () => {
    expect(readExp("#exp=1700000000")).toBe(1700000000);
    expect(readExp("?exp=1700000000000")).toBe(1700000000000);
    expect(readExp("#v=1")).toBeNull();
    expect(readLocationExp({ search: "", hash: "#exp=42" })).toBe(42);
    expect(readLocationExp({ search: "?exp=99", hash: "#v=1" })).toBe(99);
  });

  it("treats a past exp as expired, in seconds or millis, and a future one as live", () => {
    expect(isExpired(1000)).toBe(true); // epoch seconds, long ago
    expect(isExpired(1_000_000_000_000)).toBe(true); // epoch millis (year 2001), still past
    expect(isExpired(Math.floor(Date.now() / 1000) + 600)).toBe(false);
    expect(isExpired(Date.now() + 600_000)).toBe(false);
    expect(isExpired(null)).toBe(false);
  });

  it("the tap link still parses through the shared protocol", () => {
    expect(parsePairingInput(tapLink)).toEqual(payload);
  });
});
