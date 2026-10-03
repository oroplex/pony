import { chacha20poly1305 } from "@noble/ciphers/chacha.js";
import { x25519 } from "@noble/curves/ed25519.js";
import { hkdf } from "@noble/hashes/hkdf.js";
import { sha256 } from "@noble/hashes/sha2.js";
import { bytesToHex, hexToBytes } from "@noble/hashes/utils.js";

import { AEAD_AAD, AEAD_AAD_V2, HKDF_INFO, SAFETY_INFO } from "./protocol.ts";

const te = new TextEncoder();

export interface KeyPair {
  privateKey: Uint8Array;
  publicKey: Uint8Array;
}

export interface SessionKeys {
  send: Uint8Array;
  recv: Uint8Array;
  shared: Uint8Array;
  safetyCode: string;
}

export function generateKeyPair(): KeyPair {
  const privateKey = x25519.utils.randomPrivateKey();
  const publicKey = x25519.getPublicKey(privateKey);
  return { privateKey, publicKey };
}

export function publicKeyFromPrivate(privateKey: Uint8Array): Uint8Array {
  return x25519.getPublicKey(privateKey);
}

export function tokenBytes(tokenHex: string): Uint8Array {
  return hexToBytes(tokenHex);
}

export function randomTokenHex(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return bytesToHex(bytes);
}

export function b64urlEncode(bytes: Uint8Array): string {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

export function b64urlDecode(s: string): Uint8Array {
  const pad = "=".repeat((4 - (s.length % 4)) % 4);
  const b64 = s.replaceAll("-", "+").replaceAll("_", "/") + pad;
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function concatBytes(...parts: Uint8Array[]): Uint8Array {
  const len = parts.reduce((n, p) => n + p.length, 0);
  const out = new Uint8Array(len);
  let off = 0;
  for (const p of parts) {
    out.set(p, off);
    off += p.length;
  }
  return out;
}

export function deriveSessionKeys(
  privateKey: Uint8Array,
  peerPublicKey: Uint8Array,
  tokenHex: string,
  role: "bot" | "phone",
): SessionKeys {
  const shared = x25519.getSharedSecret(privateKey, peerPublicKey);
  const salt = tokenBytes(tokenHex);
  const okm = hkdf(sha256, shared, salt, te.encode(HKDF_INFO), 64);
  const phoneToBot = okm.slice(0, 32);
  const botToPhone = okm.slice(32, 64);
  const send = role === "phone" ? phoneToBot : botToPhone;
  const recv = role === "phone" ? botToPhone : phoneToBot;
  return { send, recv, shared, safetyCode: safetyCode(shared, salt) };
}

export function safetyCode(shared: Uint8Array, token: Uint8Array): string {
  const digest = sha256(concatBytes(shared, token, te.encode(SAFETY_INFO)));
  const n = ((digest[0] << 16) | (digest[1] << 8) | digest[2]) % 1_000_000;
  return n.toString().padStart(6, "0");
}

export function formatSafetyCode(code: string): string {
  return `${code.slice(0, 3)}-${code.slice(3)}`;
}

const AAD_V1 = te.encode(AEAD_AAD);
const AAD_V2_PREFIX = te.encode(AEAD_AAD_V2);

/** AAD for a frame. Protocol v1 is the constant `pony-v1`. v2 binds the per-direction seq. */
export function aeadAad(seq?: number): Uint8Array {
  if (seq == null) return AAD_V1;
  const out = new Uint8Array(AAD_V2_PREFIX.length + 8);
  out.set(AAD_V2_PREFIX);
  writeUint64BE(out, AAD_V2_PREFIX.length, seq);
  return out;
}

function writeUint64BE(out: Uint8Array, offset: number, value: number): void {
  let n = BigInt(value);
  for (let i = 7; i >= 0; i--) {
    out[offset + i] = Number(n & 0xffn);
    n >>= 8n;
  }
}

function readUint64BE(bytes: Uint8Array, offset: number): number {
  let n = 0n;
  for (let i = 0; i < 8; i++) n = (n << 8n) | BigInt(bytes[offset + i]!);
  return Number(n);
}

/** Tink-compatible: 12-byte random nonce || ciphertext || 16-byte tag. v2 prefixes an 8-byte seq. */
export function encrypt(key: Uint8Array, plaintext: Uint8Array, seq?: number): Uint8Array {
  const nonce = new Uint8Array(12);
  crypto.getRandomValues(nonce);
  return encryptWithNonce(key, nonce, plaintext, seq);
}

export function encryptWithNonce(key: Uint8Array, nonce: Uint8Array, plaintext: Uint8Array, seq?: number): Uint8Array {
  if (nonce.length !== 12) throw new Error("nonce must be 12 bytes");
  const cipher = chacha20poly1305(key, nonce, aeadAad(seq));
  const blob = concatBytes(nonce, cipher.encrypt(plaintext));
  if (seq == null) return blob;
  const prefix = new Uint8Array(8);
  writeUint64BE(prefix, 0, seq);
  return concatBytes(prefix, blob);
}

export function decrypt(key: Uint8Array, frame: Uint8Array, seq?: number): Uint8Array {
  if (seq != null) {
    if (frame.length < 8 + 12 + 16) throw new Error("ciphertext too short");
    const framed = readUint64BE(frame, 0);
    if (framed !== seq) throw new Error("seq mismatch");
    return decryptBlob(key, frame.slice(8), seq);
  }
  return decryptBlob(key, frame);
}

function decryptBlob(key: Uint8Array, blob: Uint8Array, seq?: number): Uint8Array {
  if (blob.length < 12 + 16) throw new Error("ciphertext too short");
  const nonce = blob.slice(0, 12);
  const ct = blob.slice(12);
  const cipher = chacha20poly1305(key, nonce, aeadAad(seq));
  return cipher.decrypt(ct);
}

/**
 * Decrypt a frame when the receiver does not yet know the seq. v2 frames start
 * with an 8-byte seq prefix; v1 frames are the raw Tink blob.
 */
export function decryptFrame(key: Uint8Array, frame: Uint8Array, protocolVersion: number): { plaintext: Uint8Array; seq?: number } {
  if (protocolVersion >= 2) {
    if (frame.length < 8 + 12 + 16) throw new Error("ciphertext too short");
    const seq = readUint64BE(frame, 0);
    return { plaintext: decryptBlob(key, frame.slice(8), seq), seq };
  }
  return { plaintext: decryptBlob(key, frame) };
}

export function encryptJson(key: Uint8Array, value: unknown, seq?: number): string {
  const bytes = te.encode(JSON.stringify(value));
  return b64urlEncode(encrypt(key, bytes, seq));
}

export function decryptJson<T>(key: Uint8Array, data: string, seq?: number): T {
  const plain = decrypt(key, b64urlDecode(data), seq);
  return JSON.parse(new TextDecoder().decode(plain)) as T;
}

export function decryptJsonFrame<T>(key: Uint8Array, data: string, protocolVersion: number): { value: T; seq?: number } {
  const { plaintext, seq } = decryptFrame(key, b64urlDecode(data), protocolVersion);
  return { value: JSON.parse(new TextDecoder().decode(plaintext)) as T, seq };
}

export { bytesToHex, hexToBytes };
