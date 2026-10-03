import type { RelayInbound, Role } from "@pony/shared";

const TOKEN_RE = /^[0-9a-f]{64}$/i;
const MAX_CLIENT_CHARS = 40;

export type ValidateOk = { ok: true; msg: RelayInbound };
export type ValidateErr = { ok: false; reason: string };

/**
 * Accepts only the 0.6.x relay frames: hello / fwd / bye.
 * Extra fields are ignored. Unknown or non-object payloads are errors.
 */
export function validateInbound(value: unknown, maxFwdChars: number): ValidateOk | ValidateErr {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return { ok: false, reason: "bad_payload" };
  }
  const rec = value as Record<string, unknown>;
  if (rec.type === "hello") return validateHello(rec);
  if (rec.type === "fwd") return validateFwd(rec, maxFwdChars);
  if (rec.type === "bye") return { ok: true, msg: { type: "bye" } };
  return { ok: false, reason: "unknown_type" };
}

function validateHello(rec: Record<string, unknown>): ValidateOk | ValidateErr {
  if (rec.role !== "bot" && rec.role !== "phone") {
    return { ok: false, reason: "bad_role" };
  }
  if (typeof rec.token !== "string" || !TOKEN_RE.test(rec.token)) {
    return { ok: false, reason: "bad_token" };
  }
  const role = rec.role as Role;
  const msg: RelayInbound = { type: "hello", role, token: rec.token };
  if (typeof rec.client === "string") {
    const client = rec.client.trim().slice(0, MAX_CLIENT_CHARS);
    if (client) msg.client = client;
  }
  if (rec.resume === true) msg.resume = true;
  return { ok: true, msg };
}

function validateFwd(rec: Record<string, unknown>, maxFwdChars: number): ValidateOk | ValidateErr {
  if (typeof rec.data !== "string") return { ok: false, reason: "bad_payload" };
  if (rec.data.length > maxFwdChars) return { ok: false, reason: "payload_too_large" };
  return { ok: true, msg: { type: "fwd", data: rec.data } };
}

export function frameText(raw: unknown, isBinary: boolean): { ok: true; text: string } | ValidateErr {
  if (isBinary) return { ok: false, reason: "bad_payload" };
  if (typeof raw === "string") return { ok: true, text: raw };
  if (Buffer.isBuffer(raw)) return { ok: true, text: raw.toString("utf8") };
  if (Array.isArray(raw)) {
    const parts = raw.map((part) => (Buffer.isBuffer(part) ? part : Buffer.from(part)));
    return { ok: true, text: Buffer.concat(parts).toString("utf8") };
  }
  if (raw instanceof ArrayBuffer) return { ok: true, text: Buffer.from(raw).toString("utf8") };
  return { ok: false, reason: "bad_payload" };
}
