import { createRelay } from "./server.ts";

const port = Number(process.env.PORT ?? 8787);
const host = process.env.HOST ?? "0.0.0.0";

const relay = await createRelay({ host, port });
console.log(`Pony relay listening on ${relay.url}`);
console.log(`WebSocket ${relay.url.replace(/^http/, "ws")}/ws`);
