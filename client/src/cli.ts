#!/usr/bin/env node
import { createWriteStream } from "node:fs";
import { stdin as input, stdout as output } from "node:process";
import * as readline from "node:readline/promises";

import { Command } from "commander";
import QRCode from "qrcode";
import qrterminal from "qrcode-terminal";

import { DEFAULT_RELAY, PAIRING_TTL_MS, pairPageLink, pairingLink, type PressKey } from "@pony/shared";

import { SessionFile, defaultStatePath, runListen, type ListenEvent } from "./listen.ts";
import { takeDisplay } from "./repl-args.ts";
import { PonySession } from "./session.ts";

const program = new Command();
program
  .name("pony-phone")
  .description("Bot-side client for the Pony Companion phone")
  .option("-r, --relay <url>", "Relay HTTP origin", process.env.PONY_RELAY ?? DEFAULT_RELAY)
  .option("--client <name>", "Name to show on the phone after pairing");

program
  .command("listen")
  .description("Stay paired and hear every request the owner makes, through network drops and restarts")
  .option("--state <file>", "Where the pairing is kept (holds a private key)", process.env.PONY_STATE ?? defaultStatePath("listen.json"))
  .option("--exec <command>", "Run this for each request. The text is in $PONY_REQUEST_TEXT and on stdin; what it prints is the reply")
  .option("--exec-timeout <seconds>", "Longest a --exec command may run", "120")
  .option("--new", "End the saved pairing and pair again")
  .option("--json", "Print events as JSON lines on stdout (messages go to stderr)")
  .option("--png <file>", "Also write the pairing QR to a PNG file")
  .action(async (opts: { state: string; exec?: string; execTimeout: string; new?: boolean; json?: boolean; png?: string }, cmd) => {
    const relay = cmd.optsWithGlobals().relay as string;
    const client = cmd.optsWithGlobals().client as string | undefined;
    const human = opts.json ? (line: string) => console.error(line) : (line: string) => console.log(line);
    const emit = opts.json ? (event: ListenEvent) => console.log(JSON.stringify(event)) : undefined;
    const timeoutSec = Number(opts.execTimeout);
    const handle = await runListen({
      relayHttp: relay,
      statePath: opts.state,
      clientName: client,
      exec: opts.exec,
      execTimeoutMs: Number.isFinite(timeoutSec) && timeoutSec > 0 ? timeoutSec * 1000 : 120_000,
      fresh: Boolean(opts.new),
      emit,
      say: human,
      showPairing: async ({ payload, link, pageLink }) => {
        human("Scan this QR with Pony on your phone. It expires in 15 minutes.");
        human(`Or open this link on the phone: ${link}`);
        human(`If this terminal is on the phone, open: ${pageLink}\n`);
        human(await terminalQr(payload));
        if (opts.png) {
          await QRCode.toFile(opts.png, payload, { width: 360, margin: 2 });
          human(`Wrote ${opts.png}`);
        }
        human("Waiting for the phone…");
      },
    });
    let stopping = false;
    const stop = () => {
      if (stopping) return;
      stopping = true;
      void handle.stop().then(() => {
        human("Stopped listening. Run the same command to pick this session up again.");
        process.exit(0);
      });
    };
    process.once("SIGINT", stop);
    process.once("SIGTERM", stop);
    const reason = await handle.finished;
    if (!stopping) process.exit(reason === "stopped" ? 0 : 1);
  });

program
  .command("unpair")
  .description("End the session saved by `listen` and delete it")
  .option("--state <file>", "The saved pairing", process.env.PONY_STATE ?? defaultStatePath("listen.json"))
  .action(async (opts: { state: string }) => {
    const store = new SessionFile(opts.state);
    const saved = store.load();
    if (!saved) {
      console.log("Nothing to unpair.");
      return;
    }
    const session = PonySession.resumeBot(saved);
    await session.untilReady(4_000);
    await session.end();
    store.clear();
    console.log("Unpaired. The phone was told the session is over.");
  });

program
  .command("pair")
  .description("Create a pairing token, print a QR code, and wait for the phone")
  .option("--png <file>", "Also write the QR to a PNG file")
  .action(async (opts: { png?: string }, cmd) => {
    const relay = cmd.optsWithGlobals().relay as string;
    const client = cmd.optsWithGlobals().client as string | undefined;
    const session = await PonySession.createBot({ relayHttp: relay, clientName: client });
    const payload = session.qrJson();
    const clientQuery = client?.trim().toLowerCase() === "grok bot" ? "grokbot" : client?.trim() || undefined;
    console.log("Scan this QR with Pony Companion. Pairing expires in 15 minutes.\n");
    console.log(payload);
    console.log(`\nOr open this link on the phone: ${pairingLink(JSON.parse(payload), clientQuery)}`);
    console.log(`If this screen is on the phone, open: ${pairPageLink(JSON.parse(payload), clientQuery)}`);
    console.log("");
    qrterminal.generate(payload, { small: true });
    if (opts.png) {
      await QRCode.toFile(opts.png, payload, { width: 360, margin: 2 });
      console.log(`Wrote ${opts.png}`);
    }
    console.log("\nWaiting for the phone…");
    await session.waitUntilReady(PAIRING_TTL_MS);
    console.log(`Paired. Safety code: ${session.safetyCode()}`);
    console.log("Confirm this same code on the phone, then send commands.\n");
    await repl(session);
    await session.end();
  });

for (const spec of [
  ["screenshot", "Capture a JPEG screenshot"],
  ["tree", "Read the current UI tree as text"],
  ["ping", "Round-trip check"],
] as const) {
  program
    .command(spec[0])
    .description(spec[1])
    .requiredOption("--session-json <payload>", "Pairing JSON from `pair` (advanced)")
    .action(() => {
      console.error("Use `pony-phone pair` and type commands in the REPL.");
      process.exitCode = 2;
    });
}

program
  .command("repl")
  .description("Hidden: used after pair")
  .action(() => {
    console.error("Run `pony-phone pair` instead.");
    process.exitCode = 2;
  });

program.parse();

function terminalQr(payload: string): Promise<string> {
  return new Promise((resolve) => qrterminal.generate(payload, { small: true }, (qr: string) => resolve(qr)));
}

async function repl(session: PonySession): Promise<void> {
  const rl = readline.createInterface({ input, output });
  printHelp();
  try {
    while (true) {
      const line = (await rl.question("pony> ")).trim();
      if (!line) continue;
      const [cmd, ...rest] = line.split(/\s+/);
      if (cmd === "quit" || cmd === "exit" || cmd === "disconnect") {
        await session.request("disconnect").catch(() => undefined);
        break;
      }
      if (cmd === "help") {
        printHelp();
        continue;
      }
      try {
        const result = await dispatch(session, cmd, rest);
        if (result.result?.method === "key_events") {
          console.warn(
            "WARNING: the phone fell back to key events. Check that field before continuing.",
          );
          if (result.result.warn) console.warn(String(result.result.warn));
        }
        if (cmd === "screenshot" && result.ok && result.result?.jpeg_b64) {
          const file = takeDisplay(rest).args[0] ?? "screenshot.jpg";
          const buf = Buffer.from(String(result.result.jpeg_b64), "base64");
          createWriteStream(file).end(buf);
          console.log(`wrote ${file} (${buf.length} bytes)`);
        } else {
          console.log(JSON.stringify(result, null, 2));
        }
      } catch (err) {
        console.error(err instanceof Error ? err.message : err);
      }
    }
  } finally {
    rl.close();
  }
}

async function dispatch(session: PonySession, cmd: string, rest: string[]) {
  const { args, display } = takeDisplay(rest);
  const where = display ? { display } : {};
  switch (cmd) {
    case "screenshot":
      return session.request("screenshot", where);
    case "tree":
    case "ui_tree":
      return session.request("ui_tree", where);
    case "wait_idle":
    case "wait":
      return session.request("wait_idle", { timeoutMs: args[0] ? num(args[0]) : 4000, ...where }, 20_000);
    case "ping":
      return session.request("ping");
    case "tap":
      return session.request("tap", { x: num(args[0]), y: num(args[1]), ...where });
    case "swipe":
      return session.request("swipe", {
        x1: num(args[0]),
        y1: num(args[1]),
        x2: num(args[2]),
        y2: num(args[3]),
        durationMs: args[4] ? num(args[4]) : 250,
        ...where,
      });
    case "long_press":
    case "longpress":
      return session.request("long_press", {
        x: num(args[0]),
        y: num(args[1]),
        durationMs: args[2] ? num(args[2]) : 600,
        ...where,
      });
    case "drag":
      return session.request("drag", {
        x1: num(args[0]),
        y1: num(args[1]),
        x2: num(args[2]),
        y2: num(args[3]),
        durationMs: args[4] ? num(args[4]) : 600,
        ...where,
      });
    case "pinch":
      return session.request("pinch", {
        x: num(args[0]),
        y: num(args[1]),
        fromDistance: num(args[2]),
        toDistance: num(args[3]),
        durationMs: args[4] ? num(args[4]) : 300,
        ...where,
      });
    case "type": {
      const append = args[0] === "--append";
      const words = append ? args.slice(1) : args;
      return session.request("type", { text: words.join(" "), ...(append ? { mode: "append" as const } : {}), ...where }, 45_000);
    }
    case "press":
      return session.request("press", { key: args[0] as PressKey, ...where });
    case "open":
    case "open_app":
      return session.request("open_app", { packageName: args[0], ...where });
    case "settings":
    case "open_settings":
      return session.request("open_settings", {
        name: args[0],
        ...(args[1] ? { packageName: args[1] } : {}),
        ...where,
      });
    default:
      throw new Error(`unknown command: ${cmd}`);
  }
}

function num(v: string | undefined): number {
  const n = Number(v);
  if (!Number.isFinite(n)) throw new Error(`expected a number, got ${v}`);
  return n;
}

function printHelp(): void {
  console.log(`Commands (pass --main or --background anywhere; a trailing main|background also works):
  screenshot [file.jpg] [--main|--background]
  tap <x> <y> [--main|--background]
  swipe <x1> <y1> <x2> <y2> [durationMs] [--main|--background]
  long_press <x> <y> [durationMs] [--main|--background]
  drag <x1> <y1> <x2> <y2> [durationMs] [--main|--background]
  pinch <x> <y> <fromDistance> <toDistance> [durationMs] [--main|--background]
  type <text> [--main|--background]
  press back|home|recents|enter|search|go|send|next|done [--main|--background]
  open <package.name> [--main|--background]
  settings <screen> [package] [--main|--background]
  tree [--main|--background]
  wait_idle [timeoutMs] [--main|--background]
  ping
  disconnect

The phone setting is the default when you omit main or background.
Run in background is on unless the owner turned it off.`);
}
