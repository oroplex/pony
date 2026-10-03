import { afterEach, describe, expect, it } from "vitest";
import { WebSocket } from "ws";

import { createRelay, type StartedRelay } from "./server.ts";

const sockets: WebSocket[] = [];
let relay: StartedRelay | undefined;

afterEach(async () => {
  for (const s of sockets) {
    s.close();
  }
  sockets.length = 0;
  if (relay) {
    await relay.close();
    relay = undefined;
  }
});

function connect(url: string): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    sockets.push(ws);
    ws.once("open", () => resolve(ws));
    ws.once("error", reject);
  });
}

function onceJson(ws: WebSocket): Promise<Record<string, unknown>> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("timed out waiting for message")), 3000);
    ws.once("message", (raw) => {
      clearTimeout(timer);
      resolve(JSON.parse(String(raw)) as Record<string, unknown>);
    });
  });
}

describe("relay http + websocket", () => {
  it("issues a pairing token and forwards opaque frames after both peers join", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const pairRes = await fetch(`${relay.url}/pair`, { method: "POST" });
    expect(pairRes.status).toBe(200);
    const pair = (await pairRes.json()) as { token: string; expiresInMs: number; wsUrl: string };
    expect(pair.token).toMatch(/^[0-9a-f]{64}$/);
    expect(pair.expiresInMs).toBe(15 * 60 * 1000);

    const bot = await connect(pair.wsUrl);
    const phone = await connect(pair.wsUrl);

    const botWait = onceJson(bot);
    bot.send(JSON.stringify({ type: "hello", role: "bot", token: pair.token }));
    expect(await botWait).toEqual({ type: "waiting" });

    const botReady = onceJson(bot);
    const phoneReady = onceJson(phone);
    phone.send(JSON.stringify({ type: "hello", role: "phone", token: pair.token }));
    expect(await botReady).toEqual({ type: "ready", role: "bot" });
    expect(await phoneReady).toEqual({ type: "ready", role: "phone" });

    const phoneFrame = onceJson(phone);
    bot.send(JSON.stringify({ type: "fwd", data: "opaque-from-bot" }));
    expect(await phoneFrame).toEqual({ type: "fwd", data: "opaque-from-bot" });

    const botFrame = onceJson(bot);
    phone.send(JSON.stringify({ type: "fwd", data: "opaque-from-phone" }));
    expect(await botFrame).toEqual({ type: "fwd", data: "opaque-from-phone" });
  });

  it("rejects a hello after the pairing token has expired", async () => {
    let now = 10_000;
    relay = await createRelay({
      host: "127.0.0.1",
      port: 0,
      ttlMs: 50,
      now: () => now,
    });
    const pairRes = await fetch(`${relay.url}/pair`, { method: "POST" });
    const { token, wsUrl } = (await pairRes.json()) as { token: string; wsUrl: string };
    now += 51;
    const bot = await connect(wsUrl);
    const reply = onceJson(bot);
    bot.send(JSON.stringify({ type: "hello", role: "bot", token }));
    expect(await reply).toEqual({ type: "error", reason: "expired_token" });
  });

  it("forwards the bot client name on ready when one was sent", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const pairRes = await fetch(`${relay.url}/pair`, { method: "POST" });
    const pair = (await pairRes.json()) as { token: string; wsUrl: string };
    const bot = await connect(pair.wsUrl);
    const phone = await connect(pair.wsUrl);
    const botWait = onceJson(bot);
    bot.send(JSON.stringify({ type: "hello", role: "bot", token: pair.token, client: "Grok Bot" }));
    expect(await botWait).toEqual({ type: "waiting" });
    const botReady = onceJson(bot);
    const phoneReady = onceJson(phone);
    phone.send(JSON.stringify({ type: "hello", role: "phone", token: pair.token }));
    expect(await botReady).toEqual({ type: "ready", role: "bot", client: "Grok Bot" });
    expect(await phoneReady).toEqual({ type: "ready", role: "phone", client: "Grok Bot" });
  });

  it("serves health", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const res = await fetch(`${relay.url}/health`);
    const body = (await res.json()) as { ok: boolean; resume: boolean };
    expect(body.ok).toBe(true);
    expect(body.resume).toBe(true);
  });
});

function onceClose(ws: WebSocket): Promise<{ code: number; reason: string }> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("timed out waiting for close")), 3000);
    ws.once("close", (code, reason) => {
      clearTimeout(timer);
      resolve({ code, reason: reason.toString() });
    });
  });
}

async function pairedSockets(resume: boolean) {
  const pairRes = await fetch(`${relay!.url}/pair`, { method: "POST" });
  const pair = (await pairRes.json()) as { token: string; wsUrl: string };
  const bot = await connect(pair.wsUrl);
  const phone = await connect(pair.wsUrl);
  const botWait = onceJson(bot);
  bot.send(JSON.stringify({ type: "hello", role: "bot", token: pair.token, resume }));
  await botWait;
  const botReady = onceJson(bot);
  const phoneReady = onceJson(phone);
  phone.send(JSON.stringify({ type: "hello", role: "phone", token: pair.token, resume }));
  await Promise.all([botReady, phoneReady]);
  return { bot, phone, ...pair };
}

describe("relay sessions that survive a dropped connection", () => {
  it("tells the other side the peer is away and resumes the room when it rejoins", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const { bot, phone, token, wsUrl } = await pairedSockets(true);

    const away = onceJson(bot);
    phone.terminate();
    expect(await away).toEqual({ type: "peer_away" });

    // A command sent while the phone is away is refused, not queued, so a
    // timed-out tap cannot run minutes later when the phone rejoins.
    const lost = onceJson(bot);
    bot.send(JSON.stringify({ type: "fwd", data: "into the void" }));
    expect(await lost).toEqual({ type: "error", reason: "peer_missing" });

    const back = await connect(wsUrl);
    const botReady = onceJson(bot);
    const phoneReady = onceJson(back);
    back.send(JSON.stringify({ type: "hello", role: "phone", token, resume: true }));
    expect(await botReady).toEqual({ type: "ready", role: "bot", resumed: true });
    expect(await phoneReady).toEqual({ type: "ready", role: "phone", resumed: true });

    const frame = onceJson(back);
    bot.send(JSON.stringify({ type: "fwd", data: "still here" }));
    expect(await frame).toEqual({ type: "fwd", data: "still here" });
  });

  it("waits when a peer rejoins before the other side is back", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const { bot, phone, token, wsUrl } = await pairedSockets(true);
    bot.terminate();
    phone.terminate();
    const hub = relay.hub;
    const deadline = Date.now() + 3000;
    while (hub.peerOf(token, "bot") || hub.peerOf(token, "phone")) {
      if (Date.now() > deadline) throw new Error("relay never noticed the drops");
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    expect(hub.size()).toBe(1);

    const bot2 = await connect(wsUrl);
    const waiting = onceJson(bot2);
    bot2.send(JSON.stringify({ type: "hello", role: "bot", token, resume: true }));
    expect(await waiting).toEqual({ type: "waiting" });
  });

  it("ends the room for both sides on bye", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const { bot, phone, token, wsUrl } = await pairedSockets(true);
    const left = onceJson(phone);
    const phoneClosed = onceClose(phone);
    const botClosed = onceClose(bot);
    bot.send(JSON.stringify({ type: "bye" }));
    expect(await left).toEqual({ type: "peer_left" });
    expect((await phoneClosed).code).toBe(4001);
    expect((await botClosed).code).toBe(1000);

    const late = await connect(wsUrl);
    const reply = onceJson(late);
    late.send(JSON.stringify({ type: "hello", role: "bot", token, resume: true }));
    expect(await reply).toEqual({ type: "error", reason: "unknown_token" });
  });

  it("still ends the session on a drop when a peer can't rejoin", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const { bot, phone } = await pairedSockets(false);
    const left = onceJson(bot);
    const closed = onceClose(bot);
    phone.terminate();
    expect(await left).toEqual({ type: "peer_left" });
    expect((await closed).code).toBe(4001);
  });

  it("drops a socket that stops answering pings", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0, heartbeatMs: 40 });
    const pairRes = await fetch(`${relay.url}/pair`, { method: "POST" });
    const pair = (await pairRes.json()) as { token: string; wsUrl: string };
    const silent = new WebSocket(pair.wsUrl, { autoPong: false });
    sockets.push(silent);
    await new Promise((resolve, reject) => {
      silent.once("open", resolve);
      silent.once("error", reject);
    });
    const healthy = await connect(pair.wsUrl);
    const closed = onceClose(silent);
    await new Promise((resolve) => setTimeout(resolve, 200));
    expect((await closed).code).toBe(1006);
    expect(healthy.readyState).toBe(WebSocket.OPEN);
  });
});
