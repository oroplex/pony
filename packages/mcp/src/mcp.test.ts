import { afterEach, describe, expect, it } from "vitest";

import { Client } from "@modelcontextprotocol/client";
import { InMemoryTransport } from "@modelcontextprotocol/server";
import type { CallToolResult } from "@modelcontextprotocol/server";
import { parsePairingInput } from "@pony/shared";
import { MockPhone } from "@pony/client/mock-phone";
import { PonySession } from "@pony/client";
import { createRelay, type StartedRelay } from "../../../relay/src/server.ts";

import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { ControllerOptions } from "./controller.ts";
import { createPonyMcp, listenMcp, type PonyMcp } from "./server.ts";

let relay: StartedRelay | undefined;
const sessions: PonySession[] = [];
let mcp: PonyMcp | undefined;
let client: Client | undefined;
let httpClose: (() => Promise<void>) | undefined;

afterEach(async () => {
  await client?.close().catch(() => undefined);
  client = undefined;
  mcp?.controller.detach();
  mcp = undefined;
  for (const session of sessions) session.close();
  sessions.length = 0;
  if (httpClose) {
    await httpClose().catch(() => undefined);
    httpClose = undefined;
  }
  if (relay) {
    await relay.close();
    relay = undefined;
  }
});

async function startMcp(extra: Omit<ControllerOptions, "relayHttp"> = {}): Promise<Client> {
  relay ??= await createRelay({ host: "127.0.0.1", port: 0 });
  mcp = createPonyMcp({ relayHttp: relay.url, ...extra });
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  client = new Client({ name: "pony-test", version: "0.0.0" });
  await Promise.all([client.connect(clientTransport), mcp.server.connect(serverTransport)]);
  return client;
}

async function connectPhone(payload: string, phone = new MockPhone(), socketUrl?: string): Promise<MockPhone> {
  const phoneSession = await PonySession.createPhone(payload, undefined, { socketUrl, reconnect: FAST });
  sessions.push(phoneSession);
  phone.attach(phoneSession);
  await phoneSession.waitUntilReady();
  return phone;
}

const FAST = { initialMs: 40, maxMs: 200 };
const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

async function until(check: () => boolean | Promise<boolean>, what: string, timeoutMs = 5_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (!(await check())) {
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`);
    await sleep(20);
  }
}

async function statusOf(caller: Client): Promise<Record<string, unknown>> {
  return JSON.parse(textOf(await caller.callTool({ name: "status", arguments: {} }))) as Record<string, unknown>;
}

async function waitConnected(caller: Client): Promise<Record<string, unknown>> {
  const deadline = Date.now() + 5_000;
  let last = "";
  while (Date.now() < deadline) {
    const status = await caller.callTool({ name: "status", arguments: {} });
    last = textOf(status);
    const body = JSON.parse(last) as Record<string, unknown>;
    if (body.connected === true && body.connectedAt != null) return body;
    await new Promise((resolve) => setTimeout(resolve, 30));
  }
  throw new Error(`phone did not connect: ${last}`);
}

function textOf(result: CallToolResult): string {
  return result.content
    .map((block) => (block.type === "text" ? block.text : ""))
    .filter(Boolean)
    .join("\n");
}

describe("pony mcp", () => {
  it("lists the phone tools", async () => {
    const caller = await startMcp();
    const listed = await caller.listTools();
    expect(listed.tools.map((tool) => tool.name).sort()).toEqual(
      [
        "ask_user",
        "confirm",
        "disconnect",
        "done",
        "drag",
        "key",
        "long_press",
        "open_app",
        "open_settings",
        "pair",
        "pinch",
        "screenshot",
        "speak",
        "status",
        "swipe",
        "tap",
        "type",
        "ui_tree",
        "wait_for_request",
        "wait_idle",
      ].sort(),
    );
  });

  it("pairs a mock phone and runs the command set", async () => {
    const caller = await startMcp();
    const paired = await caller.callTool({ name: "pair", arguments: { waitMs: 0 } });
    expect(paired.isError).toBeFalsy();
    const image = paired.content.find((block) => block.type === "image");
    expect(image).toMatchObject({ type: "image", mimeType: "image/png" });
    if (image?.type === "image") {
      expect(image.data.length).toBeGreaterThan(100);
    }

    const summary = JSON.parse(textOf(paired)) as {
      token: string;
      pairingLink: string;
      pairPageLink: string;
      payload: string;
      safetyCode: string | null;
    };
    expect(summary.safetyCode).toBeNull();
    expect(summary.pairingLink.startsWith("pony://pair?")).toBe(true);
    expect(parsePairingInput(summary.pairingLink).token).toBe(summary.token);
    expect(summary.pairPageLink).toContain("/pair.html?");
    expect(new URL(summary.pairPageLink).searchParams.get("token")).toBe(summary.token);
    expect(Object.keys(JSON.parse(summary.payload)).sort()).toEqual(["pk", "relay", "token", "v"]);
    expect(summary.payload).not.toContain("private");

    const structured = paired.structuredContent as {
      qrPngDataUrl?: string;
      pairingLink?: string;
      pairPageLink?: string;
    };
    expect(structured.qrPngDataUrl?.startsWith("data:image/png;base64,")).toBe(true);
    expect(structured.pairingLink).toBe(summary.pairingLink);
    expect(structured.pairPageLink).toBe(summary.pairPageLink);

    const secret = "s3cret-pony";
    const phone = await connectPhone(summary.payload);
    const status = await waitConnected(caller);
    expect(status.safetyCode).toMatch(/^\d{3}-\d{3}$/);
    expect(status.safetyCode).toBe(sessions[0].safetyCode());

    const shot = await caller.callTool({ name: "screenshot", arguments: {} });
    expect(shot.isError).toBeFalsy();
    expect(shot.content.find((block) => block.type === "image")).toMatchObject({ mimeType: "image/jpeg" });

    phone.screenshotBlank = true;
    const blankShot = await caller.callTool({ name: "screenshot", arguments: {} });
    expect(blankShot.isError).toBe(true);
    expect(textOf(blankShot)).toContain("no image");
    phone.screenshotBlank = false;

    phone.screenshotEmptyBackground = true;
    const emptyBg = await caller.callTool({ name: "screenshot", arguments: { background: true } });
    expect(emptyBg.isError).toBe(true);
    expect(textOf(emptyBg)).toContain("background_empty");
    phone.screenshotEmptyBackground = false;

    const tap = await caller.callTool({ name: "tap", arguments: { x: 12, y: 34 } });
    expect(JSON.parse(textOf(tap)).result).toMatchObject({ x: 12, y: 34 });

    const swipe = await caller.callTool({
      name: "swipe",
      arguments: { x1: 1, y1: 2, x2: 3, y2: 4, durationMs: 80 },
    });
    expect(JSON.parse(textOf(swipe)).ok).toBe(true);

    const held = await caller.callTool({
      name: "long_press",
      arguments: { x: 50, y: 60, durationMs: 800 },
    });
    expect(JSON.parse(textOf(held)).result).toMatchObject({ x: 50, y: 60, durationMs: 800 });

    const dragged = await caller.callTool({
      name: "drag",
      arguments: { x1: 5, y1: 6, x2: 7, y2: 8 },
    });
    expect(JSON.parse(textOf(dragged)).result).toMatchObject({ x1: 5, y1: 6, x2: 7, y2: 8, durationMs: 600 });

    const pinched = await caller.callTool({
      name: "pinch",
      arguments: { x: 100, y: 200, fromDistance: 100, toDistance: 400 },
    });
    expect(JSON.parse(textOf(pinched)).result).toMatchObject({ fromDistance: 100, toDistance: 400 });

    const idle = await caller.callTool({ name: "wait_idle", arguments: { timeoutMs: 1000 } });
    expect(JSON.parse(textOf(idle)).result).toMatchObject({ settled: true, reason: "settled" });

    const typed = await caller.callTool({ name: "type", arguments: { text: secret } });
    expect(JSON.parse(textOf(typed)).result).toMatchObject({ length: secret.length, method: "set_text" });
    expect(phone.lastTyped).toBe(secret);

    const retyped = await caller.callTool({ name: "type", arguments: { text: "replaced" } });
    expect(JSON.parse(textOf(retyped)).ok).toBe(true);
    expect(phone.lastTyped).toBe("replaced");

    const appended = await caller.callTool({ name: "type", arguments: { text: "-more", mode: "append" } });
    expect(JSON.parse(textOf(appended)).result).toMatchObject({ mode: "append" });
    expect(phone.lastTyped).toBe("replaced-more");

    phone.focusedIsPassword = true;
    const blocked = await caller.callTool({ name: "type", arguments: { text: secret } });
    expect(blocked.isError).toBe(true);
    expect(textOf(blocked)).toContain("password_field");

    const tree = await caller.callTool({ name: "ui_tree", arguments: {} });
    expect(textOf(tree)).toContain("TextView");
    expect(textOf(tree)).toContain("[password]");

    const home = await caller.callTool({ name: "key", arguments: { key: "home" } });
    expect(JSON.parse(textOf(home)).result).toMatchObject({ key: "home" });

    const enter = await caller.callTool({ name: "key", arguments: { key: "enter" } });
    expect(JSON.parse(textOf(enter)).result).toMatchObject({ key: "enter" });

    for (const key of ["search", "go", "send", "next", "done"] as const) {
      const pressed = await caller.callTool({ name: "key", arguments: { key } });
      expect(JSON.parse(textOf(pressed)).result).toMatchObject({ key });
    }

    const notAKey = await caller.callTool({ name: "key", arguments: { key: "sideways" } });
    expect(notAKey.isError).toBe(true);

    const opened = await caller.callTool({
      name: "open_app",
      arguments: { packageName: "com.android.settings" },
    });
    expect(JSON.parse(textOf(opened)).ok).toBe(true);

    const settings = await caller.callTool({
      name: "open_settings",
      arguments: { name: "input_method" },
    });
    expect(JSON.parse(textOf(settings)).result).toMatchObject({ settings: "input_method" });

    const appDetails = await caller.callTool({
      name: "open_settings",
      arguments: { name: "app_details", packageName: "com.whatsapp" },
    });
    expect(JSON.parse(textOf(appDetails)).result).toMatchObject({
      settings: "app_details",
      packageName: "com.whatsapp",
    });

    const logged = JSON.parse(textOf(await caller.callTool({ name: "status", arguments: {} }))) as {
      log: { detail: string }[];
    };
    expect(JSON.stringify(logged.log)).not.toContain(secret);

    const gone = await caller.callTool({ name: "disconnect", arguments: {} });
    expect(JSON.parse(textOf(gone)).ok).toBe(true);
    const after = JSON.parse(textOf(await caller.callTool({ name: "status", arguments: {} }))) as {
      connected: boolean;
    };
    expect(after.connected).toBe(false);
  });

  it("routes voice tools without logging the spoken text", async () => {
    const caller = await startMcp();
    const paired = JSON.parse(textOf(await caller.callTool({ name: "pair", arguments: {} }))) as {
      payload: string;
    };
    await connectPhone(paired.payload);
    await waitConnected(caller);

    const secret = "sk-live-SHOULD-NOT-LOG";
    const spoken = await caller.callTool({ name: "speak", arguments: { text: secret } });
    expect(spoken.isError).toBeFalsy();
    expect(JSON.parse(textOf(spoken)).result).toMatchObject({ spoken: true });

    const waited = await caller.callTool({ name: "wait_for_request", arguments: {} });
    expect(JSON.parse(textOf(waited)).result).toMatchObject({
      requestId: "req-1",
      text: "lock the door",
      source: "voice",
    });
    const empty = await caller.callTool({ name: "wait_for_request", arguments: { timeoutMs: 1000 } });
    expect(JSON.parse(textOf(empty)).result).toMatchObject({ empty: true });

    const asked = await caller.callTool({ name: "ask_user", arguments: { text: "Which one?" } });
    expect(JSON.parse(textOf(asked)).result).toMatchObject({ text: "the blue one" });

    const no = await caller.callTool({ name: "confirm", arguments: { text: "Pay 20 dollars?" } });
    expect(JSON.parse(textOf(no)).result).toMatchObject({ accepted: false });
    const yes = await caller.callTool({ name: "confirm", arguments: { text: "Send the note?" } });
    expect(JSON.parse(textOf(yes)).result).toMatchObject({ accepted: true });

    const logged = JSON.parse(textOf(await caller.callTool({ name: "status", arguments: {} }))) as {
      log: { detail: string }[];
    };
    const flat = JSON.stringify(logged.log);
    expect(flat).not.toContain(secret);
    expect(flat).toContain(`${secret.length} chars`);
    expect(flat).not.toContain("lock the door");
  });

  it("refuses commands after the session time limit", async () => {
    let now = 1_700_000_000_000;
    const caller = await startMcp({ sessionTtlMs: 1_000, now: () => now });
    const paired = JSON.parse(textOf(await caller.callTool({ name: "pair", arguments: {} }))) as {
      payload: string;
    };
    await connectPhone(paired.payload);
    await waitConnected(caller);
    now += 1_000;
    const denied = await caller.callTool({ name: "tap", arguments: { x: 1, y: 1 } });
    expect(denied.isError).toBe(true);
    expect(textOf(denied)).toContain("Session time limit reached");
  });

  it("serves the same tools over streamable HTTP on localhost", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const listening = await listenMcp({ relayHttp: relay.url, port: 0, host: "127.0.0.1" });
    httpClose = () => listening.close();
    const { StreamableHTTPClientTransport } = await import("@modelcontextprotocol/client");
    client = new Client({ name: "pony-http-test", version: "0.0.0" });
    await client.connect(new StreamableHTTPClientTransport(new URL(listening.url)));
    const listed = await client.listTools();
    expect(listed.tools.some((tool) => tool.name === "pair")).toBe(true);
  });

  it("targets the background display over a live relay", async () => {
    const caller = await startMcp();
    const paired = JSON.parse(textOf(await caller.callTool({ name: "pair", arguments: {} }))) as {
      payload: string;
      pairingLink: string;
    };
    expect(paired.pairingLink).toContain("client=grokbot");
    expect(Object.keys(JSON.parse(paired.payload)).sort()).toEqual(["pk", "relay", "token", "v"]);
    await connectPhone(paired.payload);
    await waitConnected(caller);

    const tap = JSON.parse(textOf(await caller.callTool({ name: "tap", arguments: { x: 8, y: 9 } })));
    expect(tap.result).toMatchObject({ x: 8, y: 9, display: "background" });

    const typed = JSON.parse(textOf(await caller.callTool({ name: "type", arguments: { text: "bg" } })));
    expect(typed.result).toMatchObject({ display: "background", method: "set_text" });

    const opened = JSON.parse(
      textOf(await caller.callTool({ name: "open_app", arguments: { packageName: "com.android.settings" } })),
    );
    expect(opened.result).toMatchObject({ display: "background" });

    const shot = JSON.parse(textOf(await caller.callTool({ name: "screenshot", arguments: {} })).split("\n")[0]);
    expect(shot.display).toBe("background");

    const main = JSON.parse(
      textOf(await caller.callTool({ name: "tap", arguments: { x: 1, y: 2, display: "main" } })),
    );
    expect(main.result.display).toBe("main");

    const forced = JSON.parse(
      textOf(await caller.callTool({ name: "ui_tree", arguments: { background: false } })),
    );
    expect(forced.result.display).toBe("main");
  });

  it("keeps background targeting when the pairing payload advertises a Tailscale relay", async () => {
    relay = await createRelay({ host: "127.0.0.1", port: 0 });
    const caller = await (async () => {
      mcp = createPonyMcp({
        relayHttp: relay!.url,
        publicRelay: "http://100.64.8.8:8787",
        socketUrl: relay!.url,
        clientName: "Grok Bot",
      });
      const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
      client = new Client({ name: "pony-tailscale", version: "0.0.0" });
      await Promise.all([client.connect(clientTransport), mcp.server.connect(serverTransport)]);
      return client;
    })();

    const summary = JSON.parse(textOf(await caller.callTool({ name: "pair", arguments: {} }))) as {
      payload: string;
    };
    const payload = JSON.parse(summary.payload) as { relay: string };
    expect(payload.relay).toContain("100.64.8.8");
    expect(summary.payload).not.toContain("private");
    await connectPhone(summary.payload, new MockPhone(), relay.url);
    await waitConnected(caller);

    const tap = JSON.parse(textOf(await caller.callTool({ name: "tap", arguments: { x: 3, y: 4 } })));
    expect(tap.result).toMatchObject({ display: "background", x: 3, y: 4 });
    const typed = JSON.parse(
      textOf(await caller.callTool({ name: "type", arguments: { text: "tail", display: "background" } })),
    );
    expect(typed.result.display).toBe("background");
    const shot = JSON.parse(textOf(await caller.callTool({ name: "screenshot", arguments: { background: true } })).split("\n")[0]);
    expect(shot.display).toBe("background");
  });

  it("reports a finished request back to the phone with done", async () => {
    const caller = await startMcp();
    const paired = JSON.parse(textOf(await caller.callTool({ name: "pair", arguments: {} }))) as { payload: string };
    const phone = await connectPhone(paired.payload);
    await waitConnected(caller);
    const got = JSON.parse(textOf(await caller.callTool({ name: "wait_for_request", arguments: {} })));
    expect(got.result).toMatchObject({ requestId: "req-1", text: "lock the door" });
    const done = await caller.callTool({ name: "done", arguments: { text: "The door is locked." } });
    expect(done.isError).toBeFalsy();
    expect(phone.finished).toEqual([{ ref: "req-1", text: "The door is locked.", ok: true }]);
  });

  it("fails fast with a clear message once the phone ends the session", async () => {
    const caller = await startMcp();
    const paired = JSON.parse(textOf(await caller.callTool({ name: "pair", arguments: {} }))) as { payload: string };
    await connectPhone(paired.payload);
    await waitConnected(caller);
    await sessions[0].end();
    await until(async () => (await statusOf(caller)).link === "ended", "the session to end");
    const started = Date.now();
    const tap = await caller.callTool({ name: "tap", arguments: { x: 1, y: 1 } });
    expect(tap.isError).toBe(true);
    expect(textOf(tap)).toContain("The owner ended the session on the phone");
    expect(textOf(tap)).toContain("Call pair");
    expect(Date.now() - started).toBeLessThan(1_000);
    const after = await statusOf(caller);
    expect(after.connected).toBe(false);
    expect(after.link).toBe("unpaired");
  });

  it("follows the phone's session length instead of cutting it at 30 minutes", async () => {
    let now = 1_700_000_000_000;
    const caller = await startMcp({ now: () => now });
    const paired = JSON.parse(textOf(await caller.callTool({ name: "pair", arguments: {} }))) as { payload: string };
    const phone = new MockPhone();
    phone.sessionEndsAt = null;
    await connectPhone(paired.payload, phone);
    await waitConnected(caller);
    await until(() => phone.calls.some((call) => call.op === "info"), "the info request");
    await sleep(50);
    now += 45 * 60 * 1000;
    const tap = await caller.callTool({ name: "tap", arguments: { x: 2, y: 3 } });
    expect(tap.isError).toBeFalsy();
    expect((await statusOf(caller)).expiresAt).toBeNull();
  });
});

describe("pony mcp listen mode", () => {
  let dir = "";

  afterEach(() => {
    if (dir) rmSync(dir, { recursive: true, force: true });
    dir = "";
  });

  it("keeps listening between calls so nothing the owner says is lost", async () => {
    const caller = await startMcp({ listen: true });
    const paired = JSON.parse(textOf(await caller.callTool({ name: "pair", arguments: {} }))) as { payload: string };
    const phone = new MockPhone();
    phone.requests = [];
    await connectPhone(paired.payload, phone);
    await waitConnected(caller);

    await until(() => phone.waiting > 0, "the standing wait");
    expect((await statusOf(caller)).listening).toBe(true);
    phone.say("open the calculator");
    phone.say("then add 2 and 2", "voice");
    await until(async () => (await statusOf(caller)).queued === 2, "two queued requests");

    const first = JSON.parse(textOf(await caller.callTool({ name: "wait_for_request", arguments: {} })));
    expect(first.result).toMatchObject({ text: "open the calculator", source: "typed" });
    await caller.callTool({ name: "done", arguments: { text: "Calculator is open." } });
    const second = JSON.parse(textOf(await caller.callTool({ name: "wait_for_request", arguments: {} })));
    expect(second.result).toMatchObject({ text: "then add 2 and 2", source: "voice" });

    const third = caller.callTool({ name: "wait_for_request", arguments: { timeoutMs: 5_000 } });
    await sleep(100);
    const late = phone.say("thanks");
    expect(JSON.parse(textOf(await third)).result).toMatchObject({ requestId: late.id, text: "thanks" });

    expect(phone.finished[0]).toMatchObject({ text: "Calculator is open.", ok: true });
    expect(phone.finished[1]).toMatchObject({ text: "", ok: true });
    const waits = phone.calls.filter((call) => call.op === "wait_for_request");
    expect(waits.every((call) => call.params.listen === true)).toBe(true);
  });

  it("stops listening when the assistant goes quiet, so the phone can use its own brain", async () => {
    const caller = await startMcp({ listen: true, attendMs: 300 });
    const paired = JSON.parse(textOf(await caller.callTool({ name: "pair", arguments: {} }))) as { payload: string };
    const phone = new MockPhone();
    phone.requests = [];
    await connectPhone(paired.payload, phone);
    await waitConnected(caller);
    await until(() => phone.waiting > 0, "the standing wait");
    await until(() => !mcp!.controller.status().listening, "the listener to pause", 3_000);
    phone.cancelWaits();
    await sleep(50);
    expect(phone.waiting).toBe(0);
    await caller.callTool({ name: "status", arguments: {} });
    await until(() => phone.waiting > 0, "the standing wait to come back");
  });

  it("rejoins the saved pairing after a restart without pairing again", async () => {
    dir = mkdtempSync(join(tmpdir(), "pony-mcp-"));
    const statePath = join(dir, "mcp.json");
    const caller = await startMcp({ listen: true, statePath, reconnect: FAST });
    const paired = JSON.parse(textOf(await caller.callTool({ name: "pair", arguments: {} }))) as { payload: string };
    const phone = new MockPhone();
    phone.requests = [];
    await connectPhone(paired.payload, phone);
    const before = await waitConnected(caller);

    await client!.close();
    mcp!.controller.detach();
    await until(() => sessions[0].state === "away", "the phone to see the restart");

    const again = await startMcp({ listen: true, statePath, reconnect: FAST });
    const after = await waitConnected(again);
    expect(after.safetyCode).toBe(before.safetyCode);
    expect(after.resumed).toBe(true);
    phone.say("are you back?");
    const got = JSON.parse(textOf(await again.callTool({ name: "wait_for_request", arguments: {} })));
    expect(got.result).toMatchObject({ text: "are you back?" });
  });
});
