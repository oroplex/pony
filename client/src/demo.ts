/**
 * Scripted end-to-end demo: real relay + bot client + mock phone.
 * Usage: npm run demo
 */
import type { WebSocket } from "ws";

import { createRelay } from "../../relay/src/server.ts";

import { RequestListener } from "./listen.ts";
import { MockPhone } from "./mock-phone.ts";
import { PonySession } from "./session.ts";

const FAST = { initialMs: 100, maxMs: 1_000 };

async function main(): Promise<void> {
  const relay = await createRelay({ host: "127.0.0.1", port: 0 });
  console.log(`relay ${relay.url}`);

  const bot = await PonySession.createBot({ relayHttp: relay.url, clientName: "Grok Bot", reconnect: FAST });
  console.log("pairing payload:");
  console.log(bot.qrJson());

  const phone = new MockPhone();
  phone.requests = [];
  const phoneSession = await PonySession.createPhone(bot.qrJson(), undefined, { reconnect: FAST });
  phone.attach(phoneSession);

  await Promise.all([bot.waitUntilReady(), phoneSession.waitUntilReady()]);
  console.log(`safety code  ${bot.safetyCode()}  (phone: ${phoneSession.safetyCode()})`);

  const steps: Array<[string, () => Promise<unknown>]> = [
    ["ping", () => bot.request("ping")],
    ["info", () => bot.request("info")],
    ["screenshot", () => bot.request("screenshot")],
    ["tap", () => bot.request("tap", { x: 120, y: 340 })],
    ["swipe", () => bot.request("swipe", { x1: 100, y1: 800, x2: 100, y2: 200, durationMs: 180 })],
    ["type", () => bot.request("type", { text: "hello from pony" })],
    ["press", () => bot.request("press", { key: "home" })],
    ["open_app", () => bot.request("open_app", { packageName: "com.android.settings" })],
    ["ui_tree", () => bot.request("ui_tree")],
  ];

  for (const [name, run] of steps) {
    const result = await run();
    console.log(`${name.padEnd(14)} ${short(result)}`);
  }

  phone.focusedIsPassword = true;
  const denied = await bot.request("type", { text: "secret" });
  console.log(`${"type-password".padEnd(14)} ${short(denied)}`);
  phone.focusedIsPassword = false;

  console.log("\nthe phone loses its connection…");
  (phoneSession as unknown as { ws?: WebSocket }).ws?.terminate();
  await waitFor(() => bot.state === "away");
  console.log(`bot sees       ${bot.state}`);
  await bot.waitUntilReady(10_000);
  console.log(`bot sees       ${bot.state} (resumed: ${bot.link().resumed}), same safety code ${bot.safetyCode()}`);
  console.log(`${"tap".padEnd(14)} ${short(await bot.request("tap", { x: 5, y: 6 }))}`);

  console.log("\nlisten: the owner asks for something on the phone…");
  const listener = new RequestListener(bot, {
    pollMs: 2_000,
    onRequest: async (request) => {
      console.log(`heard          “${request.text}” (${request.source})`);
      await bot.request("open_app", { packageName: "com.google.android.calculator" });
      await bot.request("done", { ref: request.requestId, text: "2 + 2 is 4." });
    },
  });
  listener.start();
  await waitFor(() => phone.waiting > 0);
  phone.say("open the calculator and add 2 + 2", "voice");
  await waitFor(() => phone.finished.length > 0);
  console.log(`phone shows    “${phone.finished[0].text}”`);
  listener.dispose();

  await bot.end();
  await waitFor(() => phoneSession.state === "ended");
  console.log(`\nbot ended the session; phone sees ${phoneSession.state} (${phoneSession.link().reason})`);
  console.log(`${"late request".padEnd(14)} ${short(await bot.request("ping"))}`);
  await relay.close();
  console.log("demo ok");
}

function short(result: unknown): string {
  const text = JSON.stringify(result, (key, value) =>
    key === "jpeg_b64" ? `<${String(value).length} chars>` : key === "tree" ? "<tree>" : value,
  );
  return text.length > 140 ? `${text.slice(0, 137)}…` : text;
}

async function waitFor(check: () => boolean, timeoutMs = 10_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (!check()) {
    if (Date.now() > deadline) throw new Error("demo step timed out");
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
