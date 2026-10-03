# Contributing to Pony

Thanks for wanting to help. Pony is a small tree: an Android app, an MCP server, a pairing relay, and the shared protocol between them. Useful changes are usually one of: a clearer first-run for a new owner, a safer tap, a phone that a new model or skin breaks, or a test that locks a fix in.

## Before you start

- Read the [README](README.md) so the safety model and the three ways to connect a brain are clear.
- Skim [docs/good-first-issues.md](docs/good-first-issues.md) if you want a scoped first change. Those drafts are meant to be filed as GitHub issues; pick one that matches how you like to work.
- Do **not** commit a keystore, signing password, API key, or `.env`. Official APKs are signed outside this repository. See `.gitignore`.

## What you need

- **Node.js 20+** for the relay, MCP server, CLI, and tests
- **JDK 17** and **Android SDK 36** (`platforms;android-36`, `build-tools;36.0.0`) for the app
- An Android 10+ phone only if you are checking a real-device path (Accessibility, Play Protect, One UI)

```bash
npm install
npm test          # relay, bot client, listener, MCP server, crypto
npm run demo      # scripted session against a mock phone
```

```bash
cd android
./gradlew :app:testDebugUnitTest    # JVM tests and screenshot tests
./gradlew :app:assembleDebug        # optional local APK
```

Point `ANDROID_HOME` at the SDK. You do not need a release key to run tests.

## How a change should look

- **One concern per pull request.** A money-app package name and a README rewrite do not belong together.
- **Tests for new logic.** Kotlin helpers should be unit-testable on the JVM. Wire and MCP changes need a vitest case. If a change is docs-only, say so in the PR.
- **Keep the four phone-tool surfaces in lockstep** when you add or change a command: the on-phone brain tools, `PhoneOps` / `ScreenRouter`, the shared protocol + Android session + MCP + CLI + mock phone, and the tests. [docs/0.6-plan.md](docs/0.6-plan.md) spells this out.
- **Safety stays on the phone.** Confirmations for send / pay / delete / call, money-screen every-tap confirms, and the password-field refusal are not optional and must not be bypassable from MCP.
- **No new signing keys, CI secrets, relay config, Caddy files, or release assets** unless the owner asked for that work by name.

## Where things live

```
android/        Pony Companion (Kotlin, Jetpack Compose)
packages/mcp/   pony-mcp server
shared/         protocol + E2E crypto
relay/          WebSocket pairing relay
client/         pony-phone CLI, bot session, mock phone
web/            pairing hand-off page only (pair.html)
docs/           hidden-display notes, owner regressions, first-issue drafts
```

The public marketing site at [pony.karlmagendavid.com](https://pony.karlmagendavid.com) is **not** in this repository.

## Issues and pull requests

- **Bugs** — use the Bug report template. Phone model, Android version, and One UI (Samsung) matter; a lot of Pony’s hard cases are OEM-specific.
- **Ideas** — use the Feature request template. Say what a person does today, what they want instead, and why the safety model still holds.
- **Pull requests** — fork (or a branch on this repo), keep `main` the base, and describe what you verified. CI runs `npm test`, `npm run demo`, the Android unit tests, and an unsigned `assembleRelease` (the official signed APK is produced outside CI).

If you get stuck, open the issue anyway with what you tried. A half-reproduced One UI bounce is still useful.
