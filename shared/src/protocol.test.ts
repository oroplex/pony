import { describe, expect, it } from "vitest";

import {
  DEFAULT_PAIR_PAGE,
  PROTOCOL_VERSION,
  UPDATE_PONY,
  pairPageLink,
  pairingLink,
  parsePairing,
  parsePairingInput,
  type PairingPayload,
} from "./protocol.ts";

const payload: PairingPayload = {
  v: PROTOCOL_VERSION,
  relay: "ws://192.168.1.20:8787",
  token: "ab".repeat(32),
  pk: "z_0qekXbJeYuSG-eeLL65tW9xFgQxAHZFosjY6MRlSk",
};

function hashParams(url: string): URLSearchParams {
  return new URLSearchParams(new URL(url).hash.replace(/^#/, ""));
}

describe("pairing link", () => {
  it("round-trips a pony://pair link without changing the QR JSON", () => {
    const link = pairingLink(payload);
    expect(link.startsWith("pony://pair?")).toBe(true);
    expect(parsePairingInput(link)).toEqual(payload);
    expect(parsePairing(JSON.stringify(payload))).toEqual(payload);
  });

  it("rejects a link that is missing the token", () => {
    expect(() => parsePairingInput("pony://pair?v=1&relay=ws://x&pk=abc")).toThrow();
  });

  it("parses the https tap link with its fields in the fragment", () => {
    const token = "ab".repeat(32);
    const link =
      `https://download.pony.karlmagendavid.com/pair#v=1` +
      `&relay=${encodeURIComponent("wss://relay.pony.karlmagendavid.com")}` +
      `&token=${token}&pk=z_0qekXbJeYuSG-eeLL65tW9xFgQxAHZFosjY6MRlSk&exp=${Date.now() + 60_000}`;
    expect(parsePairingInput(link)).toEqual({
      v: 1,
      relay: "wss://relay.pony.karlmagendavid.com",
      token,
      pk: "z_0qekXbJeYuSG-eeLL65tW9xFgQxAHZFosjY6MRlSk",
    });
  });

  it("parses the https pair page with its fields in the fragment", () => {
    expect(parsePairingInput(pairPageLink(payload))).toEqual(payload);
  });

  it("still parses a legacy query-string pair page", () => {
    const token = "ab".repeat(32);
    const legacy =
      `https://download.pony.karlmagendavid.com/pair.html?v=1&relay=wss://relay.example&token=${token}&pk=abc`;
    expect(parsePairingInput(legacy)).toEqual({
      v: 1,
      relay: "wss://relay.example",
      token,
      pk: "abc",
    });
  });

  it("rejects an https url that is not the /pair path", () => {
    const token = "ab".repeat(32);
    expect(() => parsePairingInput(`https://example.com/other#v=1&token=${token}`)).toThrow();
  });

  it("accepts a v1 payload and refuses a future version with a please-update error", () => {
    const token = "ab".repeat(32);
    expect(parsePairing(JSON.stringify({ v: 1, relay: "ws://x", token, pk: "abc" })).v).toBe(1);
    expect(() => parsePairing(JSON.stringify({ v: 99, relay: "ws://x", token, pk: "abc" }))).toThrow(UPDATE_PONY);
  });
});

describe("pair page link", () => {
  it("puts pairing fields in the fragment, not the query", () => {
    const page = pairPageLink(payload);
    expect(page.startsWith(`${DEFAULT_PAIR_PAGE}#`)).toBe(true);
    const fragment = hashParams(page);
    const qr = new URL(pairingLink(payload)).searchParams;
    for (const key of ["v", "relay", "token", "pk"]) {
      expect(fragment.get(key)).toBe(qr.get(key));
    }
    expect(new URL(page).searchParams.get("token")).toBeNull();
    expect(fragment.get("client")).toBeNull();
  });

  it("keeps the client name and respects a custom base", () => {
    const page = pairPageLink(payload, "grokbot", "https://pair.example/p");
    expect(page.startsWith("https://pair.example/p#")).toBe(true);
    expect(hashParams(page).get("client")).toBe("grokbot");
    expect(new URL(page).searchParams.get("client")).toBeNull();
  });
});
