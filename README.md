# Pony

Free, MIT-licensed Android app that lets an AI agent you choose — Claude, Grok, Gemini, OpenAI, or any MCP client — see your phone’s screen and tap, type, and swipe. Pony is the hands. You pick the brain.

<!--
TODO: add a short demo GIF or video here once one is recorded.
Suggested: 20–40 seconds of a real phone task (for example “check my battery, then open Clock”).
Host the file in this repo (docs/demo.gif) or as a release asset, then uncomment one of:

![Pony driving an Android phone](docs/demo.gif)

<video src="docs/demo.mp4" controls muted playsinline width="600"></video>

Do not commit a fake or stock placeholder.
-->

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/oroplex/pony)](https://github.com/oroplex/pony/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/oroplex/pony/total.svg)](https://github.com/oroplex/pony/releases)

Pony is not a Grok feature and not locked to one model. The agent never needs a vendor SDK. It shares the screen, reads the UI tree, and performs the gestures — through Uber, Postmates, Resy, Settings, or anything else on the phone.

- **Site:** [pony.karlmagendavid.com](https://pony.karlmagendavid.com)
- **APK:** [pony-latest.apk](https://github.com/oroplex/pony/releases/latest/download/pony-latest.apk) — signed release build. [latest.json](https://download.pony.karlmagendavid.com/latest.json) lists the current version, size, and SHA-256.
- **Code:** [github.com/oroplex/pony](https://github.com/oroplex/pony)

## Quick start

On the Android phone:

1. Download the APK: [pony-latest.apk](https://github.com/oroplex/pony/releases/latest/download/pony-latest.apk)
2. Open the file. When Android asks, allow the browser to install unknown apps.
3. Enable **Pony control** in **Settings › Accessibility**.

Then open Pony, read the privacy promise, and agree. The first-run task (Calculator, 2 + 2) is optional.

On Android 13 and newer the Accessibility switch may be greyed out: open Pony’s **App info**, tap the **⋮** menu, and choose **Allow restricted settings**, then try again.

## If the install is blocked

Sideloading an Accessibility app is noisy on purpose. Work through these in order:

- **Open the download in Chrome**, not an in-app browser (Gmail, Messages, Slack, X, and similar). In-app browsers often cannot hand the APK to the installer.
- **Chrome says the file might be harmful.** Tap **Download anyway**. Official builds are signed; the warning is Chrome treating any APK from the web as untrusted.
- **Play Protect warning.** Tap **More details › Install anyway**. Pony is not on Google Play; Play Protect flags most sideloaded Accessibility apps.
- **In some countries Play Protect blocks sideloaded Accessibility apps entirely.** There is then no “Install anyway”. A VPN to another region does not reliably fix this. Building from source and installing over USB (`adb install`) still works. See [PLAY_POLICY.md](PLAY_POLICY.md).

Optional: check the APK against the SHA-256 in [latest.json](https://download.pony.karlmagendavid.com/latest.json) before you install.

## MCP setup

You need [Node.js 20+](https://nodejs.org/) on the computer that runs the agent. The phone side is the same Pony app.

Until `pony-mcp` is on npm, run it from a clone of this repo:

```bash
git clone https://github.com/oroplex/pony.git
cd pony
npm install
```

### Claude Desktop

Edit Claude Desktop’s MCP config (macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`; Windows: `%APPDATA%\Claude\claude_desktop_config.json`) and add:

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

Use real absolute paths. Restart Claude Desktop. Ask it to pair with your phone.

Do not launch the server with `npm run mcp` for a stdio host — npm prints a banner on stdout, which breaks MCP.

### Claude Code

From the cloned repo:

```bash
claude mcp add --transport stdio pony -- /absolute/path/to/pony/node_modules/.bin/tsx /absolute/path/to/pony/packages/mcp/src/cli.ts --listen
```

Or add the same JSON block as Claude Desktop to Claude Code’s MCP settings. Then ask Claude Code to pair.

### Other MCP clients (Cursor, and anyone that speaks MCP)

**stdio** — point the host at the same `tsx` command as above.

**Streamable HTTP** — start the server yourself, then point the host at the URL:

```bash
npm run -s mcp -- --listen --http 43123
```

Host URL: `http://127.0.0.1:43123/mcp` (localhost only).

Ask the assistant to pair. It calls `pair`, which returns a one-tap link (`pairPageLink`), a `pony://pair` link, and a QR image. Open the link on the phone, or scan the QR. Compare the six-digit safety code on both sides, tap **It matches**, and accept screen sharing (**Share entire screen**). A pairing token lasts 15 minutes.

In listen mode the server remembers the pairing (`~/.pony/mcp.json`) and rejoins on start.

Copy [`.env.example`](.env.example) for the override list (`PONY_RELAY`, `PONY_CLIENT_NAME`, `PONY_STATE`, `PONY_HOME`, `PONY_LISTEN`). The server and CLI read the process environment; they do not load `.env` themselves.

## Safety

Pony is allowed to drive the phone. It is not allowed to be reckless.

- It **asks before sending, paying, buying, deleting, calling, transferring, posting, or changing security settings**. “Yesterday” is not yes.
- **Money screens confirm every tap.** On Venmo, PayPal, Cash App, Zelle, Google Pay, and major bank apps — and on any screen that says Pay, Transfer, Send, Checkout, or Place order — Pony asks before each tap, including a bare icon or a tap by coordinates.
- **Password fields are off-limits.** They show up as `[password]`. Typing into one is refused. A tap on one asks you first.
- It never answers, declines, or ends a call, and it never taps the call screen.
- **Stop** ends the current task. The session stays up. Disconnect from the app or the notification ends the session.
- API keys for on-phone brains never leave the device (Android Keystore).
- Plain `http`/`ws` is allowed only for localhost, Tailscale (`100.64.0.0/10`), and private LAN ranges. Everything else must be `https`/`wss`.

If a star on this repo helped you find Pony — or if you want others to — tap the star. It is the whole marketing budget.

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
| **Any MCP host**: Claude Desktop, Claude Code, Cursor, and others | The Pony MCP server ([above](#mcp-setup)) | Yes, Node.js 20+ |
| **Grok Bot**, through the [Pony template](https://x.ai/bot/Kh1wQPniQK4R2l9k3XE1L) | A Grok Bot account. No API key. | No |

The default relay is Pony Cloud at `https://relay.pony.karlmagendavid.com`. A private relay — including Tailscale or your own machine — is a first-class choice. End-to-end encryption stays on in every mode.

## Requirements

- An Android phone on **Android 10** or newer (`minSdk` 29)
- **Optional:** [Shizuku](https://shizuku.rikka.app/) if you want Pony to run *other* apps on the hidden screen. Without it, Android will only place Pony’s own activities there; everything else, system apps included, bounces to your display (Pony asks first). Shizuku’s wireless-debugging setup needs Android 11+.
- **Node.js 20+** only if you run the MCP server or the `pony-phone` CLI yourself. A key on the phone or the Grok Bot template needs no computer.

Distribution is a sideloaded, **signed** APK, not Google Play. See [PLAY_POLICY.md](PLAY_POLICY.md).

## Pair a brain

A key on the phone needs no pairing. For an MCP host or Grok Bot, the quickest way to pair is a one-tap `https://download.pony.karlmagendavid.com/pair…` link: open it on the phone and it hands the pairing to Pony. The QR is the fallback when the link is on another screen: scan it (**Scan code** on Pony’s pairing screen), or paste the link (**Paste link**). Either way, compare the six-digit safety code on both sides, tap **It matches**, and accept screen sharing (**Share entire screen**). A pairing token lasts 15 minutes.

**Your own key (no computer)**

1. In Pony, tap **Connect** (or **Settings › Brain**) and pick Claude, OpenAI, Gemini, or xAI Grok.
2. Tap **Get your … API key** to open the provider’s key page, then paste the key.
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

### Tools

`pair` · `status` · `screenshot` · `ui_tree` · `wait_idle` · `tap` · `swipe` · `long_press` · `drag` · `pinch` · `type` · `key` · `open_app` · `open_settings` · `wait_for_request` · `done` · `speak` · `ask_user` · `confirm` · `disconnect`

Screen tools take optional `background` and `display` (`main` or `background`). Omit both to follow the phone. Password fields are shown as `[password]` and refused for typing. The action log stores character counts, never the text.

In listen mode a standing `wait_for_request` stays open while the assistant is active, so asks typed or spoken on the phone are not lost between tool calls. `done` reports a one-sentence result.

## Limits

- **Other apps on the hidden screen need [Shizuku](https://shizuku.rikka.app/).** Without it Android lets Pony place only its own screens on the hidden display; every other app, system apps included, bounces to your screen, and by default Pony asks before using it.
- **Banking and other secure apps refuse.** They keep themselves off the hidden screen and block screenshots, even with Shizuku.
- **DRM video shows black.** Protected video (Netflix and the like) renders black off-screen.
- **A locked phone must be unlocked.** Pony never dismisses or bypasses the lock screen. It asks you to unlock and waits.

App-by-app detail: [docs/hidden-display-apps.md](docs/hidden-display-apps.md).

## Build from source

Official GitHub release APKs (`pony-latest.apk`, `pony-0.x.y.apk`) are **signed** with a private release key that is **not** in this repository. A build from this tree uses **your** debug or release key. Android will not update an official install with a self-signed APK, or the other way around — install them side by side (the debug build is `app.pony.companion.debug`) or uninstall first.

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
./gradlew :app:assembleRelease      # local release APK (no keystore in this repo)
```

`assembleRelease` from this tree writes `android/app/build/outputs/apk/release/app-release-unsigned.apk` because the release key is not here. Sign that artifact with the existing release key **outside** this repository if you are cutting an official build. Never commit a keystore or its password.

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner sign --ks /path/to/your-release.jks \
  --out pony-0.6.4.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

Settings → Check for updates reads `https://download.pony.karlmagendavid.com/latest.json` and only installs an APK signed with the same certificate as the app already on the phone. A self-built install will not accept the official update stream.

## Layout

```
shared/         protocol + E2E crypto (TypeScript)
relay/          WebSocket pairing relay (Node, Docker)
client/         pony-phone CLI (pair, listen, unpair), bot session, mock phone
packages/mcp/   MCP server (pony-mcp)
web/            pairing hand-off page (pair.html) — not the marketing site
android/        Pony Companion app (Kotlin, Jetpack Compose)
```

```bash
docker compose up --build        # relay on :8787
npm run listen                   # print each ask from the phone
npm run pair -- --relay https://relay.pony.karlmagendavid.com
```

Relay environment is documented in [`relay/.env.example`](relay/.env.example). What changed in each release is in [CHANGELOG.md](CHANGELOG.md). How to contribute is in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE). Public domain of the idea; the code is yours to read, build, and run. Copyright (c) 2026 Karl Magen David.
