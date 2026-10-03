import { afterEach, describe, expect, it } from "vitest";
import { WebSocket } from "ws";

import { createRelay, type StartedRelay } from "./server.ts";

const sockets: WebSocket[] = [];
let relay: StartedRelay | undefined;

afterEach(async () => {
  for (const socket of sockets) {
    socket.terminate();
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

function onceClose(ws: WebSocket): Promise<{ code: number; reason: string }> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("timed out waiting for close")), 3000);
    ws.once("close", (code, reason) => {
      clearTimeout(timer);
      resolve({ code, reason: reason.toString() });
    });
  });
}

async function expectHealth(url: string) {
  const res = await fetch(`${url}/health`);
  const body = (await res.json()) as { ok: boolean };
  expect(res.status).toBe(200);
  expect(body.ok).toBe(true);
  return body;
}

describe("relay abuse inputs stay on their socket", () => {
  it("answers and closes on empty, null, non-JSON, binary, and malformed frames without dropping /health", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0, heartbeatMs: 60_000 });
    const wsUrl = `${relay.url.replace(/^http/, "ws")}/ws`;

    const cases: Array<{ name: string; send: (ws: WebSocket) => void; reason: string }> = [
      { name: "empty string", send: (ws) => ws.send(""), reason: "bad_json" },
      { name: "zero-length buffer", send: (ws) => ws.send(Buffer.alloc(0)), reason: "bad_json" },
      { name: "json null", send: (ws) => ws.send("null"), reason: "bad_payload" },
      { name: "json array", send: (ws) => ws.send("[]"), reason: "bad_payload" },
      { name: "json number", send: (ws) => ws.send("1"), reason: "bad_payload" },
      { name: "non-json", send: (ws) => ws.send("not-json"), reason: "bad_json" },
      { name: "binary", send: (ws) => ws.send(Buffer.from([0, 1, 255, 0])), reason: "bad_payload" },
      { name: "missing hello fields", send: (ws) => ws.send(JSON.stringify({ type: "hello" })), reason: "bad_role" },
      {
        name: "malformed pairing token",
        send: (ws) => ws.send(JSON.stringify({ type: "hello", role: "bot", token: "zzzz" })),
        reason: "bad_token",
      },
      { name: "unknown type", send: (ws) => ws.send(JSON.stringify({ type: "explode" })), reason: "unknown_type" },
    ];

    for (const testCase of cases) {
      const ws = await connect(wsUrl);
      const reply = onceJson(ws);
      const closed = onceClose(ws);
      testCase.send(ws);
      expect(await reply, testCase.name).toEqual({ type: "error", reason: testCase.reason });
      expect((await closed).reason, testCase.name).toBe(testCase.reason);
      await expectHealth(relay.url);
    }
  });

  it("drops a frame over maxPayload and keeps serving /health", async () => {
    relay = await createRelay({
      host: "127.0.0.1",
      port: 0,
      heartbeatMs: 60_000,
      limits: { maxPayloadBytes: 256 },
    });
    const ws = await connect(`${relay.url.replace(/^http/, "ws")}/ws`);
    const closed = onceClose(ws);
    ws.send("x".repeat(2048));
    expect((await closed).code).toBe(1009);
    await expectHealth(relay.url);
  });

  it("rejects an oversized HTTP body on POST /pair", async () => {
    relay = await createRelay({
      host: "127.0.0.1",
      port: 0,
      limits: { maxHttpBodyBytes: 64 },
    });
    const res = await fetch(`${relay.url}/pair`, {
      method: "POST",
      headers: { "content-length": "999999", "content-type": "application/json" },
      body: "x".repeat(200),
    });
    expect(res.status).toBe(413);
    expect(await res.json()).toEqual({ error: "payload_too_large" });
    await expectHealth(relay.url);
    expect(relay.hub.size()).toBe(0);
  });
});

describe("relay rate limits and room caps", () => {
  it("caps pairing attempts and rooms from one IP", async () => {
    relay = await createRelay({
      host: "127.0.0.1",
      port: 0,
      limits: {
        maxPairPerIpPerWindow: 2,
        maxRooms: 10,
        maxRoomsPerIp: 2,
        windowMs: 60_000,
      },
    });
    const first = await fetch(`${relay.url}/pair`, { method: "POST" });
    const second = await fetch(`${relay.url}/pair`, { method: "POST" });
    const third = await fetch(`${relay.url}/pair`, { method: "POST" });
    expect(first.status).toBe(200);
    expect(second.status).toBe(200);
    expect(third.status).toBe(429);
    expect(await third.json()).toEqual({ error: "rate_limited" });
    await expectHealth(relay.url);
  });

  it("caps total rooms even when IPs differ", async () => {
    relay = await createRelay({
      host: "127.0.0.1",
      port: 0,
      limits: { maxRooms: 2, maxPairPerIpPerWindow: 50, maxRoomsPerIp: 50, windowMs: 60_000 },
    });
    const a = await fetch(`${relay.url}/pair`, { method: "POST", headers: { "x-forwarded-for": "10.0.0.1" } });
    const b = await fetch(`${relay.url}/pair`, { method: "POST", headers: { "x-forwarded-for": "10.0.0.2" } });
    const c = await fetch(`${relay.url}/pair`, { method: "POST", headers: { "x-forwarded-for": "10.0.0.3" } });
    expect(a.status).toBe(200);
    expect(b.status).toBe(200);
    expect(c.status).toBe(503);
    expect(await c.json()).toEqual({ error: "too_many_rooms" });
    await expectHealth(relay.url);
  });

  it("closes a connect flood from one IP and keeps /health up", async () => {
    relay = await createRelay({
      host: "127.0.0.1",
      port: 0,
      heartbeatMs: 60_000,
      limits: { maxConnectPerIpPerWindow: 3, maxWsPerIp: 3, windowMs: 60_000 },
    });
    const wsUrl = `${relay.url.replace(/^http/, "ws")}/ws`;
    const first = await connect(wsUrl);
    const second = await connect(wsUrl);
    const third = await connect(wsUrl);
    expect(first.readyState).toBe(WebSocket.OPEN);
    expect(second.readyState).toBe(WebSocket.OPEN);
    expect(third.readyState).toBe(WebSocket.OPEN);

    const extra = new WebSocket(wsUrl);
    sockets.push(extra);
    extra.on("error", () => {
      /* handshake may fail after we close */
    });
    const reply = onceJson(extra);
    const closed = onceClose(extra);
    expect(await reply).toEqual({ type: "error", reason: "rate_limited" });
    expect((await closed).code).toBe(4008);
    await expectHealth(relay.url);
  });

  it("rate-limits a message flood on one socket", async () => {
    relay = await createRelay({
      host: "127.0.0.1",
      port: 0,
      heartbeatMs: 60_000,
      limits: { maxMessagesPerSocketPerSec: 5 },
    });
    const ws = await connect(`${relay.url.replace(/^http/, "ws")}/ws`);
    const closed = onceClose(ws);
    let last: Record<string, unknown> | undefined;
    ws.on("message", (raw) => {
      last = JSON.parse(String(raw)) as Record<string, unknown>;
    });
    for (let i = 0; i < 12; i++) {
      try {
        ws.send(JSON.stringify({ type: "bye" }));
      } catch {
        break;
      }
    }
    expect((await closed).code).toBe(4008);
    expect(last).toEqual({ type: "error", reason: "rate_limited" });
    await expectHealth(relay.url);
  });
});
