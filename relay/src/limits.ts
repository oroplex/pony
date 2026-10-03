import type { IncomingMessage } from "node:http";

/** Limits that keep a public relay from being knocked over by one IP. */
export interface RelayLimits {
  /** WebSocket frame cap. Screenshots travel as encrypted `fwd` data; 16 MiB leaves room for a 4K JPEG. */
  maxPayloadBytes: number;
  /** Unread HTTP body cap. `POST /pair` does not use a body. */
  maxHttpBodyBytes: number;
  /** All rooms, pairing and established. */
  maxRooms: number;
  /** Rooms created by one client IP. */
  maxRoomsPerIp: number;
  /** `POST /pair` calls per IP per [windowMs]. */
  maxPairPerIpPerWindow: number;
  /** Concurrent WebSocket connections per IP. */
  maxWsPerIp: number;
  /** New WebSocket handshakes per IP per [windowMs]. */
  maxConnectPerIpPerWindow: number;
  /** Inbound frames per socket per second. */
  maxMessagesPerSocketPerSec: number;
  /** Sliding window for pair and connect counters. */
  windowMs: number;
}

export const DEFAULT_LIMITS: RelayLimits = {
  maxPayloadBytes: 16 * 1024 * 1024,
  maxHttpBodyBytes: 64 * 1024,
  maxRooms: 10_000,
  maxRoomsPerIp: 50,
  maxPairPerIpPerWindow: 30,
  maxWsPerIp: 40,
  maxConnectPerIpPerWindow: 60,
  maxMessagesPerSocketPerSec: 60,
  windowMs: 60_000,
};

export function mergeLimits(partial?: Partial<RelayLimits>): RelayLimits {
  return { ...DEFAULT_LIMITS, ...partial };
}

function envInt(name: string): number | undefined {
  const raw = process.env[name];
  if (raw === undefined || raw === "") return undefined;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

/** Optional env overrides for a self-hosted relay. Unset keys keep the defaults. */
export function limitsFromEnv(): Partial<RelayLimits> {
  return {
    maxPayloadBytes: envInt("PONY_MAX_PAYLOAD_BYTES"),
    maxHttpBodyBytes: envInt("PONY_MAX_HTTP_BODY_BYTES"),
    maxRooms: envInt("PONY_MAX_ROOMS"),
    maxRoomsPerIp: envInt("PONY_MAX_ROOMS_PER_IP"),
    maxPairPerIpPerWindow: envInt("PONY_MAX_PAIR_PER_IP"),
    maxWsPerIp: envInt("PONY_MAX_WS_PER_IP"),
    maxConnectPerIpPerWindow: envInt("PONY_MAX_CONNECT_PER_IP"),
    maxMessagesPerSocketPerSec: envInt("PONY_MAX_MESSAGES_PER_SEC"),
    windowMs: envInt("PONY_LIMIT_WINDOW_MS"),
  };
}

export function clientIp(req: IncomingMessage): string {
  const forwarded = req.headers["x-forwarded-for"];
  const raw =
    typeof forwarded === "string" && forwarded.trim()
      ? forwarded.split(",")[0]!.trim()
      : (req.socket.remoteAddress ?? "unknown");
  return raw.startsWith("::ffff:") ? raw.slice("::ffff:".length) : raw;
}

/** Sliding-window counter. Used for pair and connect floods. */
export class WindowCounter {
  private readonly hits = new Map<string, number[]>();

  constructor(private readonly now: () => number = () => Date.now()) {}

  allow(key: string, max: number, windowMs: number): boolean {
    const t = this.now();
    const recent = (this.hits.get(key) ?? []).filter((stamp) => t - stamp < windowMs);
    if (recent.length >= max) {
      this.hits.set(key, recent);
      return false;
    }
    recent.push(t);
    this.hits.set(key, recent);
    return true;
  }

  gc(windowMs: number): void {
    const t = this.now();
    for (const [key, times] of this.hits) {
      const recent = times.filter((stamp) => t - stamp < windowMs);
      if (recent.length === 0) this.hits.delete(key);
      else this.hits.set(key, recent);
    }
  }
}

/** Concurrent-connection gauge keyed by IP. */
export class Gauge {
  private readonly counts = new Map<string, number>();

  inc(key: string): number {
    const next = (this.counts.get(key) ?? 0) + 1;
    this.counts.set(key, next);
    return next;
  }

  dec(key: string): void {
    const next = (this.counts.get(key) ?? 0) - 1;
    if (next <= 0) this.counts.delete(key);
    else this.counts.set(key, next);
  }

  get(key: string): number {
    return this.counts.get(key) ?? 0;
  }
}
