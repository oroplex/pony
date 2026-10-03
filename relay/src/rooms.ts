import type { Role } from "@pony/shared";
import { PAIRING_TTL_MS } from "@pony/shared";

export interface PeerSocket {
  send(data: string): void;
  close(code?: number, reason?: string): void;
}

export interface Room {
  token: string;
  createdAt: number;
  /** When both peers were first present. After that the pairing TTL no longer applies. */
  establishedAt?: number;
  /** Last time at least one peer was connected. */
  lastSeenAt: number;
  bot?: PeerSocket;
  phone?: PeerSocket;
  /** Peers that said they can rejoin. A room outlives a drop only when both can. */
  resume: { bot: boolean; phone: boolean };
  /** Set from the bot hello. Omitted on the wire when empty. */
  clientName?: string;
  /** IP that created the pairing token via POST /pair. Used for per-IP caps. */
  createdByIp?: string;
}

export type RoomError =
  | "unknown_token"
  | "expired_token"
  | "role_taken"
  | "room_closed";

export interface HubOptions {
  /** How long an established room waits with nobody in it. */
  resumeGraceMs?: number;
  /** Hard cap on an established room's life. */
  maxRoomMs?: number;
}

export interface Joined {
  room: Room;
  /** True when this join rejoined an established room. */
  resumed: boolean;
  /** A stale socket this join replaced. The caller closes it. */
  replaced?: PeerSocket;
}

export interface Left {
  room: Room;
  /** The room is being held for the peer that left. */
  away: boolean;
  /** The peer still connected, if any. */
  peer?: PeerSocket;
}

export const RESUME_GRACE_MS = 10 * 60 * 1000;
export const MAX_ROOM_MS = 24 * 60 * 60 * 1000;

export class PairingHub {
  private readonly rooms = new Map<string, Room>();
  private readonly resumeGraceMs: number;
  private readonly maxRoomMs: number;

  constructor(
    private readonly ttlMs = PAIRING_TTL_MS,
    private readonly now: () => number = () => Date.now(),
    options: HubOptions = {},
  ) {
    this.resumeGraceMs = options.resumeGraceMs ?? RESUME_GRACE_MS;
    this.maxRoomMs = options.maxRoomMs ?? MAX_ROOM_MS;
  }

  createToken(token: string, createdByIp?: string): { token: string; expiresAt: number } {
    this.gc();
    const createdAt = this.now();
    this.rooms.set(token, {
      token,
      createdAt,
      lastSeenAt: createdAt,
      resume: { bot: false, phone: false },
      createdByIp,
    });
    return { token, expiresAt: createdAt + this.ttlMs };
  }

  /** Rooms still on the books that this IP created. Call after gc(). */
  countForIp(ip: string): number {
    if (!ip) return 0;
    let n = 0;
    for (const room of this.rooms.values()) {
      if (room.createdByIp === ip) n++;
    }
    return n;
  }

  join(token: string, role: Role, socket: PeerSocket, resume = false): Joined | { error: RoomError } {
    const room = this.rooms.get(token);
    if (!room) {
      this.gc();
      return { error: "unknown_token" };
    }
    const t = this.now();
    if (room.establishedAt === undefined && t - room.createdAt > this.ttlMs) {
      this.drop(token);
      return { error: "expired_token" };
    }
    if (room.establishedAt !== undefined && t - room.establishedAt > this.maxRoomMs) {
      this.drop(token);
      return { error: "room_closed" };
    }
    let replaced: PeerSocket | undefined;
    if (room[role]) {
      if (!resume || room.establishedAt === undefined) return { error: "role_taken" };
      replaced = room[role];
    }
    const resumed = room.establishedAt !== undefined;
    room[role] = socket;
    room.resume[role] = resume;
    room.lastSeenAt = t;
    if (room.establishedAt === undefined && room.bot && room.phone) room.establishedAt = t;
    return { room, resumed, replaced };
  }

  leave(token: string, role: Role, socket: PeerSocket): Left | undefined {
    const room = this.rooms.get(token);
    if (!room || room[role] !== socket) return undefined;
    room[role] = undefined;
    room.lastSeenAt = this.now();
    const peer = role === "bot" ? room.phone : room.bot;
    const other: Role = role === "bot" ? "phone" : "bot";
    const holdable = room.establishedAt !== undefined && room.resume[role] && (peer === undefined || room.resume[other]);
    if (holdable) return { room, away: true, peer };
    this.rooms.delete(token);
    return peer ? { room, away: false, peer } : undefined;
  }

  /** An explicit goodbye ends the room for both sides. Returns whoever is still connected. */
  end(token: string, role: Role): PeerSocket | undefined {
    const room = this.rooms.get(token);
    if (!room) return undefined;
    this.rooms.delete(token);
    return role === "bot" ? room.phone : room.bot;
  }

  peerOf(token: string, role: Role): PeerSocket | undefined {
    const room = this.rooms.get(token);
    if (!room) return undefined;
    return role === "bot" ? room.phone : room.bot;
  }

  bothPresent(room: Room): boolean {
    return Boolean(room.bot && room.phone);
  }

  size(): number {
    this.gc();
    return this.rooms.size;
  }

  drop(token: string): void {
    this.rooms.delete(token);
  }

  /** Removes rooms nobody can use anymore. Returns sockets whose room hit its hard cap. */
  gc(): PeerSocket[] {
    const t = this.now();
    const orphaned: PeerSocket[] = [];
    for (const [token, room] of this.rooms) {
      const empty = !room.bot && !room.phone;
      if (room.establishedAt === undefined) {
        if (empty && t - room.createdAt > this.ttlMs) this.rooms.delete(token);
        continue;
      }
      if (t - room.establishedAt > this.maxRoomMs) {
        if (room.bot) orphaned.push(room.bot);
        if (room.phone) orphaned.push(room.phone);
        this.rooms.delete(token);
        continue;
      }
      if (empty && t - room.lastSeenAt > this.resumeGraceMs) this.rooms.delete(token);
    }
    return orphaned;
  }
}
