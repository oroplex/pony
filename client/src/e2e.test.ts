import { afterEach, describe, expect, it } from "vitest";
import type { WebSocket } from "ws";

import { generateKeyPair } from "@pony/shared";

import { createRelay, type StartedRelay } from "../../relay/src/server.ts";

import { MockPhone } from "./mock-phone.ts";
import {
  PonySession,
  backoffDelay,
  type AppMessage,
  type LinkState,
  type ProgressEvent,
  type ReconnectOptions,
} from "./session.ts";

let relay: StartedRelay | undefined;
const sessions: PonySession[] = [];

afterEach(async () => {
  for (const s of sessions) s.close();
  sessions.length = 0;
  if (relay) {
    await relay.close();
    relay = undefined;
  }
});

const FAST: ReconnectOptions = { initialMs: 40, maxMs: 200 };

async function paired(
  opts: { bot?: ReconnectOptions; phone?: ReconnectOptions } = {},
): Promise<{ bot: PonySession; phone: MockPhone; phoneSession: PonySession }> {
  relay = await createRelay({ host: "127.0.0.1", port: 0 });
  const bot = await PonySession.createBot({ relayHttp: relay.url, reconnect: opts.bot });
  sessions.push(bot);
  const phone = new MockPhone();
  const phoneSession = await PonySession.createPhone(bot.qrJson(), undefined, { reconnect: opts.phone });
  sessions.push(phoneSession);
  phoneSession.onRequest = (msg) => phone.handle(msg);
  await Promise.all([bot.waitUntilReady(), phoneSession.waitUntilReady()]);
  return { bot, phone, phoneSession };
}

function reached(session: PonySession, state: LinkState, timeoutMs = 5_000): Promise<void> {
  if (session.state === state) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      off();
      reject(new Error(`never reached ${state}; at ${session.state}`));
    }, timeoutMs);
    const off = session.onStatus((status) => {
      if (status.state !== state) return;
      clearTimeout(timer);
      off();
      resolve();
    });
  });
}

/** Pulls the network out from under a session without a close handshake. */
function cutCable(session: PonySession): void {
  (session as unknown as { ws?: WebSocket }).ws?.terminate();
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

describe("bot client ↔ mock phone through the relay", () => {
  it("completes the handshake and matches safety codes", async () => {
    const { bot, phoneSession } = await paired();
    expect(bot.safetyCode()).toBe(phoneSession.safetyCode());
    expect(bot.safetyCode()).toMatch(/^\d{3}-\d{3}$/);
  });

  it("runs every MVP command over E2E-encrypted frames", async () => {
    const { bot, phone } = await paired();

    expect((await bot.request("ping")).ok).toBe(true);

    const shot = await bot.request("screenshot");
    expect(shot.ok).toBe(true);
    expect(String(shot.result?.jpeg_b64).length).toBeGreaterThan(20);
    expect(shot.result?.width).toBe(1080);

    expect((await bot.request("tap", { x: 10, y: 20 })).result).toMatchObject({ x: 10, y: 20 });
    expect(
      (await bot.request("swipe", { x1: 0, y1: 1, x2: 0, y2: 0, durationMs: 100 })).ok,
    ).toBe(true);
    expect(
      (await bot.request("long_press", { x: 10, y: 20, durationMs: 700 })).result,
    ).toMatchObject({ x: 10, y: 20, durationMs: 700 });
    expect(
      (await bot.request("drag", { x1: 1, y1: 2, x2: 3, y2: 4 })).result,
    ).toMatchObject({ x1: 1, y1: 2, x2: 3, y2: 4, durationMs: 600 });
    expect(
      (await bot.request("pinch", { x: 5, y: 6, fromDistance: 80, toDistance: 300 })).result,
    ).toMatchObject({ fromDistance: 80, toDistance: 300 });
    expect(
      (await bot.request("wait_idle", { timeoutMs: 1000 })).result,
    ).toMatchObject({ settled: true, reason: "settled" });
    const typed = await bot.request("type", { text: "hi" });
    expect(typed.ok).toBe(true);
    expect(typed.result).toMatchObject({ length: 2, method: "set_text" });
    expect(phone.lastTyped).toBe("hi");
    expect((await bot.request("press", { key: "back" })).result).toMatchObject({ key: "back" });
    expect(
      (await bot.request("open_app", { packageName: "com.android.settings" })).ok,
    ).toBe(true);
    expect(
      (await bot.request("open_settings", { name: "voice_input" })).result,
    ).toMatchObject({ settings: "voice_input" });
    const tree = await bot.request("ui_tree");
    expect(String(tree.result?.tree)).toContain("TextView");

    phone.focusedIsPassword = true;
    const blocked = await bot.request("type", { text: "hunter2" });
    expect(blocked).toEqual({ ok: false, error: "password_field" });
  });

  it("leaves ciphertext opaque to a relay sniffer", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const seen: string[] = [];
    const bot = await PonySession.createBot({ relayHttp: relay.url });
    sessions.push(bot);

    const phone = new MockPhone();
    const phoneSession = await PonySession.createPhone(bot.qrJson());
    sessions.push(phoneSession);
    phoneSession.onRequest = (msg) => {
      seen.push(JSON.stringify(msg));
      return phone.handle(msg);
    };
    await Promise.all([bot.waitUntilReady(), phoneSession.waitUntilReady()]);
    await bot.request("type", { text: "not-for-the-relay" });
    expect(seen.some((s) => s.includes("not-for-the-relay"))).toBe(true);
    expect(bot.qrJson()).not.toContain("not-for-the-relay");
  });
});

describe("sessions that survive dropped connections", () => {
  it("answers at once, not after a timeout, once the phone ends the session", async () => {
    const { bot, phoneSession } = await paired();
    phoneSession.onRequest = () => new Promise(() => undefined);
    const inFlight = bot.request("tap", { x: 1, y: 1 }, 10_000);
    await sleep(50);
    const started = Date.now();
    await phoneSession.end();
    expect(await inFlight).toEqual({ ok: false, error: "peer_left" });
    expect(await bot.request("screenshot", {}, 10_000)).toEqual({ ok: false, error: "peer_left" });
    expect(await bot.request("wait_for_request", {}, 10_000)).toEqual({ ok: false, error: "peer_left" });
    expect(Date.now() - started).toBeLessThan(1_500);
    expect(bot.state).toBe("ended");
    expect(bot.link().endedBy).toBe("owner");
  });

  it("hears why the phone ended the session", async () => {
    const { bot, phoneSession } = await paired();
    await phoneSession.end("time_limit");
    await reached(bot, "ended");
    expect(bot.link()).toMatchObject({ reason: "peer_left", endedBy: "time_limit" });
  });

  it("holds the session while the phone drops off, then carries on with the same keys", async () => {
    const { bot, phoneSession } = await paired({ bot: FAST, phone: FAST });
    const code = bot.safetyCode();
    const away = reached(bot, "away");
    cutCable(phoneSession);
    await away;
    await bot.waitUntilReady(5_000);
    expect(bot.link().resumed).toBe(true);
    expect((await bot.request("tap", { x: 3, y: 4 })).result).toMatchObject({ x: 3, y: 4 });
    expect(bot.safetyCode()).toBe(code);
  });

  it("says peer_away quickly while the phone is gone instead of hanging", async () => {
    const { bot, phoneSession } = await paired({ bot: { ...FAST, awayGraceMs: 100 }, phone: { initialMs: 60_000 } });
    const away = reached(bot, "away");
    cutCable(phoneSession);
    await away;
    const started = Date.now();
    expect(await bot.request("ping", {}, 10_000)).toEqual({ ok: false, error: "peer_away" });
    expect(Date.now() - started).toBeLessThan(1_000);
  });

  it("redials after its own connection drops and rejoins the room", async () => {
    const { bot } = await paired({ bot: FAST, phone: FAST });
    const reconnecting = reached(bot, "reconnecting");
    cutCable(bot);
    await reconnecting;
    expect(bot.link().attempt).toBeGreaterThanOrEqual(1);
    await bot.waitUntilReady(5_000);
    expect(bot.link().resumed).toBe(true);
    expect((await bot.request("ping")).ok).toBe(true);
  });

  it("fails the command in flight when its connection drops, then works after the rejoin", async () => {
    const { bot, phoneSession, phone } = await paired({ bot: FAST, phone: FAST });
    phoneSession.onRequest = (msg) => (msg.op === "tap" ? new Promise(() => undefined) : phone.handle(msg));
    const inFlight = bot.request("tap", { x: 1, y: 2 }, 10_000);
    await sleep(50);
    cutCable(bot);
    expect(await inFlight).toEqual({ ok: false, error: "connection_lost" });
    await bot.waitUntilReady(5_000);
    expect((await bot.request("ping")).ok).toBe(true);
  });

  it("gives a held command more time while the phone reports it is waiting", async () => {
    const { bot, phoneSession } = await paired();
    phoneSession.onRequest = async (msg) => {
      phoneSession.sendEvent("progress", {
        ref: msg.id,
        state: "deferred",
        reason: "call_ui_foreground",
        waitedMs: 0,
        limitMs: 2_000,
      });
      await sleep(500);
      return { ok: true, result: { deferredMs: 500 } };
    };
    const seen: ProgressEvent[] = [];
    const result = await bot.request("tap", { x: 1, y: 1 }, 150, { onProgress: (event) => seen.push(event) });
    expect(result).toEqual({ ok: true, result: { deferredMs: 500 } });
    expect(seen[0]).toMatchObject({ reason: "call_ui_foreground", limitMs: 2_000 });
  });

  it("still times out a command the phone never answers or mentions", async () => {
    const { bot, phoneSession } = await paired();
    phoneSession.onRequest = () => new Promise(() => undefined);
    await expect(bot.request("tap", { x: 1, y: 1 }, 150)).rejects.toThrow("timed out waiting for tap");
  });

  it("cancels a timed-out command so the phone does not run it later", async () => {
    const { bot, phoneSession } = await paired();
    const seen: AppMessage[] = [];
    phoneSession.onEvent = (msg) => seen.push(msg);
    phoneSession.onRequest = () => new Promise(() => undefined);
    await expect(bot.request("tap", { x: 1, y: 1 }, 150)).rejects.toThrow("timed out waiting for tap");
    await sleep(50);
    expect(seen.some((msg) => msg.op === "cancel" && typeof msg.result?.ref === "string")).toBe(true);
  });

  it("stamps issuedAt and ttlMs on every command", async () => {
    const { bot, phone } = await paired();
    const before = Date.now();
    await bot.request("ping");
    const ping = phone.calls.find((call) => call.op === "ping");
    expect(ping?.params.issuedAt).toBeGreaterThanOrEqual(before);
    expect(ping?.params.ttlMs).toBe(15_000);
  });

  it("drops a tap whose TTL has already passed", async () => {
    const { bot } = await paired();
    const result = await bot.request("tap", { x: 1, y: 1, issuedAt: Date.now() - 60_000, ttlMs: 15_000 });
    expect(result).toEqual({ ok: false, error: "expired" });
  });

  it("saves the pairing and rejoins it after a restart", async () => {
    const { bot, phoneSession } = await paired({ phone: FAST });
    const saved = bot.save();
    expect(saved).toMatchObject({ v: 1, role: "bot", payload: { token: bot.payload.token } });
    const away = reached(phoneSession, "away");
    bot.close();
    await away;

    const again = PonySession.resumeBot(saved, { reconnect: FAST });
    sessions.push(again);
    await again.waitUntilReady(5_000);
    expect(again.link().resumed).toBe(true);
    expect(again.safetyCode()).toBe(phoneSession.safetyCode());
    expect((await again.request("tap", { x: 5, y: 6 })).result).toMatchObject({ x: 5, y: 6 });
  });

  it("reports ended when the relay no longer has the room", async () => {
    const { bot } = await paired();
    const saved = bot.save();
    await bot.end();
    const again = PonySession.resumeBot(saved, { reconnect: FAST });
    sessions.push(again);
    await expect(again.waitUntilReady(3_000)).rejects.toThrow("unknown_token");
    expect(again.state).toBe("ended");
    expect(await again.request("ping")).toEqual({ ok: false, error: "unknown_token" });
  });

  it("ignores someone rejoining as the phone with a different key", async () => {
    const { bot, phoneSession } = await paired({ bot: FAST, phone: { initialMs: 60_000 } });
    const away = reached(bot, "away");
    cutCable(phoneSession);
    await away;

    const stranger = await PonySession.createPhone(bot.qrJson(), generateKeyPair(), { reconnect: FAST });
    sessions.push(stranger);
    let reached_ = false;
    stranger.onRequest = () => {
      reached_ = true;
      return { ok: true };
    };
    await stranger.waitUntilReady(3_000);
    const deadline = Date.now() + 2_000;
    while (bot.link().reason !== "peer_key_changed" && Date.now() < deadline) await sleep(20);
    expect(bot.link().reason).toBe("peer_key_changed");
    await expect(bot.request("tap", { x: 9, y: 9 }, 300)).rejects.toThrow("timed out");
    expect(reached_).toBe(false);
  });
});

describe("backoffDelay", () => {
  it("doubles from the first delay up to the cap, with bounded jitter", () => {
    const mid = () => 0.5;
    expect(backoffDelay(1, 1_000, 30_000, mid)).toBe(1_000);
    expect(backoffDelay(2, 1_000, 30_000, mid)).toBe(2_000);
    expect(backoffDelay(5, 1_000, 30_000, mid)).toBe(16_000);
    expect(backoffDelay(9, 1_000, 30_000, mid)).toBe(30_000);
    expect(backoffDelay(3, 1_000, 30_000, () => 0)).toBe(3_200);
    expect(backoffDelay(3, 1_000, 30_000, () => 1)).toBe(4_800);
  });
});
