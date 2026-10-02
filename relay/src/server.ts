import http from "node:http";
import { URL } from "node:url";

import { PAIRING_TTL_MS, parsePairing, randomTokenHex, type RelayInbound, type Role } from "@pony/shared";
import { WebSocket, WebSocketServer } from "ws";

import { PairingHub, type PeerSocket } from "./rooms.ts";

export interface RelayOptions {
  host?: string;
  port?: number;
  ttlMs?: number;
  now?: () => number;
  publicUrl?: string;
  /** How long an established room waits for a peer that dropped. */
  resumeGraceMs?: number;
  /** Hard cap on an established room's life. */
  maxRoomMs?: number;
  /** Ping interval. A socket that misses one pong is dropped. */
  heartbeatMs?: number;
}

interface ClientMeta {
  token: string;
  role: Role;
  peer: PeerSocket;
}

export interface StartedRelay {
  url: string;
  port: number;
  host: string;
  hub: PairingHub;
  close(): Promise<void>;
}

function json(res: http.ServerResponse, status: number, body: unknown): void {
  const data = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "access-control-allow-origin": "*",
    "cache-control": "no-store",
  });
  res.end(data);
}

function html(res: http.ServerResponse, body: string): void {
  res.writeHead(200, { "content-type": "text/html; charset=utf-8" });
  res.end(body);
}

export function createRelay(options: RelayOptions = {}): Promise<StartedRelay> {
  const host = options.host ?? "0.0.0.0";
  const port = options.port ?? Number(process.env.PORT ?? 8787);
  const hub = new PairingHub(options.ttlMs ?? PAIRING_TTL_MS, options.now ?? (() => Date.now()), {
    resumeGraceMs: options.resumeGraceMs,
    maxRoomMs: options.maxRoomMs,
  });
  const publicUrl = options.publicUrl ?? process.env.PONY_RELAY_URL;

  const server = http.createServer((req, res) => {
    const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "localhost"}`);

    if (req.method === "OPTIONS") {
      res.writeHead(204, {
        "access-control-allow-origin": "*",
        "access-control-allow-methods": "GET,POST,OPTIONS",
        "access-control-allow-headers": "content-type",
      });
      res.end();
      return;
    }

    if (req.method === "GET" && url.pathname === "/health") {
      json(res, 200, { ok: true, rooms: hub.size(), pairingTtlMs: options.ttlMs ?? PAIRING_TTL_MS, resume: true });
      return;
    }

    if (req.method === "POST" && url.pathname === "/pair") {
      const token = randomTokenHex();
      const created = hub.createToken(token);
      json(res, 200, {
        token: created.token,
        expiresAt: created.expiresAt,
        expiresInMs: (options.ttlMs ?? PAIRING_TTL_MS),
        wsUrl: wsUrlFromRequest(req, publicUrl),
      });
      return;
    }

    if (req.method === "GET" && url.pathname === "/") {
      html(res, landingPage(hub.size()));
      return;
    }

    json(res, 404, { error: "not_found" });
  });

  const wss = new WebSocketServer({ server, path: "/ws" });
  const meta = new WeakMap<WebSocket, ClientMeta>();
  const alive = new WeakMap<WebSocket, boolean>();

  const heartbeat = setInterval(() => {
    for (const client of wss.clients) {
      if (alive.get(client) === false) {
        client.terminate();
        continue;
      }
      alive.set(client, false);
      try {
        client.ping();
      } catch {
        client.terminate();
      }
    }
    for (const orphan of hub.gc()) orphan.close(4000, "room_closed");
  }, options.heartbeatMs ?? 20_000);
  heartbeat.unref?.();

  wss.on("connection", (socket) => {
    alive.set(socket, true);
    socket.on("pong", () => alive.set(socket, true));
    const peer: PeerSocket = {
      send(data) {
        if (socket.readyState === WebSocket.OPEN) socket.send(data);
      },
      close(code, reason) {
        socket.close(code, reason);
      },
    };

    socket.on("message", (raw) => {
      alive.set(socket, true);
      let msg: RelayInbound;
      try {
        msg = JSON.parse(String(raw)) as RelayInbound;
      } catch {
        socket.send(JSON.stringify({ type: "error", reason: "bad_json" }));
        return;
      }

      if (msg.type === "hello") {
        if (meta.has(socket)) {
          socket.send(JSON.stringify({ type: "error", reason: "already_joined" }));
          return;
        }
        if (msg.role !== "bot" && msg.role !== "phone") {
          socket.send(JSON.stringify({ type: "error", reason: "bad_role" }));
          return;
        }
        try {
          parsePairing(
            JSON.stringify({
              v: 1,
              relay: "ws://placeholder",
              token: msg.token,
              pk: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            }),
          );
        } catch {
          socket.send(JSON.stringify({ type: "error", reason: "bad_token" }));
          return;
        }

        const joined = hub.join(msg.token, msg.role, peer, msg.resume === true);
        if ("error" in joined) {
          socket.send(JSON.stringify({ type: "error", reason: joined.error }));
          socket.close(4000, joined.error);
          return;
        }
        joined.replaced?.close(4002, "replaced");
        const client = typeof msg.client === "string" ? msg.client.trim().slice(0, 40) : "";
        if (msg.role === "bot" && client) joined.room.clientName = client;
        meta.set(socket, { token: msg.token, role: msg.role, peer });
        if (hub.bothPresent(joined.room)) {
          const name = joined.room.clientName;
          const ready = (role: Role) =>
            JSON.stringify({
              type: "ready",
              role,
              ...(name ? { client: name } : {}),
              ...(joined.resumed ? { resumed: true } : {}),
            });
          joined.room.bot?.send(ready("bot"));
          joined.room.phone?.send(ready("phone"));
        } else {
          socket.send(JSON.stringify({ type: "waiting" }));
        }
        return;
      }

      if (msg.type === "bye") {
        const who = meta.get(socket);
        if (!who) return;
        meta.delete(socket);
        const other = hub.end(who.token, who.role);
        other?.send(JSON.stringify({ type: "peer_left" }));
        other?.close(4001, "peer_left");
        socket.close(1000, "bye");
        return;
      }

      if (msg.type === "fwd") {
        const who = meta.get(socket);
        if (!who) {
          socket.send(JSON.stringify({ type: "error", reason: "hello_required" }));
          return;
        }
        const dest = hub.peerOf(who.token, who.role);
        if (!dest) {
          socket.send(JSON.stringify({ type: "error", reason: "peer_missing" }));
          return;
        }
        dest.send(JSON.stringify({ type: "fwd", data: msg.data }));
        return;
      }

      socket.send(JSON.stringify({ type: "error", reason: "unknown_type" }));
    });

    socket.on("close", () => {
      const who = meta.get(socket);
      if (!who) return;
      const left = hub.leave(who.token, who.role, who.peer);
      if (!left?.peer) return;
      if (left.away) {
        left.peer.send(JSON.stringify({ type: "peer_away" }));
      } else {
        left.peer.send(JSON.stringify({ type: "peer_left" }));
        left.peer.close(4001, "peer_left");
      }
    });
  });

  return new Promise((resolve, reject) => {
    server.listen(port, host, () => {
      const addr = server.address();
      if (!addr || typeof addr === "string") {
        reject(new Error("failed to bind relay"));
        return;
      }
      const actualPort = addr.port;
      const actualHost = host === "0.0.0.0" ? "127.0.0.1" : host;
      resolve({
        url: `http://${actualHost}:${actualPort}`,
        port: actualPort,
        host: actualHost,
        hub,
        close: () =>
          new Promise((done, fail) => {
            clearInterval(heartbeat);
            for (const client of wss.clients) client.terminate();
            wss.close();
            server.closeAllConnections?.();
            server.close((err) => (err ? fail(err) : done()));
          }),
      });
    });
    server.on("error", reject);
  });
}

function wsUrlFromRequest(req: http.IncomingMessage, publicUrl?: string): string {
  if (publicUrl) {
    return publicUrl.replace(/^http/, "ws").replace(/\/$/, "") + "/ws";
  }
  const host = req.headers.host ?? "127.0.0.1:8787";
  const proto = req.headers["x-forwarded-proto"] === "https" ? "wss" : "ws";
  return `${proto}://${host}/ws`;
}

function landingPage(rooms: number): string {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Pony relay</title>
  <style>
    :root { color-scheme: dark; }
    body { font-family: ui-sans-serif, system-ui, sans-serif; margin: 0; background: #0b0a13; color: #f4f2fb; }
    main { max-width: 40rem; margin: 4rem auto; padding: 0 1.25rem; }
    h1 { font-size: 1.6rem; letter-spacing: -0.03em; }
    code { background: #1d1c2a; padding: 0.1rem 0.35rem; border-radius: 4px; }
    .ok { color: #a195ff; }
    p { line-height: 1.5; color: #aba7bf; }
  </style>
</head>
<body>
  <main>
    <p class="ok">Pony relay is up</p>
    <h1>Untrusted WebSocket pairing room</h1>
    <p>This process only matches a phone and a bot by a one-time token. Frames are opaque. It cannot decrypt screenshots or commands.</p>
    <p>Established sessions survive a dropped connection: either side can rejoin with the same token for a while.</p>
    <p>Open rooms: <strong>${rooms}</strong></p>
    <p>Health: <code>GET /health</code> · Pair: <code>POST /pair</code> · Socket: <code>/ws</code></p>
  </main>
</body>
</html>`;
}
