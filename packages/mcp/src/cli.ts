#!/usr/bin/env node
import { parseArgs } from "node:util";

import { StdioServerTransport } from "@modelcontextprotocol/server/stdio";
import { DEFAULT_RELAY } from "@pony/shared";
import { defaultStatePath } from "@pony/client/listen";

import { createPonyMcp, listenMcp } from "./server.ts";

const { values } = parseArgs({
  options: {
    http: { type: "string" },
    relay: { type: "string" },
    client: { type: "string" },
    listen: { type: "boolean" },
    state: { type: "string" },
  },
  strict: true,
});

const relayHttp = values.relay ?? process.env.PONY_RELAY ?? DEFAULT_RELAY;
const clientName = values.client ?? process.env.PONY_CLIENT_NAME ?? "Grok Bot";
const listen = values.listen === true || process.env.PONY_LISTEN === "1";
const statePath = listen ? values.state ?? process.env.PONY_STATE ?? defaultStatePath("mcp.json") : values.state;
const options = { relayHttp, clientName, listen, statePath };

if (values.http !== undefined) {
  const port = Number(values.http);
  if (!Number.isInteger(port) || port < 0 || port > 65535) {
    console.error("--http expects a port number");
    process.exit(1);
  }
  const listening = await listenMcp({ ...options, port, host: "127.0.0.1" });
  console.error(`pony-mcp streamable HTTP on ${listening.url} (localhost only, one phone)`);
  if (listen) console.error(`Listen mode on. The pairing is kept in ${statePath}.`);
  const stop = () => void listening.close().finally(() => process.exit(0));
  process.once("SIGINT", stop);
  process.once("SIGTERM", stop);
} else {
  const { server, controller } = createPonyMcp(options);
  if (listen) console.error(`pony-mcp listen mode. The pairing is kept in ${statePath}.`);
  let stopping = false;
  const stop = () => {
    if (stopping) return;
    stopping = true;
    void controller.shutdown().finally(() => process.exit(0));
  };
  process.stdin.once("end", stop);
  process.once("SIGINT", stop);
  process.once("SIGTERM", stop);
  await server.connect(new StdioServerTransport());
}
