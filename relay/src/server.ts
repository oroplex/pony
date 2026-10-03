import http from "node:http";
import { URL } from "node:url";

import { PAIRING_TTL_MS, randomTokenHex, type Role } from "@pony/shared";
import { WebSocket, WebSocketServer } from "ws";

import {
  clientIp,
  Gauge,
  mergeLimits,
  type RelayLimits,
  WindowCounter,
} from "./limits.ts";
import { PairingHub, type PeerSocket } from "./rooms.ts";
import { frameText, validateInbound } from "./validate.ts";

export type { RelayLimits } from "./limits.ts";
export { DEFAULT_LIMITS, limitsFromEnv } from "./limits.ts";

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
  /** Per-IP and payload caps. Defaults keep 0.6.4 screenshot frames under the limit. */
  limits?: Partial<RelayLimits>;
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
  limits: RelayLimits;
  close(): Promise<void>;
}

const CLOSE_BAD = 4003;
const CLOSE_RATE = 4008;

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

function safeSend(socket: WebSocket, data: string): void {
  try {
    if (socket.readyState === WebSocket.OPEN) socket.send(data);
  } catch (err) {
    console.error("[relay] send failed", err);
  }
}

function safeClose(socket: WebSocket, code: number, reason: string): void {
  try {
    if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
      socket.close(code, reason.slice(0, 123));
    }
  } catch {
    try {
      socket.terminate();
    } catch {
      /* already gone */
    }
  }
}

function rejectSocket(socket: WebSocket, reason: string, code = CLOSE_BAD): void {
  safeSend(socket, JSON.stringify({ type: "error", reason }));
  safeClose(socket, code, reason);
}

function contentLengthTooLarge(req: http.IncomingMessage, maxBytes: number): boolean {
  const raw = req.headers["content-length"];
  if (raw === undefined) return false;
  const len = Number(raw);
  return Number.isFinite(len) && len > maxBytes;
}

function capUnreadBody(req: http.IncomingMessage, maxBytes: number): void {
  let seen = 0;
  req.on("data", (chunk: Buffer) => {
    seen += chunk.length;
    if (seen > maxBytes) req.destroy();
  });
  req.on("error", () => {
    /* inbound reset; do not throw */
  });
}

export function createRelay(options: RelayOptions = {}): Promise<StartedRelay> {
  const host = options.host ?? "0.0.0.0";
  const port = options.port ?? Number(process.env.PORT ?? 8787);
  const now = options.now ?? (() => Date.now());
  const limits = mergeLimits(options.limits);
  const ttlMs = options.ttlMs ?? PAIRING_TTL_MS;
  const hub = new PairingHub(ttlMs, now, {
    resumeGraceMs: options.resumeGraceMs,
    maxRoomMs: options.maxRoomMs,
  });
  const publicUrl = options.publicUrl ?? process.env.PONY_RELAY_URL;
  const pairLimiter = new WindowCounter(now);
  const connectLimiter = new WindowCounter(now);
  const socketsByIp = new Gauge();
  /** Encrypted `fwd.data` is ASCII; leave headroom for the JSON wrapper. */
  const maxFwdChars = Math.max(1024, limits.maxPayloadBytes - 64);

  const handleHttp = (req: http.IncomingMessage, res: http.ServerResponse): void => {
    const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "localhost"}`);

    if (contentLengthTooLarge(req, limits.maxHttpBodyBytes)) {
      json(res, 413, { error: "payload_too_large" });
      req.destroy();
      return;
    }
    capUnreadBody(req, limits.maxHttpBodyBytes);

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
      json(res, 200, { ok: true, rooms: hub.size(), pairingTtlMs: ttlMs, resume: true });
      return;
    }

    if (req.method === "POST" && url.pathname === "/pair") {
      const ip = clientIp(req);
      if (!pairLimiter.allow(ip, limits.maxPairPerIpPerWindow, limits.windowMs)) {
        json(res, 429, { error: "rate_limited" });
        return;
      }
      if (hub.size() >= limits.maxRooms) {
        json(res, 503, { error: "too_many_rooms" });
        return;
      }
      if (hub.countForIp(ip) >= limits.maxRoomsPerIp) {
        json(res, 429, { error: "too_many_rooms" });
        return;
      }
      const token = randomTokenHex();
      const created = hub.createToken(token, ip);
      json(res, 200, {
        token: created.token,
        expiresAt: created.expiresAt,
        expiresInMs: ttlMs,
        wsUrl: wsUrlFromRequest(req, publicUrl),
      });
      return;
    }

    if (req.method === "GET" && url.pathname === "/") {
      html(res, landingPage(hub.size()));
      return;
    }

    json(res, 404, { error: "not_found" });
  };

  const server = http.createServer((req, res) => {
    try {
      handleHttp(req, res);
    } catch (err) {
      console.error("[relay] http handler", err);
      try {
        if (!res.headersSent) json(res, 500, { error: "internal_error" });
        else res.destroy();
      } catch {
        /* response already gone */
      }
    }
  });

  server.on("clientError", (_err, socket) => {
    try {
      socket.end("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n");
    } catch {
      try {
        socket.destroy();
      } catch {
        /* ignore */
      }
    }
  });

  const wss = new WebSocketServer({
    server,
    path: "/ws",
    maxPayload: limits.maxPayloadBytes,
  });
  wss.on("error", (err) => {
    console.error("[relay] websocket server", err);
  });

  const meta = new WeakMap<WebSocket, ClientMeta>();
  const alive = new WeakMap<WebSocket, boolean>();
  const msgTimes = new WeakMap<WebSocket, number[]>();

  const heartbeat = setInterval(() => {
    try {
      pairLimiter.gc(limits.windowMs);
      connectLimiter.gc(limits.windowMs);
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
      for (const orphan of hub.gc()) {
        try {
          orphan.close(4000, "room_closed");
        } catch {
          /* peer already gone */
        }
      }
    } catch (err) {
      console.error("[relay] heartbeat", err);
    }
  }, options.heartbeatMs ?? 20_000);
  heartbeat.unref?.();

  const handleMessage = (socket: WebSocket, peer: PeerSocket, raw: unknown, isBinary: boolean): void => {
    alive.set(socket, true);
    const t = now();
    const recent = (msgTimes.get(socket) ?? []).filter((stamp) => t - stamp < 1000);
    if (recent.length >= limits.maxMessagesPerSocketPerSec) {
      rejectSocket(socket, "rate_limited", CLOSE_RATE);
      return;
    }
    recent.push(t);
    msgTimes.set(socket, recent);

    const frame = frameText(raw, isBinary);
    if (!frame.ok) {
      rejectSocket(socket, frame.reason);
      return;
    }
    if (frame.text.length === 0) {
      rejectSocket(socket, "bad_json");
      return;
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(frame.text);
    } catch {
      rejectSocket(socket, "bad_json");
      return;
    }

    const checked = validateInbound(parsed, maxFwdChars);
    if (!checked.ok) {
      rejectSocket(socket, checked.reason);
      return;
    }
    const msg = checked.msg;

    if (msg.type === "hello") {
      if (meta.has(socket)) {
        safeSend(socket, JSON.stringify({ type: "error", reason: "already_joined" }));
        return;
      }

      const joined = hub.join(msg.token, msg.role, peer, msg.resume === true);
      if ("error" in joined) {
        rejectSocket(socket, joined.error, 4000);
        return;
      }
      joined.replaced?.close(4002, "replaced");
      if (msg.role === "bot" && msg.client) joined.room.clientName = msg.client;
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
        safeSend(socket, JSON.stringify({ type: "waiting" }));
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
      safeClose(socket, 1000, "bye");
      return;
    }

    if (msg.type === "fwd") {
      const who = meta.get(socket);
      if (!who) {
        safeSend(socket, JSON.stringify({ type: "error", reason: "hello_required" }));
        return;
      }
      const dest = hub.peerOf(who.token, who.role);
      if (!dest) {
        safeSend(socket, JSON.stringify({ type: "error", reason: "peer_missing" }));
        return;
      }
      dest.send(JSON.stringify({ type: "fwd", data: msg.data }));
    }
  };

  function peerOf(socket: WebSocket): PeerSocket {
    return {
      send(data) {
        safeSend(socket, data);
      },
      close(code, reason) {
        if (code === undefined) safeClose(socket, 1000, "");
        else safeClose(socket, code, reason ?? "");
      },
    };
  }

  wss.on("connection", (socket, req) => {
    try {
      const ip = clientIp(req);
      if (
        !connectLimiter.allow(ip, limits.maxConnectPerIpPerWindow, limits.windowMs) ||
        socketsByIp.get(ip) >= limits.maxWsPerIp
      ) {
        rejectSocket(socket, "rate_limited", CLOSE_RATE);
        return;
      }
      socketsByIp.inc(ip);
      alive.set(socket, true);
      const peer = peerOf(socket);
      socket.on("error", (err) => {
        console.error("[relay] socket", err.message);
      });
      socket.on("pong", () => alive.set(socket, true));

      socket.on("message", (raw, isBinary) => {
        try {
          handleMessage(socket, peer, raw, isBinary);
        } catch (err) {
          console.error("[relay] message handler", err);
          rejectSocket(socket, "internal_error", 1011);
        }
      });

      socket.on("close", () => {
        try {
          socketsByIp.dec(ip);
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
        } catch (err) {
          console.error("[relay] close handler", err);
        }
      });
    } catch (err) {
      console.error("[relay] connection handler", err);
      rejectSocket(socket, "internal_error", 1011);
    }
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
        limits,
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
  const hostHeader = req.headers.host ?? "127.0.0.1:8787";
  const proto = req.headers["x-forwarded-proto"] === "https" ? "wss" : "ws";
  return `${proto}://${hostHeader}/ws`;
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
