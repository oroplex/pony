# Pony

**Pony is the hands. You pick the brain.**

Pony is a harness for your Android phone. Claude, Grok, Gemini, OpenAI, or any MCP agent can see the screen and tap and type through apps that have no API — Uber, Postmates, Resy, phone Settings.

The agent never needs a vendor SDK. Pony shares the screen, reads the UI tree, and performs the gestures. You choose which model — or which MCP host — is in charge.

- **Site:** [pony.karlmagendavid.com](https://pony.karlmagendavid.com)
- **APK:** [download.pony.karlmagendavid.com](https://download.pony.karlmagendavid.com)
- **This tree:** 0.6.2 (versionCode 9), MIT licensed

## What it is

Your phone opens an outbound WebSocket to a relay. Your assistant does the same. A one-time code pairs them. Screenshots and commands are encrypted on the two ends, so the relay cannot read them.

Pony can:

- Screenshot the screen and read the accessibility tree
- Tap, swipe, long-press, drag, pinch, and type
- Open apps by package name and jump straight to Settings screens
- Work on a hidden display so your phone stays yours
- Take spoken or typed asks on the phone and hand them to a paired assistant

On the phone you can also run a key-based brain (Claude, Gemini, OpenAI, xAI Grok, OpenRouter, or any OpenAI-compatible endpoint). Keys stay on the device, sealed by the Android Keystore. Assistants never see them.

The default relay is Pony Cloud at `https://relay.pony.karlmagendavid.com`. A private relay — including Tailscale or your own machine — is a first-class choice. End-to-end encryption stays on in every mode.

## Requirements

- An Android phone on **Android 11** or newer
- **Optional:** [Shizuku](https://shizuku.rikka.app/) if you want Pony to run *other* apps on the hidden screen. Without it, Android will only place Pony's own activities there; everything else may bounce to your display (Pony asks first).
- A computer with **Node.js 20+** to run the MCP server (or the `pony-phone` CLI)

Distribution is a sideloaded APK, not Google Play. See [PLAY_POLICY.md](PLAY_POLICY.md).

## Install and pair

1. Download the APK from [download.pony.karlmagendavid.com](https://download.pony.karlmagendavid.com) and install it.
2. On Android 13 and newer, open **App info → Allow restricted settings** so the Pony accessibility switch can move.
3. Walk the four onboarding screens, turn on the Pony control, and run the first task (Pony opens Calculator and adds 2 + 2).
4. On your computer, start the MCP server (below). The assistant calls `pair` and shows a QR or a `pony://pair` link.
5. Scan the QR, or tap the one-tap `https://download.pony.karlmagendavid.com/pair…` link on the phone. Compare the six-digit safety code and tap **It matches**. Accept screen sharing (**Share entire screen**).

A pairing token lasts 15 minutes. After that, the server remembers the pairing (`~/.pony/mcp.json`) and rejoins on start.

## MCP setup

```bash
npm install
npm run -s mcp -- --listen --http 43123
```

Point the MCP host at `http://127.0.0.1:43123/mcp`. For a stdio host, launch the server directly — npm prints a banner on stdout, which breaks stdio:

```json
{
  "mcpServers": {
    "pony": {
      "command": "/absolute/path/to/pony/node_modules/.bin/tsx",
      "args": ["/absolute/path/to/pony/packages/mcp/src/cli.ts", "--listen"],
      "env": { "PONY_RELAY": "https://relay.pony.karlmagendavid.com" }
    }
  }
}
```

Copy [`.env.example`](.env.example) if you want a documented list of overrides (`PONY_RELAY`, `PONY_CLIENT_NAME`, `PONY_STATE`, `PONY_HOME`, `PONY_LISTEN`). The server and CLI read the process environment; they do not load `.env` themselves.

### Tools

`pair` · `status` · `screenshot` · `ui_tree` · `wait_idle` · `tap` · `swipe` · `long_press` · `drag` · `pinch` · `type` · `key` · `open_app` · `open_settings` · `wait_for_request` · `done` · `speak` · `ask_user` · `confirm` · `disconnect`

Screen tools take optional `background` and `display` (`main` or `background`). Omit both to follow the phone. Password fields are shown as `[password]` and refused for typing. The action log stores character counts, never the text.

In listen mode a standing `wait_for_request` stays open while the assistant is active, so asks typed or spoken on the phone are not lost between tool calls. `done` reports a one-sentence result.

## Safety

Pony is allowed to drive the phone. It is not allowed to be reckless.

- It **asks before sending, paying, buying, deleting, calling, transferring, posting, or changing security settings**. "Yesterday" is not yes.
- **Password fields are masked** as `[password]`. Typing into one is refused. A tap on one asks you first.
- It never answers, declines, or ends a call, and it never taps the call screen.
- **Stop** ends the current task. The session stays up.
- API keys for on-phone brains never leave the device.
- Plain `http`/`ws` is allowed only for localhost, Tailscale (`100.64.0.0/10`), and private LAN ranges. Everything else must be `https`/`wss`.

## Build from source

Official APKs are signed with a private release key that is **not** in this repository. A build from this tree uses **your** debug or release key. Android will not update an official install with a self-signed APK, or the other way around — install them side by side (the debug build is `app.pony.companion.debug`) or uninstall first.

```bash
npm install
npm test          # relay, bot client, listener, MCP server, crypto
npm run demo      # a scripted session against a mock phone
```

The Android app needs JDK 17 and Android SDK 36 (`platforms;android-36`, `build-tools;36.0.0`). Point `ANDROID_HOME` at the SDK.

```bash
cd android
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # JVM tests and screenshot tests
./gradlew :app:assembleRelease      # unsigned release APK
```

Sign a release build with a keystore you control. Never commit the keystore or its password.

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner sign --ks /path/to/your-release.jks \
  --out pony-0.6.2.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

Settings → Check for updates reads `https://download.pony.karlmagendavid.com/latest.json` and only installs an APK signed with the same certificate as the app already on the phone. A self-built install will not accept the official update stream.

## Layout

```
shared/         protocol + E2E crypto (TypeScript)
relay/          WebSocket pairing relay (Node, Docker)
client/         pony-phone CLI (pair, listen, unpair), bot session, mock phone
packages/mcp/   MCP server (@pony/mcp)
web/            pairing hand-off page (pair.html)
android/        Pony Companion app (Kotlin, Jetpack Compose)
```

```bash
docker compose up --build        # relay on :8787
npm run listen                   # print each ask from the phone
npm run pair -- --relay https://relay.pony.karlmagendavid.com
```

Relay environment is documented in [`relay/.env.example`](relay/.env.example). What changed in each release is in [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE). Copyright (c) 2026 Karl Magen David.
