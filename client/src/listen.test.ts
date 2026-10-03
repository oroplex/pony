import { mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { WebSocket } from "ws";

import { createRelay, type StartedRelay } from "../../relay/src/server.ts";

import { RequestListener, SessionFile, runExec, runListen, type ListenEvent, type ListenHandle, type OwnerRequest } from "./listen.ts";
import { MockPhone } from "./mock-phone.ts";
import { PonySession, type ReconnectOptions } from "./session.ts";

const FAST: ReconnectOptions = { initialMs: 40, maxMs: 200 };
const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

let relay: StartedRelay | undefined;
let dir = "";
const sessions: PonySession[] = [];
const handles: ListenHandle[] = [];

beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), "pony-listen-"));
});

afterEach(async () => {
  for (const handle of handles) await handle.stop().catch(() => undefined);
  handles.length = 0;
  for (const session of sessions) session.close();
  sessions.length = 0;
  if (relay) {
    await relay.close();
    relay = undefined;
  }
  rmSync(dir, { recursive: true, force: true });
});

async function until(check: () => boolean, what: string, timeoutMs = 5_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (!check()) {
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`);
    await sleep(15);
  }
}

async function phoneFor(payload: string, phone: MockPhone): Promise<PonySession> {
  const session = await PonySession.createPhone(payload, undefined, { reconnect: FAST });
  sessions.push(session);
  phone.attach(session);
  await session.waitUntilReady();
  return session;
}

describe("RequestListener", () => {
  it("keeps a standing wait open and hands over every request in order", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const bot = await PonySession.createBot({ relayHttp: relay.url, reconnect: FAST });
    sessions.push(bot);
    const phone = new MockPhone();
    phone.requests = [];
    const phoneSession = await phoneFor(bot.qrJson(), phone);
    await bot.waitUntilReady();

    const heard: OwnerRequest[] = [];
    const listener = new RequestListener(bot, { pollMs: 150, onRequest: (request) => void heard.push(request) });
    listener.start();
    await until(() => phone.waiting > 0, "the first standing wait");

    phone.say("open the calculator");
    await sleep(400);
    phone.say("and add two plus two", "voice");
    await until(() => heard.length === 2, "two requests");
    expect(heard.map((request) => [request.text, request.source])).toEqual([
      ["open the calculator", "typed"],
      ["and add two plus two", "voice"],
    ]);

    const waits = phone.calls.filter((call) => call.op === "wait_for_request");
    expect(waits.length).toBeGreaterThanOrEqual(3);
    expect(waits.every((call) => call.params.listen === true)).toBe(true);

    (phoneSession as unknown as { ws?: WebSocket }).ws?.terminate();
    await until(() => bot.state === "away", "the phone to drop");
    await bot.waitUntilReady(5_000);
    phone.say("still there?");
    await until(() => heard.length === 3, "a request after the phone came back");
    listener.stop();
    await listener.settled();
  });

  it("picks up a request the phone handed out just before the link dropped", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const bot = await PonySession.createBot({ relayHttp: relay.url, reconnect: FAST });
    sessions.push(bot);
    const phone = new MockPhone();
    phone.requests = [];
    const phoneSession = await PonySession.createPhone(bot.qrJson(), undefined, { reconnect: FAST });
    sessions.push(phoneSession);
    let release: (() => void) | undefined;
    phoneSession.onRequest = async (msg) => {
      if (msg.op !== "wait_for_request") return phone.handle(msg);
      await new Promise<void>((resolve) => {
        release = resolve;
      });
      return { ok: true, result: { requestId: "late-1", text: "set a timer", source: "voice" } };
    };
    await bot.waitUntilReady();

    const heard: OwnerRequest[] = [];
    const listener = new RequestListener(bot, { pollMs: 5_000, onRequest: (request) => void heard.push(request) });
    listener.start();
    await until(() => release !== undefined, "the standing wait");
    (bot as unknown as { ws?: WebSocket }).ws?.terminate();
    await until(() => bot.state === "reconnecting", "the drop");
    await bot.waitUntilReady(5_000);
    release!();
    await until(() => heard.length === 1, "the late request");
    expect(heard[0]).toMatchObject({ requestId: "late-1", text: "set a timer" });
    listener.dispose();
  });

  it("gets a request again, once, when the reply carrying it was lost", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const bot = await PonySession.createBot({ relayHttp: relay.url, reconnect: FAST });
    sessions.push(bot);
    const phone = new MockPhone();
    phone.requests = [];
    const phoneSession = await PonySession.createPhone(bot.qrJson(), undefined, { reconnect: FAST });
    sessions.push(phoneSession);
    let swallow = true;
    phoneSession.onRequest = async (msg) => {
      const result = await phone.handle(msg);
      if (swallow && msg.op === "wait_for_request" && typeof result.result?.text === "string") {
        swallow = false;
        (bot as unknown as { ws?: WebSocket }).ws?.terminate();
        return new Promise(() => undefined);
      }
      return result;
    };
    await bot.waitUntilReady();

    const heard: OwnerRequest[] = [];
    const listener = new RequestListener(bot, { pollMs: 150, onRequest: (request) => void heard.push(request) });
    listener.start();
    await until(() => phone.waiting > 0, "the standing wait");
    const asked = phone.say("book a table for two");
    await until(() => heard.length === 1, "the request after the reply was lost");
    expect(heard[0]).toMatchObject({ requestId: asked.id, text: "book a table for two" });
    await sleep(500);
    expect(heard).toHaveLength(1);
    const acks = phone.calls.filter((call) => call.op === "wait_for_request" && call.params.ack === asked.id);
    expect(acks.length).toBeGreaterThan(0);
    listener.dispose();
  });
});

describe("SessionFile", () => {
  it("writes the saved pairing readable only by the owner", () => {
    const file = new SessionFile(join(dir, "nested", "listen.json"));
    expect(file.load()).toBeNull();
    file.save({
      v: 1,
      role: "bot",
      payload: { v: 1, relay: "wss://relay.example", token: "ab".repeat(32), pk: "pk" },
      socketUrl: "wss://relay.example/ws",
      privateKey: "sk",
      publicKey: "pk",
      peerKey: "phone",
      pairedAt: 1,
    });
    expect(file.load()?.peerKey).toBe("phone");
    if (process.platform !== "win32") {
      expect(statSync(file.path).mode & 0o777).toBe(0o600);
      expect(statSync(join(dir, "nested")).mode & 0o077).toBe(0);
    }
    writeFileSync(file.path, "not json");
    expect(file.load()).toBeNull();
    file.clear();
    expect(file.load()).toBeNull();
  });

  it("encrypts the pairing file and still reads leftover plaintext", () => {
    const saved = {
      v: 1 as const,
      role: "bot" as const,
      payload: { v: 2, relay: "wss://relay.example", token: "ab".repeat(32), pk: "pk" },
      socketUrl: "wss://relay.example/ws",
      privateKey: "sk-secret",
      publicKey: "pk",
      peerKey: "phone",
      pairedAt: 1,
      ownerConfirmed: true,
    };
    const encrypted = new SessionFile(join(dir, "mcp.json"));
    encrypted.save(saved);
    const raw = readFileSync(encrypted.path, "utf8");
    expect(raw.startsWith("pony-mcp1.")).toBe(true);
    expect(raw).not.toContain("sk-secret");
    expect(encrypted.load()?.privateKey).toBe("sk-secret");
    expect(encrypted.load()?.ownerConfirmed).toBe(true);
    if (process.platform !== "win32") {
      expect(statSync(encrypted.path).mode & 0o777).toBe(0o600);
      expect(statSync(`${encrypted.path}.key`).mode & 0o777).toBe(0o600);
    }
    const leftover = join(dir, "legacy.json");
    writeFileSync(leftover, JSON.stringify(saved), { mode: 0o600 });
    expect(new SessionFile(leftover).load()?.privateKey).toBe("sk-secret");
  });
});

describe("runExec", () => {
  it("passes the request in the environment and on stdin, never on the command line", async () => {
    const script = join(dir, "echo.mjs");
    writeFileSync(
      script,
      "let input='';process.stdin.on('data',d=>input+=d).on('end',()=>{process.stdout.write(`${process.env.PONY_REQUEST_TEXT}|${process.env.PONY_REQUEST_SOURCE}|${input.trim()}`)})",
    );
    const request = { requestId: "r1", text: "it's $HOME; `rm -rf` \"quoted\"", source: "voice", receivedAt: 0 };
    const reply = await runExec(`node "${script}"`, request);
    expect(reply).toEqual({ ok: true, text: `${request.text}|voice|${request.text}` });
  });

  it("reports a failing command with its error output", async () => {
    const reply = await runExec(`node -e "process.stderr.write('no door found');process.exit(3)"`, {
      requestId: "r2",
      text: "lock the door",
      source: "typed",
      receivedAt: 0,
    });
    expect(reply).toEqual({ ok: false, text: "no door found" });
  });

  it("stops a command that runs too long", async () => {
    const reply = await runExec(`node -e "setTimeout(()=>{},10000)"`, { requestId: "r3", text: "x", source: "typed", receivedAt: 0 }, 200);
    expect(reply.ok).toBe(false);
    expect(reply.text).toContain("too long");
  });
});

describe("runListen", () => {
  it("pairs, runs --exec for each request, sends the reply, and rejoins on the next run", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const statePath = join(dir, "listen.json");
    const script = join(dir, "reply.mjs");
    writeFileSync(script, "process.stdout.write(`Done: ${process.env.PONY_REQUEST_TEXT}`)");
    const phone = new MockPhone();
    phone.requests = [];
    const events: ListenEvent[] = [];
    let phoneSession: PonySession | undefined;

    const first = await runListen({
      relayHttp: relay.url,
      statePath,
      clientName: "Grok Bot",
      exec: `node "${script}"`,
      pollMs: 150,
      reconnect: FAST,
      emit: (event) => events.push(event),
      showPairing: async ({ payload, link }) => {
        expect(link).toContain("client=grokbot");
        phoneSession = await phoneFor(payload, phone);
      },
    });
    handles.push(first);
    expect(events.map((event) => event.type).slice(0, 2)).toEqual(["pairing", "paired"]);
    expect(new SessionFile(statePath).load()?.payload.token).toBe(first.session()?.payload.token);

    await until(() => phone.waiting > 0, "the standing wait");
    const asked = phone.say("add 2 and 2");
    await until(() => phone.finished.length === 1, "the reply");
    expect(phone.finished[0]).toEqual({ ref: asked.id, text: "Done: add 2 and 2", ok: true });
    expect(events.some((event) => event.type === "request" && event.text === "add 2 and 2")).toBe(true);
    expect(events.some((event) => event.type === "reply" && event.ok)).toBe(true);

    await first.stop();
    handles.length = 0;
    await until(() => phoneSession?.state === "away", "the phone to see the listener leave");

    const again: ListenEvent[] = [];
    const second = await runListen({
      relayHttp: relay.url,
      statePath,
      exec: `node "${script}"`,
      pollMs: 150,
      reconnect: FAST,
      emit: (event) => again.push(event),
      showPairing: () => {
        throw new Error("should not pair again");
      },
    });
    handles.push(second);
    expect(again.find((event) => event.type === "paired")).toMatchObject({ resumed: true, safetyCode: phoneSession?.safetyCode() });
    await until(() => phone.waiting > 0, "the standing wait after rejoining");
    phone.say("lock the door");
    await until(() => phone.finished.length === 2, "the second reply");
    expect(phone.finished[1].text).toBe("Done: lock the door");
  });

  it("stops and forgets the pairing when the phone ends the session", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const statePath = join(dir, "listen.json");
    const phone = new MockPhone();
    phone.requests = [];
    let phoneSession: PonySession | undefined;
    const lines: string[] = [];
    const handle = await runListen({
      relayHttp: relay.url,
      statePath,
      pollMs: 150,
      reconnect: FAST,
      say: (line) => lines.push(line),
      showPairing: async ({ payload }) => {
        phoneSession = await phoneFor(payload, phone);
      },
    });
    handles.push(handle);
    await until(() => phone.waiting > 0, "the standing wait");
    await phoneSession!.end("time_limit");
    expect(await handle.finished).toBe("peer_left");
    expect(new SessionFile(statePath).load()).toBeNull();
    expect(lines.at(-1)).toContain("session time ran out");
    expect(lines.at(-1)).toContain("Until I disconnect");
  });

  it("drops a saved pairing the relay no longer knows and says so", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const statePath = join(dir, "listen.json");
    const bot = await PonySession.createBot({ relayHttp: relay.url, reconnect: FAST });
    sessions.push(bot);
    await phoneFor(bot.qrJson(), new MockPhone());
    await bot.waitUntilReady();
    new SessionFile(statePath).save(bot.save());
    await bot.end();

    const lines: string[] = [];
    const handle = await runListen({ relayHttp: relay.url, statePath, reconnect: FAST, say: (line) => lines.push(line) });
    expect(await handle.finished).toBe("unknown_token");
    expect(new SessionFile(statePath).load()).toBeNull();
    expect(lines.join("\n")).toContain("no longer has this session");
  });
});
