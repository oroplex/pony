import { describe, expect, it } from "vitest";

import { frameText, validateInbound } from "./validate.ts";

describe("validateInbound", () => {
  it("accepts the 0.6.x hello / fwd / bye frames", () => {
    const token = "ab".repeat(32);
    expect(validateInbound({ type: "hello", role: "bot", token, client: "Grok Bot", resume: true }, 100)).toEqual({
      ok: true,
      msg: { type: "hello", role: "bot", token, client: "Grok Bot", resume: true },
    });
    expect(validateInbound({ type: "hello", role: "phone", token }, 100)).toEqual({
      ok: true,
      msg: { type: "hello", role: "phone", token },
    });
    expect(validateInbound({ type: "fwd", data: "opaque" }, 100)).toEqual({
      ok: true,
      msg: { type: "fwd", data: "opaque" },
    });
    expect(validateInbound({ type: "bye" }, 100)).toEqual({ ok: true, msg: { type: "bye" } });
  });

  it("rejects empty, null, non-objects, and malformed pairing", () => {
    expect(validateInbound(null, 100)).toEqual({ ok: false, reason: "bad_payload" });
    expect(validateInbound(undefined, 100)).toEqual({ ok: false, reason: "bad_payload" });
    expect(validateInbound([], 100)).toEqual({ ok: false, reason: "bad_payload" });
    expect(validateInbound(1, 100)).toEqual({ ok: false, reason: "bad_payload" });
    expect(validateInbound("hello", 100)).toEqual({ ok: false, reason: "bad_payload" });
    expect(validateInbound({}, 100)).toEqual({ ok: false, reason: "unknown_type" });
    expect(validateInbound({ type: "hello" }, 100)).toEqual({ ok: false, reason: "bad_role" });
    expect(validateInbound({ type: "hello", role: "bot", token: "nope" }, 100)).toEqual({
      ok: false,
      reason: "bad_token",
    });
    expect(validateInbound({ type: "hello", role: "modem", token: "ab".repeat(32) }, 100)).toEqual({
      ok: false,
      reason: "bad_role",
    });
    expect(validateInbound({ type: "fwd", data: 1 }, 100)).toEqual({ ok: false, reason: "bad_payload" });
    expect(validateInbound({ type: "fwd", data: "toolong" }, 3)).toEqual({
      ok: false,
      reason: "payload_too_large",
    });
    expect(validateInbound({ type: "explode" }, 100)).toEqual({ ok: false, reason: "unknown_type" });
  });

  it("trims the bot client name the same way as before", () => {
    const token = "cd".repeat(32);
    const long = `  ${"n".repeat(80)}  `;
    const result = validateInbound({ type: "hello", role: "bot", token, client: long }, 100);
    expect(result).toMatchObject({ ok: true, msg: { client: "n".repeat(40) } });
  });
});

describe("frameText", () => {
  it("rejects binary frames and accepts utf8 text", () => {
    expect(frameText(Buffer.from("hi"), true)).toEqual({ ok: false, reason: "bad_payload" });
    expect(frameText(Buffer.from("hi"), false)).toEqual({ ok: true, text: "hi" });
    expect(frameText("", false)).toEqual({ ok: true, text: "" });
  });
});
