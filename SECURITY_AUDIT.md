# Pony security audit

**Scope:** public repository `oroplex/pony` at `8647289` (0.6.4 / versionCode 11), plus the full git history (18 commits).  
**Date:** 2026-10-03  
**Method:** read-only review. Secret scan of every commit with gitleaks v8.24.2, trufflehog, and manual `git grep` across `git rev-list --all`. Protocol and Android review from source. `npm audit` on the workspace lockfile. Gradle CVEs checked against public advisory databases for the pinned Android libraries.  
**No production credentials were exercised.** This report does not prove whether a live pairing token or API key still works; it says whether a finding *looks* like a real secret.

This PR contains only this file. It is not a code fix.

---

## Plain-English summary (for a non-programmer owner)

Pony is built so a stranger on the internet **cannot guess their way onto your phone**. The pairing code is a huge random number (256 bits). Guessing it is not realistic. Screenshots and taps are encrypted between your phone and your assistant. The public relay in the middle can see that two devices are talking, and can delay or drop messages, but it **cannot read the screen or the commands** if both sides have the right keys.

The real risks are not “a hacker types random codes until they get in.” They are:

1. **Someone tricks you into opening *their* pairing link.** A link that looks official (`download.pony.karlmagendavid.com/pair…`) can pair your phone to an attacker’s assistant. The six-digit safety code will match *their* computer, not yours, because you really did pair with them. After you tap “Share entire screen,” they can already drive the phone. The “It matches” button only closes the screen; it does not lock the session.

2. **The “ask me first” safety net has holes.** Pony is supposed to ask before sending, paying, buying, deleting, or calling. The agent **cannot tap its own Yes button** — that part is done carefully. But it can still send a message by typing and pressing Send/Enter, or by tapping an unlabeled send icon in many chat apps. Those paths do not ask you.

3. **A pairing link is a secret.** If it is pasted into a chat, a ticket, or a browser history, anyone who sees it in the next 15 minutes can race to join as the phone, or pair *you* to *them* if you open it.

4. **No live secrets were found in the repo.** There is no keystore, no Cloudflare/Hostinger/GitHub token, no real API key, and no `.env` with passwords. One personal Gmail address appears on two GitHub merge commits. Test files use fake keys like `sk-ant-PLAINTEXT-secret-1111` on purpose.

**What you should do first:** treat pairing links like house keys; never open one you did not just ask your own assistant to create; keep sessions short; do not leave “Until I disconnect” on a shared or lost phone; rotate the Gmail on git commits if you care about privacy; fix the confirmation holes and make “It matches” actually required before the assistant can act.

---

## How to read the ratings

| Rating | Meaning |
| --- | --- |
| **Critical** | Remote control of a phone, or a live secret that still works, without the owner meaning to allow it |
| **High** | A realistic path to phone control or data theft if the owner is phished or a pairing link leaks |
| **Medium** | Real weakness that needs a concrete change; exploit needs extra conditions |
| **Low** | Defense-in-depth, hygiene, or a dependency issue that does not look reachable |

---

## 1. Secret scan (full git history)

**Tools:** gitleaks 8.24.2 (`gitleaks detect --log-opts='--all'`), trufflehog on `file:///workspace`, and manual greps for PEM/SSH keys, `ghp_` / `github_pat_` / `sk-ant-` / `sk-live-` / `AKIA` / `AIza` / Cloudflare / Hostinger, emails, phones, RFC1918 / Tailscale IPs, and added `.env` / keystore / `.pem` / `.jks` files.

**History size:** 18 commits, ~1.8 MB scanned. No `.env`, `.pem`, `.key`, `.jks`, `.keystore`, `.p12`, `id_rsa`, or `google-services.json` was ever committed. `.gitignore` already blocks those.

### Findings

| ID | What | Commit | Path | Still valid-looking? |
| --- | --- | --- | --- | --- |
| S1 | gitleaks `generic-api-key`: `sk-abcdef0123456789xyz` | `5b4c250` (and every later commit; file unchanged) | `android/app/src/test/java/app/pony/companion/tasks/CarryBufferTest.kt:85` | **No.** Unit-test bait for `MemoryGuard`. Not a live key. |
| S2 | Test Anthropic/OpenAI-shaped strings `sk-ant-PLAINTEXT-secret-1111/2222/3333`, `sk-proj-PLAINTEXT-secret-3333` | `5b4c250` onward | `android/app/src/test/java/app/pony/companion/brain/BrainLibraryTest.kt` | **No.** Literal `PLAINTEXT-secret` placeholders. |
| S3 | Test string `sk-live-SHOULD-NOT-LOG` | `5b4c250` onward | `packages/mcp/src/mcp.test.ts:286` | **No.** Asserts the action log stores a character count, not the text. |
| S4 | Prefix list `sk-`, `ghp_`, `xoxb-`, … | all commits | `android/app/src/main/java/app/pony/companion/memory/MemoryGuard.kt:20` | **No.** Detector patterns, not secrets. |
| S5 | Personal email `unreal36@gmail.com` in **git author metadata** (not a source file) | `cb7f008` Merge PR #1; `5f1df1b` Merge PR #2 | commit headers only | **Yes, as an identity.** Public on GitHub merge commits. Not a password or API token. |
| S6 | GitHub noreply `oroplex@users.noreply.github.com` and `244263670+oroplex@users.noreply.github.com` | most commits | commit headers / LICENSE copyright “Karl Magen David” | Expected for a public GitHub identity. |
| S7 | Product hostnames `pony.karlmagendavid.com`, `relay.pony.karlmagendavid.com`, `download.pony.karlmagendavid.com` | throughout | README, protocol defaults, Android update/pairing | Public product names, not credentials. |
| S8 | Example / test private IPs (`192.168.1.20`, `10.0.0.5`, `100.64.8.8`, `172.16.0.9`, `10.0.2.2`) | throughout | tests and `CleartextPolicy` docs | **Not personal hosts.** Policy examples and emulator `10.0.2.2`. |
| S9 | Fictional address `742 Evergreen Terrace` | `5b4c250` onward | `CarryBufferTest.kt` | The Simpsons. Not PII. |

**Not found anywhere in history:** Cloudflare tokens, Hostinger tokens, GitHub PATs, SSH private keys, Android keystores or keystore passwords, real `.env` files, phone numbers, or PEM material.

**Trufflehog:** zero findings.  
**gitleaks:** one finding (S1), false positive.

**Fix for S5 (Low):** set `git config user.email` to a `users.noreply.github.com` address and, if you want the old address gone from GitHub’s UI, use GitHub’s email-privacy settings. Rewriting already-pushed merge commits is optional and painful; the address is already public.

---

## 2. Threat model: pairing and relay

### What the protocol actually does

1. The assistant `POST`s `/pair` on the relay. The relay mints a **32-byte hex token** (`crypto.getRandomValues`) and a room that lasts **15 minutes** until both sides join (`PAIRING_TTL_MS`).
2. The assistant generates an **X25519** key pair and puts `{v, relay, token, pk}` in a QR / `pony://pair` / `https://…/pair` link.
3. The phone opens the link, generates its own X25519 key, **derives session keys immediately** from `(phone_priv, bot_pk_from_link, token)` via HKDF-SHA256 (`info = pony-companion-v1`), then connects to the relay.
4. After the relay says both peers are present, the phone sends a **plaintext** `{type:"hs", pk}` frame so the bot can derive the matching keys. If a later handshake shows a different phone key, the bot records `peer_key_changed` and does not switch keys.
5. Application messages are **ChaCha20-Poly1305** with AAD `pony-v1`, Tink-compatible `nonce || ciphertext || tag`. Send/receive keys are directional (phone→bot and bot→phone).
6. The six-digit **safety code** is `SHA-256(shared || token || "pony-safety")` modulo 1,000,000.

This is real end-to-end encryption, not TLS-to-the-relay-and-hope. The relay only matches rooms and forwards opaque `fwd` strings.

### Can someone without the pairing link control a phone?

**No, not by guessing.** 256 bits of CSPRNG token. The relay refuses `unknown_token` unless `POST /pair` created that room. There is no short PIN.

They *can* control a phone if they get the owner to **open their link** (see H1) or if they obtain a live link and win the join race (H2).

### Brute-force pairing tokens?

| Property | Value | Verdict |
| --- | --- | --- |
| Entropy | 256 bits | Not brute-forceable |
| Unpaired TTL | 15 minutes | Fine |
| Established room lifetime | 24 hours (`MAX_ROOM_MS`) | Long window if the token later leaks |
| Empty-room resume grace | 10 minutes | Fine |
| Rate limit on `POST /pair` | **None** | DoS / room exhaustion (M3), not token guessing |
| Rate limit on `hello` | **None** | Irrelevant against 256-bit tokens |
| Health leak | `GET /health` returns `{rooms}` | Low information |

### Hijack or resume a session?

- Both sides send `resume: true` by default (`RelayClient`, `PonySession`).
- After the room is established, a second `hello` with `resume: true` **replaces** that role’s socket (`role_taken` only before establish or without resume).
- Replacing the socket does **not** give the attacker the X25519 private keys. They receive ciphertext they cannot decrypt. The displaced peer is closed with `replaced`.
- Saved sessions (`~/.pony/mcp.json`, phone `SessionVault`) store the private key so a restart can rejoin **the same keys**. An attacker who steals that file (MCP) or an unlocked phone backup of the vault can resume for real.

So: token-only resume is a **denial-of-service / kick-off**, not a decrypt. Key-file theft is a full hijack (H4, M4).

### Is E2E real? Can the relay read or modify traffic?

| Question | Answer |
| --- | --- |
| Where are keys derived? | Independently on phone and bot: X25519 shared secret, HKDF-SHA256, token as salt. |
| Can the relay decrypt screenshots or commands? | **No**, if both ends used the keys from the pairing payload / handshake. |
| Can the relay modify a frame? | Tampering fails Poly1305. The receiver drops it (`decrypt` throws / Android returns). |
| Can the relay swap the phone’s handshake `pk`? | Bot would derive the wrong keys. Encrypted traffic would not decrypt. Safety codes would disagree **if anyone compared them**. |
| Replay? | **Yes, within the action TTL** (usually 15s, type 45s). There is no message counter or nonce denylist. `issuedAt` + `ttlMs` is the only replay brake (M1). |
| Metadata the relay sees | Token, role, optional client name (max 40 chars), frame sizes and timing, room count. |

TLS to `relay.pony.karlmagendavid.com` is **not** pinned. A network attacker who can mint a trusted cert (or the owner who installed a user CA) can see the same metadata, still not the AEAD payload.

### Is the https pairing deep link safe from phishing?

**No.** This is the highest-impact protocol issue.

- App Links: `MainActivity` is exported and handles `https://download.pony.karlmagendavid.com/pair` (`autoVerify=true`) and `pony://pair`.
- `pairPageLink()` puts `v, relay, token, pk` in the **query string** of `https://download.pony.karlmagendavid.com/pair.html?…` (`shared/src/protocol.ts`). Those values hit CDN/server logs and can leak via `Referer`.
- `PairingLinks.isHttpPairLink` accepts **any host** whose path is `/pair` or `/pair.html`. `CleartextPolicy.allows` accepts **any `https`/`wss` relay**.
- `MainActivity.beginPairing` does **not** wait for “It matches.” If consent + accessibility are on, it immediately shows the system screen-capture sheet. On grant, `PonySessionService.start` runs. `Pair.kt`’s “It matches” only calls `nav.pop()`.
- A phishing link that the attacker created will show a safety code that **matches the attacker’s bot**. Comparing codes does not detect “I paired with the wrong person.” It only detects an active cryptographic MITM on a link you already intended to use.

A malicious app on the same phone can also `startActivity` a `pony://pair?…` VIEW intent and spring the same capture sheet (M6).

---

## 3. Android app

### Exported components

| Component | Exported | Permission / filter | Risk |
| --- | --- | --- | --- |
| `MainActivity` | yes | LAUNCHER + `pony://pair` + https App Link | Intended. Any app can deliver a pairing URI (M6). `EXTRA_REQUEST_MIC` triggers a mic permission prompt. |
| `PonyAccessibilityService` | yes | `BIND_ACCESSIBILITY_SERVICE` | System-only bind. Correct. |
| `PonyInputMethodService` | yes | `BIND_INPUT_METHOD` | System-only. Refuses password fields. |
| `PonyVoiceInteractionService` / `PonyVoiceSessionService` / `PonyRecognitionService` | yes | `BIND_VOICE_INTERACTION` / `BIND_RECOGNITION_SERVICE` | System-only. |
| `VoiceTile` | yes | `BIND_QUICK_SETTINGS_TILE` | System-only. |
| `ShizukuProvider` | yes | `INTERACT_ACROSS_USERS_FULL` | Stock Shizuku client pattern. |
| `PonySessionService`, `WakeWordService` | **no** | FGS types mediaProjection / microphone | Good. |
| `BackgroundStopReceiver`, widgets, boot/update receivers | **no** | — | Good. |
| `VoiceWakeActivity`, `AskLaunchActivity` | **no** | show-when-locked | Good. |

`allowBackup="false"`, `usesCleartextTraffic="false"`, network security config denies cleartext except localhost / `*.ts.net` / synthetic `.invalid` aliases that `CleartextPolicy` maps to Tailscale/LAN IPs.

No `WebView` exists in the project.

### Accessibility abuse

Accessibility is the product: read the tree, screenshot, gesture, type. That is full phone control **after the owner turns the service on and grants screen capture for the session**.

Mitigations that are real:

- Password nodes are labeled `[password]` and `type` is refused (`TextEntry.isPasswordField`, IME `passwordField`).
- Call UI is never tapped (`CoverCheck` / `CallGuard`).
- Lock screen is not dismissed.
- Overlay confirm uses `TYPE_ACCESSIBILITY_OVERLAY`, is stripped from the window list the agent reads, and gestures go through `OverlayHost.passThrough` so a tap cannot hit Yes/No. Command handling is a **single thread** (`pony-commands`), so the agent cannot issue a tap while `confirmOutcome` is blocking.

Residual (by design): a paired agent sees the screen and can open banking apps on the main display (those apps often refuse the hidden display and screenshots, but not always). Shizuku, if the owner enables it, runs `am start` / `input keyevent` as shell on a trusted virtual display.

### Confirmation bypass — can the agent auto-approve itself?

**It cannot press its own Yes.** That specific attack is blocked.

**It can avoid the prompt:**

1. **Unlabeled Send.** `SafetyPolicy.forTap` allows an empty label unless `MoneyScreens` matches. Chat send buttons are often icon-only. (H3)
2. **`type` then `press` `send` / `enter`.** `PonySessionService` runs SafetyPolicy on `tap` and `open_app` only. `press` and `type` are ungated (password `type` still refused). `ActionGuard` for the on-phone brain always `Allow`s `key` and `swipe`. (H3)
3. **Voice “yes” vocabulary** includes `ok`, `okay`, `sure`, `go ahead`. Ambient speech or a still-playing TTS phrase could theoretically accept. The agent cannot `speak` during an in-flight confirm on the same command thread, so this is weaker than (1)/(2). (M2)
4. **“It matches” is not a gate.** Commands can run as soon as capture is granted. (H1)

### Cleartext and logging

- Cleartext policy is strict and unit-tested (including rejecting `10.0.0.5.nip.io`).
- QR scanner `Log.d` logs miss counts and frame size, **not** the payload.
- Action / MCP logs store character counts for `type` / `speak` / `ask_user`, not the text. Tests lock this in.
- `pony-phone listen` prints the owner’s request text to the terminal (`listen.ts`). That is local stdout, not the relay. (L2)
- On-phone API keys and session private keys are AES-GCM file vaults wrapped by Android Keystore aliases `pony_brain_keys` / `pony_session_keys` / `pony_memory_keys`. **No user-authentication-required or StrongBox flag.** A stolen unlocked device, or malware with the app’s UID, can use the keys. `allowBackup` is false, which helps. (M4)

### Updates

`Updater` fetches `https://download.pony.karlmagendavid.com/latest.json`, requires https + that host, checks SHA-256, and installs only if the APK is signed with the **same certificate** as the installed app. That is a sound sideload update design. Compromise of the download host alone is not enough without the release key (which is not in this repo).

---

## 4. MCP connector

**Secrets stored:** in listen mode, `SessionFile` writes `~/.pony/mcp.json` (or `PONY_STATE`) with mode **0600** (dir 0700). The file is JSON containing the bot **X25519 private key**, token, peer key, and socket URL. It is **not** encrypted at rest — UNIX permissions only. `status` and `pair` return the pairing **token** (not the private key) to the MCP host / model.

**HTTP mode:** `listenMcp` binds **127.0.0.1**, uses MCP SDK `localhostHostValidation` + `localhostOriginValidation`, **no extra auth**. Comment in source: “One process controls one phone. No auth.” Correct for a local stdio/HTTP bridge; disastrous if someone later binds `0.0.0.0` (the CLI does not).

**Command injection:** MCP tools take numbers, enums, package names, and text. Those become encrypted protocol fields, then Android accessibility / intents. Shizuku `Runtime.getRuntime().exec(argv)` uses a **string array**, not a shell. `component` is passed as `-n` to `am start`; a malicious package name cannot run extra shell commands.

`pony-phone listen --exec` uses `spawn(command, { shell: true })`. The **owner-chosen** command is the shell string. Request text is passed via `PONY_REQUEST_TEXT` and stdin, **not** interpolated into the command line. If the owner writes `--exec 'agent "$PONY_REQUEST_TEXT"'` they re-introduce injection themselves. MCP does not expose `--exec`. (L3)

No Hostinger/Cloudflare/GitHub tokens are read or stored by the MCP package.

---

## 5. Dependencies

### npm (`npm audit` on this workspace)

| Package | Severity | Advisory | In production path? |
| --- | --- | --- | --- |
| `vitest` / `@vitest/mocker` 3.2.x | Moderate (CVSS 5.9) | [GHSA-82fw-gwwq-j7x9](https://github.com/advisories/GHSA-82fw-gwwq-j7x9) path traversal in the mocker | **Dev/test only.** Fix: upgrade vitest to ≥ 5.0.3 (major) or accept until the next test-stack bump. |

No high/critical npm production advisories. Runtime libs: `@noble/ciphers`, `@noble/curves`, `@noble/hashes`, `ws`, `zod`, MCP SDK, `qrcode`.

### Gradle / Android (public CVE databases, 2026-10-03)

| Library | Pinned | Known CVE | Reachable in Pony? |
| --- | --- | --- | --- |
| `com.squareup.okhttp3:okhttp:4.12.0` | 4.12.0 | None listed on Snyk/Sonatype for 4.12.0. CVE-2023-0833 is &lt; 4.9.2. | N/A |
| `com.google.crypto.tink:tink-android:1.16.0` | 1.16.0 | **CVE-2026-15432** (ChunkedMacVerification non-constant-time compare; CVSS 4.0 8.2 / Sonatype Medium 5.9). Fixed after 1.21.0 (current Tink Android is 1.23.0). | **Likely not reachable.** Pony uses `tink.subtle.ChaCha20Poly1305` / `X25519` / `Hkdf`, not `ChunkedMacVerification`. Still upgrade (M5). |
| `com.alphacephei:vosk-android:0.3.75` | 0.3.75 | No CVE listed | N/A |
| Compose BOM `2024.12.01`, CameraX 1.4.1, AndroidX activity 1.10.0 | older than current | No specific CVE tied to this review | Routine bump recommended (L4) |

This environment has no Android SDK / Gradle cache for a full `dependencyCheck`. Treat the table as advisory-db review, not an OWASP scan of the resolved tree.

---

## 6. Issue list (severity → concrete fix)

### H1 — Phishing pairing link takes over the phone
**High** (Critical if you count “owner tapped a link that looked official” as unattended).  
A `https://download.pony.karlmagendavid.com/pair?…` or `pony://pair?…` crafted by an attacker opens Pony, requests screen capture, and starts an encrypted session with **them**. Safety code matches. “It matches” is cosmetic.

**Fix:**

1. Do not start `PonySessionService` until the owner taps an explicit **Pair with this assistant** control that shows the relay host and client name.
2. Keep the session in a `paired-but-inert` state until “It matches” (compare codes, then enable commands).
3. Put pairing fields in the **URL fragment**, not the query (`pairPageLink` / `pair.html`).
4. Restrict App Link / `intent://` hand-off to the official pair host; still show an interstitial, never silent pair.
5. Document: only open a link you just generated on *your* computer.

### H2 — Pairing-link leak = join race
**High.** Whoever `hello`s as `phone` first owns the role. A leaked QR (screenshot, chat, email) lets an attacker join as the phone (they get a bot session to a fake phone) or, if the owner also opens it, creates confusion / lock-out (`role_taken`).

**Fix:** bind the first phone join to a short-lived proof (e.g. the phone must present a HMAC of the token with a value shown only on the creating device), or consume the token after the first successful handshake and rotate to a session id that is never in the URL. Rate-limit unknown hellos per IP as belt-and-suspenders.

### H3 — Confirmation bypass (type + send / unlabeled send)
**High.** SafetyPolicy is tap/open_app-only. Messaging Send is often unlabeled.

**Fix:** treat `press` of `send`/`enter`/`go` as Confirm when a messaging/mail/social app is in front. Treat coordinate taps with empty labels in those packages as Confirm. Optionally require Confirm for any `type` that is immediately followed by a submit key. Add tests next to `SafetyPolicyTest`.

### H4 — MCP session file is the keys
**High** on a shared or backed-up computer. `~/.pony/mcp.json` is the bot private key in plaintext JSON (0600).

**Fix:** encrypt the file with a key from the OS keychain (libsecret / DPAPI / Keychain), or require an unlock passphrase. Never sync that path to cloud backup. `status` should not need to echo the raw token to the model after pairing.

### M1 — Encrypted-frame replay inside the action TTL
**Medium.** Relay or a network observer who captured a `fwd` blob can replay it for ~15–45s. `issuedAt` helps but is attacker-controlled within 120s of clock skew.

**Fix:** monotonic message counter or timestamp in the AAD, reject reused nonces/ids on each side. Ignore client `issuedAt` when it is newer than receive time.

### M2 — Loose voice-confirm words
**Medium.** `ok` / `sure` / `go ahead` count as yes.

**Fix:** require “yes” / “yeah” / “yep” only, or a random two-digit confirm code spoken back.

### M3 — Unauthenticated, unlimited `POST /pair` + CORS `*`
**Medium** for the public relay (resource exhaustion, junk rooms).

**Fix:** per-IP rate limit and max rooms; drop CORS `*` or restrict it; optional relay auth for private deployments.

### M4 — Keystore keys usable without biometrics
**Medium.** Device unlock is enough.

**Fix:** `setUserAuthenticationRequired(true)` for brain keys (with a “use while unlocked” validity window so the agent loop does not prompt every tap). Accept that a fully unlocked stolen phone is still lost.

### M5 — Tink Android 1.16.0 / CVE-2026-15432
**Medium** as a library CVE, **Low** as exploited here.

**Fix:** bump to `tink-android:1.23.0` (or latest ≥ 1.21.1) and re-run `SessionCryptoTest`.

### M6 — Any app can fire a pairing VIEW intent
**Medium.** Complements H1.

**Fix:** ignore pairing VIEW intents unless Pony is already in the foreground pairing screen, or require a tap on a system confirmation that names the calling package when `getReferrer` / `getCallingPackage` is available.

### M7 — Long-lived established rooms (24h) + “Until I disconnect”
**Medium.** Token + saved keys remain useful long after the 15-minute pairing window.

**Fix:** default stay-connected to 30 minutes (already the UI default). Rotate a post-handshake session id; stop accepting the pairing token after `establishedAt`.

### L1 — `GET /health` room count
**Low.** Fix: omit `rooms` in production or require a bearer.

### L2 — CLI prints owner request text
**Low.** Fix: default to length-only; `--verbose` for the words.

### L3 — `--exec` with `shell: true`
**Low** (owner-controlled). Fix: document never to interpolate `$PONY_REQUEST_TEXT` into the command; or drop `shell: true` and take `argv`.

### L4 — Vitest mocker + aging AndroidX BOM
**Low.** Fix: upgrade when convenient.

### L5 — Personal Gmail on two merge commits
**Low.** See S5.

---

## 7. What is in good shape

- 256-bit pairing tokens, HKDF domain separation, directional AEAD keys, Tink-compatible nonce framing, safety-code tests on both TypeScript and Kotlin.
- Relay is honest about being an untrusted matcher; it never sees plaintext app messages.
- Password typing is refused on a11y, IME, MemoryGuard, and CarryBuffer; tests cover “do not log the secret.”
- Backup disabled; cleartext denied except private nets; update host + SHA-256 + matching signing cert.
- Exported services that must be exported use the correct bind permissions.
- MCP HTTP is localhost-only with host/origin checks.
- No keystore or production secret in git history.
- Call-screen and lock-screen policy is conservative.
- Overlay Yes/No is not a11y-tappable by the same service.

---

## 8. Suggested order of work

1. Gate pairing on an explicit owner tap and inert-until-“It matches” (H1).  
2. Close send/submit confirmation holes (H3).  
3. Move pairing secrets out of query strings; treat links as bearer tokens (H1, H2).  
4. Encrypt `~/.pony/mcp.json` (H4).  
5. Rate-limit the public relay (M3) and add AEAD replay counters (M1).  
6. Bump Tink (M5) and tighten git author email (L5).

---

*Audit performed against the public tree only. Live relay configuration, CDN App Link `assetlinks.json`, and release-signing procedures were not inspected on the deployed hosts.*
