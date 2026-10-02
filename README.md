# Pony

**Pony is the hands. You pick the brain.**

Pony is a harness for your Android phone. Claude, Grok, Gemini, OpenAI, or any MCP agent can see the screen and tap and type through apps that have no API — Uber, Postmates, Resy, phone Settings.

The agent never needs a vendor SDK. Pony shares the screen, reads the UI tree, and performs the gestures. You choose which model — or which MCP host — is in charge.

- **Site:** [pony.karlmagendavid.com](https://pony.karlmagendavid.com)
- **APK:** [pony-latest.apk](https://download.pony.karlmagendavid.com/pony-latest.apk) (0.6.2, SHA-256 `c64f123b24b8969474ebc117664dbbc4b0420eaa139030fd7b468c5660b697a6`). [latest.json](https://download.pony.karlmagendavid.com/latest.json) always lists the current version and its SHA-256.
- **Code:** [github.com/oroplex/pony](https://github.com/oroplex/pony)
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

## Three ways to connect a brain

Pick whichever fits. The phone side is the same app in every case.

| Brain | What you need | Computer? |
| --- | --- | --- |
| **Your own API key, on the phone**: Claude, OpenAI, Gemini, or xAI Grok (OpenRouter and any OpenAI-compatible endpoint under Advanced) | A key from that provider | No. The agent loop runs on the phone. |
| **Any MCP host**: Claude Desktop, Claude Code, Cursor, and others | The Pony MCP server ([below](#mcp-setup)) | Yes, Node.js 20+ |
| **Grok Bot**, through the [Pony template](https://x.ai/bot/Kh1wQPniQK4R2l9k3XE1L) | A Grok Bot account. No API key. | No |

The default relay is Pony Cloud at `https://relay.pony.karlmagendavid.com`. A private relay — including Tailscale or your own machine — is a first-class choice. End-to-end encryption stays on in every mode.

## Requirements

- An Android phone on **Android 11** or newer
- **Optional:** [Shizuku](https://shizuku.rikka.app/) if you want Pony to run *other* apps on the hidden screen. Without it, Android will only place Pony's own activities there; everything else, system apps included, bounces to your display (Pony asks first).
- **Node.js 20+** only if you run the MCP server or the `pony-phone` CLI yourself. A key on the phone or the Grok Bot template needs no computer.

Distribution is a sideloaded APK, not Google Play. See [PLAY_POLICY.md](PLAY_POLICY.md).

## Install

1. On the phone, download [pony-latest.apk](https://download.pony.karlmagendavid.com/pony-latest.apk) and open it. If Android asks, allow your browser to install unknown apps. (Optional: check the file against the SHA-256 above.)
2. Open Pony, read what it does, and agree to the privacy promise.
3. Turn on **Pony control** in **Settings › Accessibility**. On Android 13 and newer the switch may be greyed out: first open Pony's **App info**, tap the **⋮** menu, and choose **Allow restricted settings**.
4. Run the first task if you like (Pony opens Calculator and adds 2 + 2).

## Pair a brain

A key on the phone needs no pairing. For an MCP host or Grok Bot, the quickest way to pair is a one-tap `https://download.pony.karlmagendavid.com/pair…` link: open it on the phone and it hands the pairing to Pony. The QR is the fallback when the link is on another screen: scan it (**Scan code** on Pony's pairing screen), or paste the link (**Paste link**). Either way, compare the six-digit safety code on both sides, tap **It matches**, and accept screen sharing (**Share entire screen**). A pairing token lasts 15 minutes.

**Your own key (no computer)**

1. In Pony, tap **Connect** (or **Settings › Brain**) and pick Claude, OpenAI, Gemini, or xAI Grok.
2. Tap **Get your … API key** to open the provider's key page, then paste the key.
3. Tap **Test & Save**. Pony checks the key with a quick call before saving it. The key is sealed by the Android Keystore and never leaves the phone except to reach the provider you picked.

**Any MCP host**

1. On your computer, start the MCP server ([MCP setup](#mcp-setup)) and add it to your host.
2. Ask the assistant to pair. It calls `pair`, which returns the one-tap link (`pairPageLink`), a `pony://pair` link, and a QR image.
3. Open the link on the phone, or scan the QR. Compare the safety code, tap **It matches**, and share the screen.

In listen mode the server remembers the pairing (`~/.pony/mcp.json`) and rejoins on start.

**Grok Bot**

1. Start the [Pony template](https://x.ai/bot/Kh1wQPniQK4R2l9k3XE1L) in Grok Bot. It explains what Pony can see and waits for your OK.
2. Grok Bot sends a pairing link (and a QR as a fallback). Tap the link on the phone.
3. Compare the safety code, tap **It matches**, and share the screen.

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

## Limits

- **Other apps on the hidden screen need [Shizuku](https://shizuku.rikka.app/).** Without it Android lets Pony place only its own screens on the hidden display; every other app, system apps included, bounces to your screen, and by default Pony asks before using it.
- **Banking and other secure apps refuse.** They keep themselves off the hidden screen and block screenshots, even with Shizuku.
- **DRM video shows black.** Protected video (Netflix and the like) renders black off-screen.
- **A locked phone must be unlocked.** Pony never dismisses or bypasses the lock screen. It asks you to unlock and waits.

App-by-app detail: [docs/hidden-display-apps.md](docs/hidden-display-apps.md).

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
