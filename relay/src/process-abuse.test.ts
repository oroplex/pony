/**
 * Process-level reproduction of the Reddit claim.
 *
 * Before the fix, a WebSocket frame whose JSON was `null` threw
 * `TypeError: Cannot read properties of null (reading 'type')` in the
 * message handler. The Node process exited and GET /health stopped.
 * Empty / zero-length frames already returned `{ type: "error", reason: "bad_json" }`
 * and did not crash.
 *
 * This file starts the real `index.ts` entrypoint (process guards included)
 * and sends each bad input at it. The process must stay up and keep serving
 * /health after every case.
 */
import { spawn, type ChildProcess } from "node:child_process";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { afterEach, describe, expect, it } from "vitest";
import { WebSocket } from "ws";

const here = path.dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);

interface Running {
  child: ChildProcess;
  url: string;
  wsUrl: string;
}

let running: Running | undefined;

function tsxCli(): string {
  return require.resolve("tsx/cli");
}

async function startRelayProcess(env: Record<string, string> = {}): Promise<Running> {
  const child = spawn(process.execPath, [tsxCli(), path.join(here, "index.ts")], {
    cwd: path.join(here, ".."),
    env: {
      ...process.env,
      HOST: "127.0.0.1",
      PORT: "0",
      PONY_MAX_PAYLOAD_BYTES: "4096",
      PONY_MAX_HTTP_BODY_BYTES: "256",
      PONY_MAX_ROOMS: "20",
      PONY_MAX_ROOMS_PER_IP: "8",
      PONY_MAX_PAIR_PER_IP: "20",
      PONY_MAX_WS_PER_IP: "20",
      PONY_MAX_CONNECT_PER_IP: "40",
      PONY_MAX_MESSAGES_PER_SEC: "30",
      ...env,
    },
    stdio: ["ignore", "pipe", "pipe"],
  });

  let stdout = "";
  let stderr = "";
  child.stdout?.on("data", (chunk: Buffer) => {
    stdout += chunk.toString();
  });
  child.stderr?.on("data", (chunk: Buffer) => {
    stderr += chunk.toString();
  });

  const url = await new Promise<string>((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error(`relay did not start.\nstdout:\n${stdout}\nstderr:\n${stderr}`));
    }, 10_000);
    const onExit = (code: number | null, signal: NodeJS.Signals | null) => {
      clearTimeout(timer);
      reject(new Error(`relay exited before listen (${code} ${signal ?? ""}).\nstderr:\n${stderr}`));
    };
    child.once("exit", onExit);
    child.stdout?.on("data", () => {
      const match = stdout.match(/listening on (http:\/\/[^\s]+)/);
      if (match) {
        clearTimeout(timer);
        child.off("exit", onExit);
        resolve(match[1]!);
      }
    });
  });

  return { child, url, wsUrl: `${url.replace(/^http/, "ws")}/ws` };
}

async function stopRelay(target: Running | undefined): Promise<void> {
  if (!target) return;
  if (target.child.exitCode != null) return;
  target.child.kill("SIGTERM");
  await new Promise<void>((resolve) => {
    const timer = setTimeout(() => {
      target.child.kill("SIGKILL");
      resolve();
    }, 2000);
    target.child.once("exit", () => {
      clearTimeout(timer);
      resolve();
    });
  });
}

afterEach(async () => {
  await stopRelay(running);
  running = undefined;
});

async function health(url: string) {
  const res = await fetch(`${url}/health`);
  const body = (await res.json()) as { ok: boolean; rooms: number };
  return { status: res.status, body };
}

function openSocket(wsUrl: string): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    ws.once("open", () => resolve(ws));
    ws.once("error", reject);
  });
}

function onceJson(ws: WebSocket): Promise<Record<string, unknown>> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("timed out waiting for message")), 4000);
    ws.once("message", (raw) => {
      clearTimeout(timer);
      resolve(JSON.parse(String(raw)) as Record<string, unknown>);
    });
  });
}

describe("spawned relay process vs bad inputs", () => {
  it("stays alive and keeps serving /health after every abusive frame and body", async () => {
    running = await startRelayProcess();
    expect(running.child.exitCode).toBeNull();
    expect(await health(running.url)).toMatchObject({ status: 200, body: { ok: true } });

    const cases: Array<{ name: string; send: (ws: WebSocket) => void; reason?: string }> = [
      { name: "empty string", send: (ws) => ws.send(""), reason: "bad_json" },
      { name: "zero-length buffer", send: (ws) => ws.send(Buffer.alloc(0)), reason: "bad_json" },
      { name: "json null (the crash)", send: (ws) => ws.send("null"), reason: "bad_payload" },
      { name: "non-json", send: (ws) => ws.send("{"), reason: "bad_json" },
      { name: "binary", send: (ws) => ws.send(Buffer.from([0xff, 0x00, 0xfe])), reason: "bad_payload" },
      { name: "missing fields", send: (ws) => ws.send(JSON.stringify({ type: "hello" })), reason: "bad_role" },
      {
        name: "malformed pairing code",
        send: (ws) => ws.send(JSON.stringify({ type: "hello", role: "phone", token: "abc" })),
        reason: "bad_token",
      },
      { name: "unknown type", send: (ws) => ws.send(JSON.stringify({ type: "nuke" })), reason: "unknown_type" },
    ];

    for (const testCase of cases) {
      const ws = await openSocket(running.wsUrl);
      const reply = onceJson(ws);
      testCase.send(ws);
      expect(await reply, testCase.name).toEqual({ type: "error", reason: testCase.reason });
      ws.terminate();
      expect(running.child.exitCode, `${testCase.name} killed the process`).toBeNull();
      const h = await health(running.url);
      expect(h.status, `${testCase.name} took down /health`).toBe(200);
      expect(h.body.ok).toBe(true);
    }

    const oversized = await fetch(running.url + "/pair", {
      method: "POST",
      headers: { "content-length": "99999", "content-type": "text/plain" },
      body: "x".repeat(400),
    });
    expect(oversized.status).toBe(413);
    expect(running.child.exitCode).toBeNull();
    expect((await health(running.url)).status).toBe(200);

    const huge = await openSocket(running.wsUrl);
    const closed = new Promise<{ code: number }>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("huge frame did not close")), 4000);
      huge.once("close", (code) => {
        clearTimeout(timer);
        resolve({ code });
      });
      huge.once("error", () => {
        /* ws emits error on 1009 */
      });
    });
    huge.send("y".repeat(8000));
    expect((await closed).code).toBe(1009);
    huge.terminate();
    expect(running.child.exitCode, "oversized frame killed the process").toBeNull();
    expect((await health(running.url)).status).toBe(200);

    const socks: WebSocket[] = [];
    for (let i = 0; i < 8; i++) {
      const ws = new WebSocket(running.wsUrl);
      ws.on("error", () => {});
      socks.push(ws);
    }
    await new Promise((resolve) => setTimeout(resolve, 300));
    for (const ws of socks) ws.terminate();
    expect(running.child.exitCode, "connect flood killed the process").toBeNull();
    expect((await health(running.url)).status).toBe(200);
  }, 20_000);
});
