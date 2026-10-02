import http from "node:http";
import { randomUUID } from "node:crypto";

import { NodeStreamableHTTPServerTransport, localhostHostValidation, localhostOriginValidation } from "@modelcontextprotocol/node";
import { McpServer, type ServerContext } from "@modelcontextprotocol/server";
import { z } from "zod";

import type { CommandResult, ProgressEvent, RequestOptions } from "@pony/client";

import { PhoneController, endedMessage, type ControllerOptions, type DisplayOpts } from "./controller.ts";

export const PONY_MCP_VERSION = "0.6.2";

const displayArgs = {
  background: z
    .boolean()
    .optional()
    .describe("Omit to follow the phone. Run in background is on by default, on every relay."),
  display: z
    .enum(["main", "background"])
    .optional()
    .describe('Wins over background. "main" forces the owner screen.'),
};

function displayOf(args: { background?: boolean; display?: "main" | "background" }): DisplayOpts {
  return { background: args.background, display: args.display };
}

const INSTRUCTIONS = `Pony controls one paired Android phone through the user's relay.

Call pair first and show the user the QR image or the pony://pair link. The QR is the same JSON the phone scans. The pony:// link, or the pairPageLink web page, is for when that QR is on the phone itself — the page opens Pony directly. A pairing token expires in 15 minutes.
The safety code is empty until the phone finishes the handshake. Call status and compare that code with the phone before you tap, type, or open apps.
The phone decides how long a session lasts (30 minutes by default; the owner can pick longer). status shows expiresAt. When it ends, commands are refused. Call pair again.
The session survives dropped connections. If a command fails with peer_away or connection_lost, wait a few seconds and retry; status.link shows "away" or "reconnecting" until the phone is back. peer_left means the owner ended the session.
Password fields are refused. Do not try to read or type them.
type inserts text at the cursor and does not open the keyboard. If method is key_events, warn the user.
For setup tasks (keyboard, languages, voice typing, autocorrect), open_settings jumps straight to a settings screen by name — input_method, keyboard_settings (the current keyboard's own options), languages, voice_input, or app_details (with packageName) — so taps don't get lost in menus that swallow touches on protected screens.
Call disconnect when the user is done.
The owner can also ask the phone for things, by voice or by typing. Keep calling wait_for_request in a loop while you are available: it returns requestId, text, and source, or {"empty":true}; call it again after empty. When you finish a request, call done with a one-sentence result; the phone shows it and says it for spoken requests. speak says a sentence out loud. ask_user asks and returns what they said. confirm asks for yes or no and returns {"accepted":true|false}. A no is not an error.
When who or what to act on is ambiguous — a name that matches several contacts, like more than one Sam — don't guess. Call ask_user to find out which one before you open the chat, send, or call. Guessing the wrong person is worse than asking.
The phone itself asks before sending, paying, buying, deleting, calling, or changing security settings. Do not bypass that. If the owner taps Stop, commands return stopped until they ask for something new. Call wait_for_request.
Calls and notifications never cancel a task. While the call screen is in front, the phone holds screen commands until it closes (up to 10 minutes) and reports progress; a pop-up over the target makes it wait up to 3 seconds. It never touches the call screen.
API keys for on-phone brains stay on the phone. Do not ask for them and do not put them in tool arguments.
Run in background is on by default on the phone. screenshot, tap, swipe, long_press, drag, pinch, type, key, open_app, open_settings, wait_idle, and ui_tree then target that background display, on the Pony Cloud relay and on a private or Tailscale relay. Omit background and display to follow the phone. Pass display "main" or background false to use the owner's screen. The result includes display, and may include warn. warn "popup_on_main_screen" means the app opened in a pop-up window on the owner's screen because it refused the hidden display.`;

export interface PonyMcp {
  server: McpServer;
  controller: PhoneController;
}

export function createPonyMcp(options: ControllerOptions): PonyMcp {
  const controller = new PhoneController(options);
  const server = new McpServer(
    { name: "pony", version: PONY_MCP_VERSION },
    { instructions: INSTRUCTIONS },
  );

  server.registerTool(
    "pair",
    {
      title: "Pair a phone",
      description:
        "Start a pairing session. Returns a QR PNG, the pony://pair link, a pairPageLink hand-off page (for when the QR is on the phone itself), the one-time token, and the QR JSON. safetyCode is null until the phone connects; call status to read it. Does not include the bot private key.",
      inputSchema: z.object({
        waitMs: z.number().int().min(0).max(60_000).optional(),
        relay: z.string().min(1).optional(),
      }),
    },
    async ({ waitMs, relay }) => {
      try {
        const info = await controller.pair({ waitMs, relay });
        const note = info.safetyCode
          ? "Compare this safety code with the phone before acting."
          : "Safety code is not ready until the phone connects. Call status and compare it before acting.";
        const summary = {
          token: info.token,
          pairingLink: info.pairingLink,
          pairPageLink: info.pairPageLink,
          payload: info.payload,
          safetyCode: info.safetyCode,
          expiresInMs: info.expiresInMs,
          sessionTtlMs: info.sessionTtlMs,
          note,
        };
        return {
          content: [
            { type: "text" as const, text: JSON.stringify(summary) },
            { type: "image" as const, data: info.qrPngBase64, mimeType: "image/png" },
          ],
          structuredContent: {
            qrPngDataUrl: info.qrPngDataUrl,
            pairingLink: info.pairingLink,
            pairPageLink: info.pairPageLink,
            token: info.token,
            safetyCode: info.safetyCode,
            payload: info.payload,
          },
        };
      } catch (err) {
        return toolError(messageOf(err));
      }
    },
  );

  server.registerTool(
    "status",
    {
      title: "Session status",
      description:
        "Report whether a phone is connected, the safety code, the link state (ready, away, reconnecting, ended), time remaining in the session, whether a standing wait is listening, and the action log. The log records character counts, not typed text.",
      inputSchema: z.object({}),
    },
    async () => {
      controller.touch();
      return { content: [{ type: "text" as const, text: JSON.stringify(controller.status()) }] };
    },
  );

  server.registerTool(
    "screenshot",
    {
      title: "Screenshot",
      description:
        "Capture a JPEG. With Run in background on, this is the background display. Pass display main to capture the owner's screen.",
      inputSchema: z.object(displayArgs),
    },
    async (args) => {
      try {
        const result = await controller.screenshot(displayOf(args));
        if (!result.ok) return toolError(explain(result));
        const jpeg = String(result.result?.jpeg_b64 ?? "");
        if (!jpeg) return toolError("screenshot returned no image");
        return {
          content: [
            {
              type: "text" as const,
              text: JSON.stringify({
                width: result.result?.width,
                height: result.result?.height,
                source: result.result?.source,
                display: result.result?.display,
                warn: result.result?.warn,
              }),
            },
            { type: "image" as const, data: jpeg, mimeType: "image/jpeg" },
          ],
        };
      } catch (err) {
        return toolError(messageOf(err));
      }
    },
  );

  server.registerTool(
    "ui_tree",
    {
      title: "UI tree",
      description:
        "Read the accessibility tree as text. Password fields are shown as [password] and their contents are not included.",
      inputSchema: z.object(displayArgs),
    },
    async (args) => textResult(() => controller.uiTree(displayOf(args))),
  );

  server.registerTool(
    "wait_idle",
    {
      title: "Wait for the screen to settle",
      description:
        "Wait until the screen stops changing — a page finished loading or an animation ended — instead of guessing a delay. Returns settled (true or false) and waitedMs. Use it after open_app or a tap that takes a moment, then read the screen.",
      inputSchema: z.object({
        timeoutMs: z.number().int().min(500).max(15_000).optional(),
        ...displayArgs,
      }),
    },
    async ({ timeoutMs, background, display }, ctx) =>
      textResult(() => controller.waitIdle(timeoutMs ?? 4_000, { background, display }, progressOf(ctx))),
  );

  server.registerTool(
    "tap",
    {
      title: "Tap",
      description: "Tap a point in screen pixels, origin at the top left.",
      inputSchema: z.object({
        x: z.number(),
        y: z.number(),
        ...displayArgs,
      }),
    },
    async ({ x, y, background, display }, ctx) =>
      textResult(() => controller.tap(x, y, { background, display }, progressOf(ctx))),
  );

  server.registerTool(
    "swipe",
    {
      title: "Swipe",
      description: "Swipe from one point to another. durationMs defaults to the phone's usual gesture length.",
      inputSchema: z.object({
        x1: z.number(),
        y1: z.number(),
        x2: z.number(),
        y2: z.number(),
        durationMs: z.number().int().positive().optional(),
        ...displayArgs,
      }),
    },
    async ({ x1, y1, x2, y2, durationMs, background, display }, ctx) =>
      textResult(() => controller.swipe(x1, y1, x2, y2, durationMs, { background, display }, progressOf(ctx))),
  );

  server.registerTool(
    "long_press",
    {
      title: "Long press",
      description:
        "Press and hold a point for a context menu, to select text, or to pick something up before a drag. durationMs defaults to about 600 ms.",
      inputSchema: z.object({
        x: z.number(),
        y: z.number(),
        durationMs: z.number().int().positive().optional(),
        ...displayArgs,
      }),
    },
    async ({ x, y, durationMs, background, display }, ctx) =>
      textResult(() => controller.longPress(x, y, durationMs, { background, display }, progressOf(ctx))),
  );

  server.registerTool(
    "drag",
    {
      title: "Drag",
      description:
        "Press at the start, move to the end, and release as one slow stroke — to reorder a list item or move something. Use swipe for scrolling or flicking. durationMs defaults to about 600 ms.",
      inputSchema: z.object({
        x1: z.number(),
        y1: z.number(),
        x2: z.number(),
        y2: z.number(),
        durationMs: z.number().int().positive().optional(),
        ...displayArgs,
      }),
    },
    async ({ x1, y1, x2, y2, durationMs, background, display }, ctx) =>
      textResult(() => controller.drag(x1, y1, x2, y2, durationMs, { background, display }, progressOf(ctx))),
  );

  server.registerTool(
    "pinch",
    {
      title: "Pinch to zoom",
      description:
        "Pinch two fingers around (x, y) to zoom a map or photo. fromDistance and toDistance are how far apart the fingers are, in pixels; make toDistance larger to zoom in, smaller to zoom out.",
      inputSchema: z.object({
        x: z.number(),
        y: z.number(),
        fromDistance: z.number().positive(),
        toDistance: z.number().positive(),
        durationMs: z.number().int().positive().optional(),
        ...displayArgs,
      }),
    },
    async ({ x, y, fromDistance, toDistance, durationMs, background, display }, ctx) =>
      textResult(() => controller.pinch(x, y, fromDistance, toDistance, durationMs, { background, display }, progressOf(ctx))),
  );

  server.registerTool(
    "type",
    {
      title: "Type text",
      description:
        "Type into the focused field. By default it replaces the field's text — it overwrites whatever is already there, so you don't need to clear it first. Pass mode:\"append\" to add to the end instead. The Pony keyboard is the primary path (method ime) when it owns the field, then ACTION_SET_TEXT. If the phone returns ime_disabled, the owner must enable the Pony keyboard. If it returns ime_required, the keyboard picker is open and they must choose Pony. Clipboard paste (method paste) happens only when the owner turned that setting on. Password fields are refused. If method is key_events, warn the user.",
      inputSchema: z.object({
        text: z.string(),
        mode: z.enum(["replace", "append"]).optional().describe("replace (default) overwrites the field; append adds to the end."),
        ...displayArgs,
      }),
    },
    async ({ text, mode, background, display }, ctx) =>
      textResult(() => controller.typeText(text, { mode, background, display }, progressOf(ctx))),
  );

  server.registerTool(
    "key",
    {
      title: "Press a key",
      description:
        "Press Back, Home, or Recents, or submit the focused field with an IME-action key. enter and search run the field's editor action (run a search, send, go). go/send/next/done pick the matching action when a field shows that specific button.",
      inputSchema: z.object({
        key: z.enum(["back", "home", "recents", "enter", "search", "go", "send", "next", "done"]),
        ...displayArgs,
      }),
    },
    async ({ key, background, display }, ctx) =>
      textResult(() => controller.key(key, { background, display }, progressOf(ctx))),
  );

  server.registerTool(
    "open_app",
    {
      title: "Open an app",
      description:
        "Open an installed app by its package name. If the app refuses the hidden display, the phone may ask the owner before using their screen; the result then has warn.",
      inputSchema: z.object({
        packageName: z.string().min(1),
        ...displayArgs,
      }),
    },
    async ({ packageName, background, display }, ctx) =>
      textResult(() => controller.openApp(packageName, { background, display }, progressOf(ctx))),
  );

  server.registerTool(
    "open_settings",
    {
      title: "Open a settings screen",
      description:
        'Jump straight to an Android settings screen instead of tapping through menus that can swallow touches. name is a screen like "input_method" (the keyboard list), "keyboard_settings" (the current keyboard\'s own options, where languages and voice typing live), "languages", "voice_input", "accessibility", "display", "sound", "wifi", "bluetooth", "location", "battery", "security", "date", or "app_details" (pass packageName). A raw android.settings.* action also works. Then read the screen and tap as usual.',
      inputSchema: z.object({
        name: z.string().min(1),
        packageName: z.string().min(1).optional(),
        ...displayArgs,
      }),
    },
    async ({ name, packageName, background, display }, ctx) =>
      textResult(() => controller.openSettings(name, packageName, { background, display }, progressOf(ctx))),
  );

  server.registerTool(
    "wait_for_request",
    {
      title: "Wait for the owner's next request",
      description: controller.listenMode
        ? "Return the next thing the owner asked Pony for, typed or spoken. This server keeps listening between calls, so nothing said in between is lost. Returns requestId, text, source, and heardMsAgo, or {empty:true} after the timeout; call it again. Calling it also marks your previous request finished, so call done first if you have a result to report."
        : "Long-poll for the next thing the owner asked Pony for, typed or spoken. Returns requestId, text, and source, or {empty:true} if they said nothing before the timeout. Call it again after empty. Does not include API keys.",
      inputSchema: z.object({
        timeoutMs: z.number().int().min(1_000).max(55_000).optional(),
      }),
    },
    async ({ timeoutMs }, ctx) => textResult(() => controller.waitForRequest(timeoutMs, ctx.mcpReq.signal)),
  );

  server.registerTool(
    "done",
    {
      title: "Finish the owner's request",
      description:
        "Tell the phone you finished the request from wait_for_request. text is a one-sentence result, for example \"2 + 2 is 4.\" The phone shows it in the task history and says it out loud for spoken requests. Pass ok false when you couldn't do it, with a short reason.",
      inputSchema: z.object({
        text: z.string().max(600).optional(),
        ok: z.boolean().optional(),
      }),
    },
    async ({ text, ok }) => textResult(() => controller.done(text ?? "", ok ?? true)),
  );

  server.registerTool(
    "speak",
    {
      title: "Speak",
      description: "Say a sentence out loud on the phone. The action log stores a character count, not the text.",
      inputSchema: z.object({
        text: z.string().min(1),
      }),
    },
    async ({ text }) => textResult(() => controller.speak(text)),
  );

  server.registerTool(
    "ask_user",
    {
      title: "Ask the owner",
      description: "Speak a question and return what the owner said. The log stores a character count, not the question.",
      inputSchema: z.object({
        text: z.string().min(1),
      }),
    },
    async ({ text }) => textResult(() => controller.askUser(text)),
  );

  server.registerTool(
    "confirm",
    {
      title: "Confirm with the owner",
      description:
        "Speak a yes-or-no question. Returns {accepted:true|false}. A no is a normal result, not an error. Ambiguous speech is not a yes.",
      inputSchema: z.object({
        text: z.string().min(1),
      }),
    },
    async ({ text }) => textResult(() => controller.confirm(text)),
  );

  server.registerTool(
    "disconnect",
    {
      title: "Disconnect",
      description: "End the session and drop the phone connection.",
      inputSchema: z.object({}),
    },
    async () => textResult(() => controller.disconnect()),
  );

  return { server, controller };
}

export interface ListeningMcp {
  url: string;
  port: number;
  close(): Promise<void>;
}

/** Localhost-only streamable HTTP. One process controls one phone. No auth. */
export async function listenMcp(options: ControllerOptions & { port: number; host?: string }): Promise<ListeningMcp> {
  const { server, controller } = createPonyMcp(options);
  const transport = new NodeStreamableHTTPServerTransport({
    sessionIdGenerator: () => randomUUID(),
  });
  await server.connect(transport);

  const validateHost = localhostHostValidation();
  const validateOrigin = localhostOriginValidation();
  const host = options.host ?? "127.0.0.1";
  const httpServer = http.createServer(async (req, res) => {
    if (!validateHost(req, res)) return;
    if (!validateOrigin(req, res)) return;
    try {
      await transport.handleRequest(req, res);
    } catch (err) {
      console.error("pony-mcp http error", err instanceof Error ? err.message : err);
      if (!res.headersSent) {
        res.writeHead(500, { "content-type": "application/json" });
      }
      if (!res.writableEnded) res.end(JSON.stringify({ error: "mcp request failed" }));
    }
  });

  await new Promise<void>((resolve) => {
    httpServer.listen(options.port, host, () => resolve());
  });
  const address = httpServer.address();
  const port = typeof address === "object" && address ? address.port : options.port;

  return {
    url: `http://${host}:${port}/mcp`,
    port,
    async close() {
      await controller.shutdown().catch(() => undefined);
      await transport.close().catch(() => undefined);
      httpServer.closeAllConnections();
      await new Promise<void>((resolve) => {
        httpServer.close(() => resolve());
      });
    },
  };
}

async function textResult(run: () => Promise<CommandResult>) {
  try {
    const result = await run();
    if (!result.ok) return toolError(explain(result));
    return { content: [{ type: "text" as const, text: commandText(result) }] };
  } catch (err) {
    return toolError(messageOf(err));
  }
}

function commandText(result: CommandResult): string {
  const body = JSON.stringify(result);
  const method = result.result?.method;
  const warn = result.result?.warn;
  if (method === "key_events") {
    const extra = typeof warn === "string" && warn ? warn : "Typing fell back to key events.";
    return `${body}\n\nWarn the user: ${extra}`;
  }
  return body;
}

function explain(result: CommandResult): string {
  if (result.error === "password_field") {
    return "Refused: the focused field is a password field (password_field). Pony will not read or type into it.";
  }
  if (result.error === "ime_disabled") {
    return "The Pony keyboard is not enabled (ime_disabled). On the phone, open Pony and tap Enable Pony keyboard, then retry. Pony will not switch keyboards by itself.";
  }
  if (result.error === "ime_required") {
    return "The keyboard picker is open (ime_required). Ask the owner to choose Pony keyboard, then retry. Their usual keyboard stays available.";
  }
  if (result.error === "locked") {
    return "The phone is locked (locked). Pony will not dismiss the lock screen. Ask the owner to unlock, then retry.";
  }
  if (result.error === "stopped") {
    return "The owner stopped Pony (stopped). Call wait_for_request and act only on their next request.";
  }
  if (result.error === "not_confirmed") {
    return "The owner did not confirm that action (not_confirmed). Do not retry it unless they ask.";
  }
  if (result.error === "in_call") {
    return "A call is on the phone (in_call). Pony will not cover the call. Retry when it ends, or keep the action on the background display.";
  }
  if (result.error === "call_ui_foreground") {
    return "The call screen is in front on the phone (call_ui_foreground). Pony never touches the call screen. Use the background display, or retry after the call screen closes.";
  }
  if (result.error === "call_ui_timeout") {
    return "The call screen stayed in front for the whole wait (call_ui_timeout), so nothing was done. The task is still on; retry when the owner is off the call screen.";
  }
  if (result.error === "covered_by_popup") {
    const by = typeof result.result?.coveredBy === "string" ? ` by ${result.result.coveredBy}` : "";
    return `A pop-up covered that spot${by} and didn't go away (covered_by_popup). Pony didn't tap it. Take a screenshot and try again.`;
  }
  if (result.error === "background_refused") {
    return "That app won't run on Pony's hidden display, and the owner didn't allow their screen (background_refused). Ask the owner, or pass display main.";
  }
  if (result.error === "accessibility_off") {
    return "Pony control is off on the phone (accessibility_off). Ask the owner to turn it on in Pony's setup, then retry.";
  }
  if (result.error === "peer_away") {
    return "The phone dropped off the relay for a moment (peer_away). Pony is holding the session. Wait a few seconds and retry.";
  }
  if (result.error === "connection_lost") {
    return "Lost the connection to the relay (connection_lost). Pony is reconnecting. Wait a few seconds and retry.";
  }
  if (result.error === "peer_left" || result.error === "unknown_token" || result.error === "expired_token" || result.error === "room_closed") {
    return endedMessage(result.error);
  }
  if (result.error?.startsWith("unknown_op:")) {
    return `The phone runs an older Pony that doesn't know this command (${result.error}). Ask the owner to update Pony.`;
  }
  if (result.error === "background_key_needs_shell") {
    return "Back, Home, and Recents are not display-scoped (background_key_needs_shell). Grant the optional Shizuku shell, or pass display main.";
  }
  if (result.error === "background_type_failed") {
    return "Pony could not set text on the background display (background_type_failed). It will not type into the owner's screen instead.";
  }
  if (result.error === "background_empty") {
    return "Nothing is on Pony's hidden display yet (background_empty). Open an app there first, or pass display main to read the owner's screen.";
  }
  return result.error ?? "command failed";
}

/** Forwards the phone's "still waiting" reports as MCP progress, so clients that reset timeouts on progress keep waiting. */
function progressOf(ctx: ServerContext): RequestOptions | undefined {
  const token = ctx.mcpReq._meta?.progressToken;
  if (token === undefined) return undefined;
  return {
    onProgress: (event: ProgressEvent) => {
      void ctx.mcpReq
        .notify({
          method: "notifications/progress",
          params: {
            progressToken: token,
            progress: Math.max(0, event.waitedMs),
            total: event.limitMs,
            message: waitingText(event.reason),
          },
        })
        .catch(() => undefined);
    },
  };
}

function waitingText(reason: string): string {
  switch (reason) {
    case "call_ui_foreground":
      return "Waiting for the call screen to close on the phone.";
    case "covered_by_popup":
      return "Waiting for a pop-up to clear on the phone.";
    case "awaiting_owner":
      return "Waiting for the owner to answer on the phone.";
    default:
      return "The phone is holding this command.";
  }
}

function toolError(text: string) {
  return {
    content: [{ type: "text" as const, text }],
    isError: true as const,
  };
}

function messageOf(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
