/** Per-direction AEAD counters and executed-id denylist. */

const MAX_EXECUTED = 4_096;

/** Monotonic send/receive counters. Rejects reused or rewound seq values. */
export class DirectionCounter {
  private send = 0;
  private recv = 0;

  nextSend(): number {
    this.send += 1;
    return this.send;
  }

  /** True when [seq] is strictly greater than every seq already accepted. */
  accept(seq: number | undefined | null): boolean {
    if (seq == null || !Number.isSafeInteger(seq) || seq <= 0) return false;
    if (seq <= this.recv) return false;
    this.recv = seq;
    return true;
  }

  lastReceived(): number {
    return this.recv;
  }
}

/** Remembers command ids that already ran so a replayed frame cannot run again. */
export class ExecutedIds {
  private readonly ids = new Set<string>();
  private readonly order: string[] = [];

  /** True if this id is new and has now been recorded. */
  remember(id: string): boolean {
    if (!id || this.ids.has(id)) return false;
    this.ids.add(id);
    this.order.push(id);
    while (this.order.length > MAX_EXECUTED) {
      const oldest = this.order.shift();
      if (oldest) this.ids.delete(oldest);
    }
    return true;
  }

  has(id: string): boolean {
    return this.ids.has(id);
  }
}
