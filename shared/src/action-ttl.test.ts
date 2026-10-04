import { describe, expect, it } from "vitest";

import {
  ACTION_TTL_DEFAULT_MS,
  OWNER_CLIENT_WAIT_MS,
  OWNER_PROMPT_MS,
  actionExpired,
  actionTtlMs,
  effectiveIssuedAt,
  screenChanged,
} from "./action-ttl.ts";

describe("action TTL", () => {
  it("gives screen taps a short life and confirm a wait longer than the phone prompt", () => {
    expect(actionTtlMs("tap")).toBe(ACTION_TTL_DEFAULT_MS);
    expect(actionTtlMs("confirm")).toBe(OWNER_CLIENT_WAIT_MS);
    expect(OWNER_CLIENT_WAIT_MS).toBeGreaterThan(OWNER_PROMPT_MS);
    expect(actionTtlMs("wait_for_request")).toBe(0);
  });

  it("expires a tap that sat in the queue for minutes", () => {
    const issued = 1_000;
    expect(actionExpired(issued, 15_000, issued + 316_000, 15_000)).toBe(true);
    expect(actionExpired(issued, 15_000, issued + 14_000, 15_000)).toBe(false);
  });

  it("does not expire a wait_for_request (ttl 0) or a command with no stamp", () => {
    expect(actionExpired(1_000, 0, 1_000_000, 0)).toBe(false);
    expect(actionExpired(undefined, 15_000, 50_000, 15_000)).toBe(false);
  });

  it("uses the receive time when the client stamp is missing, and rejects a skewed stamp", () => {
    expect(effectiveIssuedAt(undefined, 9_000)).toBe(9_000);
    expect(effectiveIssuedAt(8_500, 9_000)).toBe(8_500);
    expect(() => effectiveIssuedAt(9_000 + 200_000, 9_000)).toThrow("clock_skew");
    expect(() => effectiveIssuedAt(1_000, 201_000)).toThrow("clock_skew");
  });

  it("refuses a tap when the named screen is no longer in front", () => {
    expect(screenChanged("com.google.android.keep", "com.android.settings")).toBe(true);
    expect(screenChanged("com.android.settings", "com.android.settings")).toBe(false);
    expect(screenChanged(undefined, "com.android.settings")).toBe(false);
    expect(screenChanged("com.android.settings", undefined)).toBe(false);
  });
});
