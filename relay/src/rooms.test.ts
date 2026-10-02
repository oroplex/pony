import { describe, expect, it } from "vitest";

import { PAIRING_TTL_MS } from "@pony/shared";

import { PairingHub, type PeerSocket } from "./rooms.ts";

function fakeSocket(label: string): PeerSocket & { messages: string[] } {
  const messages: string[] = [];
  return {
    messages,
    send(data) {
      messages.push(data);
    },
    close() {
      messages.push(`${label}:closed`);
    },
  };
}

describe("PairingHub", () => {
  it("pairs a bot and a phone on a fresh token", () => {
    const hub = new PairingHub();
    const { token } = hub.createToken("aa".repeat(32));
    const bot = fakeSocket("bot");
    const phone = fakeSocket("phone");
    const a = hub.join(token, "bot", bot);
    const b = hub.join(token, "phone", phone);
    expect("room" in a && "room" in b).toBe(true);
    if ("room" in a && "room" in b) {
      expect(hub.bothPresent(a.room)).toBe(true);
      expect(hub.peerOf(token, "bot")).toBe(phone);
      expect(hub.peerOf(token, "phone")).toBe(bot);
    }
  });

  it("rejects an unknown token", () => {
    const hub = new PairingHub();
    const result = hub.join("bb".repeat(32), "bot", fakeSocket("bot"));
    expect(result).toEqual({ error: "unknown_token" });
  });

  it("rejects a second client for the same role", () => {
    const hub = new PairingHub();
    const { token } = hub.createToken("cc".repeat(32));
    hub.join(token, "bot", fakeSocket("bot1"));
    const second = hub.join(token, "bot", fakeSocket("bot2"));
    expect(second).toEqual({ error: "role_taken" });
  });

  it("expires unused tokens after the pairing TTL", () => {
    let now = 1_000_000;
    const hub = new PairingHub(PAIRING_TTL_MS, () => now);
    const { token } = hub.createToken("dd".repeat(32));
    now += PAIRING_TTL_MS + 1;
    const result = hub.join(token, "phone", fakeSocket("phone"));
    expect(result).toEqual({ error: "expired_token" });
  });

  it("still accepts a join just before expiry", () => {
    let now = 5_000;
    const hub = new PairingHub(PAIRING_TTL_MS, () => now);
    const { token } = hub.createToken("ee".repeat(32));
    now += PAIRING_TTL_MS - 1;
    const result = hub.join(token, "bot", fakeSocket("bot"));
    expect("room" in result).toBe(true);
  });

  it("drops the room when either peer leaves", () => {
    const hub = new PairingHub();
    const { token } = hub.createToken("ff".repeat(32));
    const bot = fakeSocket("bot");
    const phone = fakeSocket("phone");
    hub.join(token, "bot", bot);
    hub.join(token, "phone", phone);
    hub.leave(token, "bot", bot);
    expect(hub.peerOf(token, "phone")).toBeUndefined();
    expect(hub.join(token, "bot", fakeSocket("bot2"))).toEqual({ error: "unknown_token" });
  });
});

describe("PairingHub resume", () => {
  function established(hub: PairingHub, token: string) {
    hub.createToken(token);
    const bot = fakeSocket("bot");
    const phone = fakeSocket("phone");
    hub.join(token, "bot", bot, true);
    hub.join(token, "phone", phone, true);
    return { bot, phone };
  }

  it("holds an established room for a peer that dropped when both can rejoin", () => {
    const hub = new PairingHub();
    const token = "a1".repeat(32);
    const { bot, phone } = established(hub, token);
    const left = hub.leave(token, "phone", phone);
    expect(left).toMatchObject({ away: true, peer: bot });
    expect(hub.peerOf(token, "bot")).toBeUndefined();

    const back = fakeSocket("phone2");
    const joined = hub.join(token, "phone", back, true);
    expect(joined).toMatchObject({ resumed: true });
    expect(hub.peerOf(token, "bot")).toBe(back);
  });

  it("keeps the legacy behavior when only one side can rejoin", () => {
    const hub = new PairingHub();
    const token = "a2".repeat(32);
    hub.createToken(token);
    const bot = fakeSocket("bot");
    const phone = fakeSocket("phone");
    hub.join(token, "bot", bot, true);
    hub.join(token, "phone", phone, false);
    expect(hub.leave(token, "bot", bot)).toMatchObject({ away: false, peer: phone });
    expect(hub.join(token, "bot", fakeSocket("bot2"), true)).toEqual({ error: "unknown_token" });
  });

  it("does not hold a room that never had both peers", () => {
    const hub = new PairingHub();
    const token = "a3".repeat(32);
    hub.createToken(token);
    const bot = fakeSocket("bot");
    hub.join(token, "bot", bot, true);
    expect(hub.leave(token, "bot", bot)).toBeUndefined();
    expect(hub.join(token, "bot", fakeSocket("bot2"), true)).toEqual({ error: "unknown_token" });
  });

  it("lets a rejoin replace a socket the relay hasn't noticed is dead", () => {
    const hub = new PairingHub();
    const token = "a4".repeat(32);
    const { phone } = established(hub, token);
    const fresh = fakeSocket("phone2");
    const joined = hub.join(token, "phone", fresh, true);
    expect(joined).toMatchObject({ resumed: true, replaced: phone });
    expect(hub.leave(token, "phone", phone)).toBeUndefined();
    expect(hub.peerOf(token, "bot")).toBe(fresh);
  });

  it("outlives the pairing TTL once established, then expires when empty past the grace period", () => {
    let now = 1_000;
    const hub = new PairingHub(PAIRING_TTL_MS, () => now, { resumeGraceMs: 60_000 });
    const token = "a5".repeat(32);
    const { bot, phone } = established(hub, token);
    now += PAIRING_TTL_MS * 3;
    hub.leave(token, "bot", bot);
    hub.leave(token, "phone", phone);
    now += 59_000;
    expect(hub.size()).toBe(1);
    const back = fakeSocket("bot2");
    expect(hub.join(token, "bot", back, true)).toMatchObject({ resumed: true });
    hub.leave(token, "bot", back);
    now += 60_001;
    expect(hub.size()).toBe(0);
  });

  it("closes rooms at the hard cap and hands back their sockets", () => {
    let now = 0;
    const hub = new PairingHub(PAIRING_TTL_MS, () => now, { maxRoomMs: 10_000 });
    const token = "a6".repeat(32);
    const { bot, phone } = established(hub, token);
    now += 10_001;
    expect(hub.gc()).toEqual([bot, phone]);
    expect(hub.join(token, "bot", fakeSocket("bot2"), true)).toEqual({ error: "unknown_token" });
  });

  it("ends a room for both sides on an explicit goodbye", () => {
    const hub = new PairingHub();
    const token = "a7".repeat(32);
    const { phone } = established(hub, token);
    expect(hub.end(token, "bot")).toBe(phone);
    expect(hub.join(token, "bot", fakeSocket("bot2"), true)).toEqual({ error: "unknown_token" });
  });
});
