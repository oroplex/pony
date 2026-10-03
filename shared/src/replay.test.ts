import { describe, expect, it } from "vitest";

import { DirectionCounter, ExecutedIds } from "./replay.ts";

describe("DirectionCounter", () => {
  it("issues strictly increasing send seqs and accepts them in order", () => {
    const send = new DirectionCounter();
    const recv = new DirectionCounter();
    expect(send.nextSend()).toBe(1);
    expect(send.nextSend()).toBe(2);
    expect(recv.accept(1)).toBe(true);
    expect(recv.accept(2)).toBe(true);
    expect(recv.lastReceived()).toBe(2);
  });

  it("restores counters so a restarted peer does not rewind", () => {
    const live = new DirectionCounter();
    live.nextSend();
    live.nextSend();
    live.accept(4);
    const copy = new DirectionCounter();
    copy.restore(live.snapshot().send, live.snapshot().recv);
    expect(copy.nextSend()).toBe(3);
    expect(copy.accept(4)).toBe(false);
    expect(copy.accept(5)).toBe(true);
  });

  it("rejects a reused or rewound seq", () => {
    const recv = new DirectionCounter();
    expect(recv.accept(3)).toBe(true);
    expect(recv.accept(3)).toBe(false);
    expect(recv.accept(2)).toBe(false);
    expect(recv.accept(0)).toBe(false);
    expect(recv.accept(undefined)).toBe(false);
    expect(recv.accept(4)).toBe(true);
  });
});

describe("ExecutedIds", () => {
  it("rejects a command id that already ran", () => {
    const seen = new ExecutedIds();
    expect(seen.remember("a")).toBe(true);
    expect(seen.remember("a")).toBe(false);
    expect(seen.has("a")).toBe(true);
    expect(seen.remember("b")).toBe(true);
  });
});
