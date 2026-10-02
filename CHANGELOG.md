# Changelog

The first list under each version is what the phone shows in **Check for updates**. Keep those lines short and in plain English.

## 0.6.2

- **Connect** now opens a picker of every brain Pony can use: your Grok Bot, plus Claude, Gemini, OpenAI, and xAI Grok that run right here on this phone with your own API key.
- Pick a brain and a simple sheet walks you through it: one button opens that provider's key page, paste the key (or tap **Paste**), and **Test & Save** checks it with a quick call before saving.
- Connected brains show a check, the one in use is marked, and each one — your Grok Bot or any API-key brain — can be disconnected on its own with a quick confirm while the rest stay connected. The keys stay sealed on the phone by the Android keystore.
- **Disconnect everything** in Settings still clears every key and unpairs in one tap.

### Details

**Brain picker**
- The Home pill's **Connect** and Ask's **Add a key** now open the Brains picker instead of jumping straight to Grok pairing. The picker lists Grok Bot (paired over Pony Cloud, unchanged) and the four API-key brains from `ProviderPreset.pickable` — Anthropic Claude, Google Gemini, OpenAI, and xAI Grok — each as a selectable row with its connected state. OpenRouter and Custom endpoints stay under **Advanced**, with the full model/base-URL editor.
- `brainChoices` maps the pickable presets to any saved brain on that provider, marking which is connected and which is in use; it's pure and unit-tested.

**Setup sheet**
- Tapping an unconnected brain opens a sheet with a one-line explanation, a **Get your <provider> API key** button that opens the real console (`console.anthropic.com/settings/keys`, `platform.openai.com/api-keys`, `aistudio.google.com/api-keys`, `console.x.ai/team/default/api-keys`), a paste field with a one-tap clipboard **Paste**, and **Test & Save**. `connectBrain` probes the key off the UI thread with a cheap, no-tools request and only saves — sealed in the keystore vault, then activated — when the probe returns `Key works.`; re-connecting a provider updates its brain in place instead of piling up duplicates.
- Keys are stored in the keystore-sealed `BrainLibrary` vault (AES-GCM, the key held in the Android Keystore) — the same protection as EncryptedSharedPreferences, without the deprecated AndroidX dependency. No key or keystore is ever written to the repo.

**Adapters**
- OpenAI and xAI already run through the shared OpenAI-compatible adapter behind the same tool interface as Claude and Gemini; 0.6.2 adds explicit build, parse, and cheap-probe tests for both so every pickable brain has the same reach and a validated key check.

**Disconnect a brain**
- Every brain disconnects on its own. Each connected row in the picker has a Disconnect control (the red unplug on an API-key brain, a labelled **Disconnect Grok Bot** on the Grok card), and tapping a connected brain opens its sheet with a **Disconnect <brain>** button. All of them ask a short "Disconnect this?" first and spell out that the rest stay connected.
- Disconnecting a brain clears only that key. `BrainLibrary.delete` promotes another saved brain to active when the one in use is removed, and the last disconnect falls back to the **No brain yet** state — covered by a new unit test alongside the existing single-active-brain test. Disconnecting Grok Bot ends the live session (its saved brains become the backup); none of this touches **Disconnect everything** in Settings, which still clears all keys and unpairs at once.

**Look and feel**
- The picker and setup sheet use Pony's cards, list rows, and bottom sheet, and every screen keeps the Android status bar (clock, signal, battery) on an opaque band so it's always legible.

## 0.6.1

- Scanning a pairing QR works reliably now, including on bright or glossy screens, with a buzz and a "Code read" note the moment the camera catches it.
- Paste or tap the pairing link in any form — the QR's JSON, a `pony://` link, or the one-tap `https://…/pair` link — and an expired link says so instead of failing quietly.
- Pony's own **Send** arrow in Ask no longer asks "Send this?" when a paired assistant taps it; every other app still gets the confirm.
- A paired assistant working on its own folds into one tidy History entry instead of flooding it, and it no longer hides your Ask box or buries the last recap.
- The after-task recap — before-and-after thumbnails and the one-tap undo — now also shows under History → Task detail, not just the pop-up card.
- If the brain is slow or the network drops, Pony retries before giving up and says "The brain took too long to answer — try again" instead of a raw error.
- A new **Disconnect everything** in Settings unpairs the assistant, erases every saved brain key from the phone, and stops Pony's background services — in one tap, with a confirm.
- Remove a single brain's key right from the brain picker, without unpairing or clearing the others.
- Pairing codes now last 15 minutes instead of 5, so there's time to scan.

### Details

**Pairing: QR scan and link formats**
- The QR analyzer no longer swallows decode exceptions: failures are logged (rate-limited) and surface a frame-error counter in debug. ZXing runs with `TRY_HARDER` and `ALSO_INVERTED`, retries with a global-histogram binarizer and a center crop, and the luminance path is verified for 90/270° rotations and padded row/pixel strides. Any successful decode gives a haptic and a "Code read" toast even if parsing then fails, so you know the camera worked.
- `beginPairing` resets a stuck `captureInFlight` guard and always gives feedback, so a camera prompt that never returned can't silently swallow every later scan.
- `PairingLinks.normalize` accepts the raw QR JSON, a `pony://pair?…` link, and the `https://…/pair` link with params in the fragment or the query string. An `exp` in the past is rejected with "This pairing link expired — ask for a new one." The download web page accepts the query-string form too.

**On-device brain fixes**
- `SafetyPolicy` exempts Pony's own package (the Ask **Send** button) from the Send gate; the gate still fires in WhatsApp, Messages, and everywhere else.
- A paired assistant's autonomous steps coalesce into one detached History session per connection (closed after a few minutes of quiet), kept out of the live slot so it never replaces an owner task's outcome or recap, never steals the Ask input, and is never attributed to a phone brain's task.
- The agent loop retries a model or network timeout up to three times with a growing, Stop-aware backoff, inside the same 30-step cap, then fails in plain language. A rejected key or refusal still fails immediately with its own message.
- The CLI REPL accepts `--main` / `--background` (and `--display=…`) anywhere on the line, matching the MCP's display argument; a trailing bare `main`/`background` still works.

**Disconnect everything and per-brain removal**
- Settings → **Disconnect everything** ends any pairing, clears every saved brain key from the keystore-backed vault, forgets the selected brain, and tears down the background session service, wake-word service, foreground notification, and overlay — behind a confirm dialog. `BrainLibrary.clearAll` removes every key and record and is unit-tested.
- The brain picker gains a per-brain **Remove key** action, so you can disconnect Claude or Gemini without hand-deleting the key or touching the others.

**Pairing token lifetime**
- The pairing token is valid for 15 minutes (`PAIRING_TTL_MS` in `shared/src/protocol.ts`, mirrored in the Kotlin `proto` package). The relay reads this constant from `@pony/shared` — `relay/src/index.ts` passes no override and there is no relay env var for it — so redeploying the relay with the rebuilt shared package applies the new lifetime.

## 0.6.0

- Pony handles freeform pop-ups now — the Settings, Google, and keyboard panels that open over an app. Taps, reading the screen, and typing all land on the window in front, so setup tasks that used to stall now finish.
- Jump straight to the right settings screen: keyboard, languages, voice typing, accessibility, and more, instead of tapping through menus that swallow touches.
- More ways to touch the screen: press and hold, drag to reorder, and pinch to zoom.
- Pony waits for a screen to finish loading instead of guessing, so it taps once things have settled.
- Pony remembers the facts and preferences you tell it — your name, a usual order — and never a password or code. Review them under Settings → Memory.
- Ask for something later or on a repeat: "every weekday at 8 read my calendar." Manage it under Settings → Scheduled tasks.
- Carry a value from one app to the next — an address, an order number — without losing it in the switch.
- Natural, higher-quality voices come first, with an optional cloud voice and a per-voice preview.
- Disconnect a paired assistant right from Home or Settings, and pick Pony's own brain for an ask without losing the pairing.
- Pair from the phone itself: a tap-to-open link and web page hand the pairing straight to Pony when the QR is on the same screen.
- After a task, a quick recap shows what Pony did, with before-and-after thumbnails and a one-tap undo when it's safe — clearing a draft it typed, turning a setting back. It never undoes anything already sent or paid.
- When a tap misses or the screen doesn't react, Pony tries another way — an accessibility click, a gesture, scrolling into view, then a search — before it reports a problem.
- Start an ask by voice without opening the app: an **Ask Pony** home-screen widget and a Quick Settings tile.
- Save something you've done as a routine and run it again by name — "my usual lunch." Pony still asks before anything it sends, pays, or books. Manage them under Settings → Saved routines.

### Details

**Freeform pop-ups and window targeting (harness)**
- Pony now reads and targets every window on screen, topmost first, with full bounds — not just the active one. The Settings, Google account, and Gboard panels that open as freeform pop-ups are read and tapped correctly instead of the app behind them, and a tall sheet is no longer clipped.
- `ui_tree` labels each window with its package, layer, and bounds. When a window has no readable nodes, the tree says so and points at the screenshot instead of returning empty boxes.
- A screenshot is never reported without an image: capture retries and a zero-byte result is an explicit error.
- Pony hides its overlay pill for the whole of every tap and gesture, so the pill can't absorb a touch mid-gesture.
- When a command explicitly asks for the hidden background display and Pony can't host there, it says so instead of silently opening a pop-up — unless your fallback preference already allows it.

**Jump to a settings screen**
- A new `open_settings` goes straight to a named screen — `input_method`, `keyboard_settings`, `languages`, `voice_input`, `accessibility`, `display`, `sound`, `wifi`, `bluetooth`, `location`, `battery`, `security`, or `app_details` for a specific app. Setup tasks land on the real screen even when protected menus swallow touches.
- The keyboard's own settings open directly through the enabled input method's settings activity, skipping the "Select input method" picker.

**Gestures**
- `long_press` holds a point for a context menu, text selection, or to pick something up; `drag` presses, moves, and releases as one slow stroke to reorder or move an item; `pinch` moves two fingers around a point to zoom a map or photo. All run on the main or hidden display and through the same pill pass-through.

**Smart waits**
- `wait_idle` waits for the screen to stop changing — a page to finish loading, an animation to end — using accessibility quiet plus a cheap screenshot-difference check, bounded by a timeout. It returns whether the screen settled and how long it waited, so Pony stops guessing fixed delays.

**Memory**
- `remember` keeps a fact or preference by a short key; `forget` drops one. What Pony knows is folded into each task so every brain sees it. Memory is stored encrypted on the phone with the same keystore-backed vault as brain keys.
- `MemoryGuard` refuses to store secrets — passwords, one-time codes, PINs, card or CVV numbers, API keys — by key name and by the shape of the value.
- Settings → **Memory** lists what Pony remembers and lets you delete one or clear all.

**Scheduled tasks**
- `schedule_task` takes a task and a time in plain words — "every weekday at 8am", "tomorrow at 6pm", "tonight at 9" — and runs it later on its own, as a fresh request. Repeats and one-shots both survive a reboot or app update.
- Settings → **Scheduled tasks** lists them with their next run, and lets you pause or delete each one.

**Cross-app context**
- `copy_text` stashes a value you can see now — an address, an order number, a price — under a short label; `recall_text` reads it back after Pony switches apps. It's scratch memory for the task in front of Pony, bounded in size, and it refuses to hold a secret.

**Natural voices**
- The voice list ranks neural, enhanced, and high-quality local voices above legacy ones, still your-language-first, each with a readable label. On-device voices stay the default; an optional, clearly labelled setting allows cloud voices. Each voice has a preview button.

**Brains**
- Every new tool above reaches the Gemini brain through the one shared tool list, so "ask Gemini" has the same reach as "ask Claude". Gemini keys are stored in the same encrypted keystore-backed vault as every other provider (equivalent to EncryptedSharedPreferences); keys are never written in the clear or returned on a record.

**Disconnect and the brain picker**
- While an assistant like Grok Bot is paired, a **Disconnect** button on the Home card and in Settings ends the session now — Pony's own brain keeps answering. After you disconnect, the card offers to reconnect through tap-link or QR.
- The brain picker stays reachable while a bot is paired, so you can pick Pony's own brain for an ask without dropping the pairing.

**Pairing from the phone itself**
- `pair` and `listen` now print a `pony://pair` link and a `pairPageLink` web page alongside the QR. When the code is on the same phone that would scan it, opening the page hands the pairing straight to Pony through an `intent://` link (with a Play Store fallback) and offers the `pony://pair` link as a backup, with the safety-code reminder. The MCP `pair` and `status` tools return the page link too. The source for the page lives in [`web/pair.html`](web/pair.html).

**Task recap and safe undo**
- After every task, Pony shows a short "Here's what I did" — the real steps, folded so repeats don't clutter it — with a **Before** and **After** thumbnail of the work. The thumbnails are of the app Pony worked in, never of Pony's own screen, and they live only in memory for the task in front of you.
- When it's safe, the recap offers a one-tap undo behind a single confirm. Clearing a draft Pony typed but never sent reopens that app and clears the field, and says so plainly if the draft is already gone. Pony never offers undo for anything already sent, paid, posted, or deleted — once a task commits, the offer is withheld.

**Reliable retries**
- When a tap lands on nothing, the screen doesn't change, or an app is slow, Pony escalates through a capped ladder — an accessibility click, a gesture at the point, scrolling the target into view, then a search path — instead of repeating the same miss or giving up. It only escalates when nothing under the point was clickable and nothing changed, so a toggle the gesture just flipped is never double-tapped.

**Ask by voice from the home screen**
- A home-screen **Ask Pony** widget (a mic and a short ask box) and a **Quick Settings** tile both start a voice ask without opening the app. They bring Pony to the front just long enough to listen — unlocking first from the tile when the phone is locked — then get out of the way. If the microphone isn't allowed yet, the listening card's "Allow microphone" button takes over.

**Saved routines**
- Finish a task, then say "save that as my usual lunch" to keep it as a named routine. Say the name any time — or "run my usual lunch" — and Pony replays the very words it first ran, back through the brain, so every Send this? and Pay? still asks. Saving is deliberately strict: an ordinary "save this photo as cover.jpg" is left for the brain, not mistaken for a routine.
- Settings → **Saved routines** lists them newest-used first, runs one on a tap, and lets you rename or delete each, or clear them all. Routines are plain instruction text kept on the phone — nothing encrypted, since it's the same words you'd say — bounded so a runaway "save this" can't fill the disk.

## 0.5.1

- Fresh suggestions every time you open Ask or Home — real, multi-step jobs like booking a table, reordering, or calling a ride, tailored to the apps you have.
- Pony finishes the moment it has your answer and says it out loud, and it ignores ads and pop-ups instead of tapping them.
- Pony gets further before it checks in: up to 30 steps, and it always pauses before anything that sends, pays, books, or orders.
- Typing replaces what's in a field, and Pony can press Enter or Search to submit it.
- Adding a brain needs only a name and an API key; the model and address wait under Advanced.
- The pairing scanner buzzes the moment it reads a code.
- Pony never mistakes its own screen for your task, opens Settings and changes things for you instead of saying it can't, and answers in plain words.
- Didn't finish? Pony tells you why and offers **Keep going** to pick up where it left off, and it asks to use your screen only once per task.

### Details

**Ask and suggestions**
- The chips on Home and Ask are real work now — "Book dinner tomorrow on Resy", "Reorder my last Amazon order", "Uber to the airport tomorrow at 6am", "Make an album of my best beach photos" — drawn from a pool of about twenty, four at a time, reshuffled each time the screen opens.
- Only chips whose app is installed are shown, so every suggestion is one Pony can actually start on this phone. Tapping one sends it straight to Pony.

**Running a task**
- The step cap is 30, so longer jobs — open an app, search, pick a result, fill a form — finish instead of stopping partway.
- Pony ends as soon as the task is done or the question is answered, with a short spoken reply, and for a question the answer itself is what it says. It no longer keeps poking once it already has the result.
- It ignores ads, "sponsored" rows, promos, and cookie, newsletter, and rate-this-app pop-ups, dismissing them only when they block the way, never acting on them.
- It still stops before anything that sends, posts, pays, buys, books, orders, deletes, or calls, and waits for your go-ahead. Changing a setting is normal work and never asks, though factory resets, screen-lock and password changes, and turning off Find My still confirm.

**From owner phone testing**
- Pony never treats its own Ask screen, orb, or pill as the task: it leaves its own UI and looks at your real phone before the first screenshot, and the prompt spells this out.
- It acts on settings instead of refusing. "Fix my autocorrect" or "set up Hebrew dictation" now opens Settings, uses the **search box** (typing something like `keyboard`, `language`, or `voice input`), and makes the change — it no longer says "I can't change that" or browses into account rows.
- For setup and configuration it goes straight to Settings search and never opens a messaging app unless the task is actually to send a message.
- If a tap doesn't change the screen, Pony stops repeating it and presses back to try the search box instead.
- Results are plain sentences. Any markdown a brain returns — `**bold**`, `- ` bullets, headings, links — is stripped before it's shown on the card or read aloud.
- A task that runs out of steps says **why** in plain words (how many steps, the last thing it tried) and offers **Keep going**, which resumes from the live screen. The step cap is 30.
- The pop-up / main-screen fallback asks **once per run**: after the first yes Pony remembers it for the rest of the session instead of asking for every app. Pick **Use a pop-up window** in Settings to never be asked.
- The owner's three failing asks are kept as regressions in [`docs/owner-regressions.md`](docs/owner-regressions.md) and in unit tests.

**Typing and keys**
- `type` replaces the focused field's text instead of adding to it, so Pony never has to clear it first. Password fields are still refused.
- `key` adds `enter` and `search`, which run the field's IME action (Go, Search, Send, or Done) to submit it. `back`, `home`, and `recents` are unchanged. The MCP `key` tool and the CLI accept the new keys too.

**Brains**
- Adding a brain asks only for a name and an API key. The model and base URL moved into an Advanced section that opens by default for a custom provider and stays closed for the presets, which already know their own defaults.

**Pairing**
- The scanner gives a success buzz the instant it decodes a pairing code, so you feel the scan land without watching the screen. It reads both the QR's JSON payload and a pasted `pony://pair` link.

**Hidden display**
- A new reference, [`docs/hidden-display-apps.md`](docs/hidden-display-apps.md), lays out which app categories run on Pony's hidden screen with and without Shizuku, why secure apps refuse, and how the main-screen fallback asks first.

## 0.5.0

- A new look, top to bottom: new type, color, motion, and haptics, in light and dark.
- Your phone's status bar stays visible on every screen.
- Asking Pony always gets an answer. If Grok Bot isn't connected, Pony says so right away and uses a brain on this phone, or does simple things like Calculator and timers itself.
- Requests wait for Grok Bot instead of failing after 20 seconds.
- Stop ends only the current task. The next ask works right away.
- Calls, WhatsApp calls, texts, and notifications no longer stop tasks. Pony waits for the call screen and never touches it.
- The connection comes back by itself after Wi-Fi drops, network switches, reboots, and app updates.
- History shows what you asked, each step, the result, and screenshots.
- Onboarding ends with a real first task: open Calculator and add 2 + 2.
- Pony asks before tapping into password fields or payment apps, and before sending.
- Check for updates from Settings.

### Details

**Ask and brains**
- The Ask page shows the status as soon as you send: sent, waiting for Grok Bot, handed to a brain on this phone, or what's missing, with one tap to fix it.
- With Grok Bot selected and not listening, a request waits up to 15 minutes. If it isn't picked up within a few seconds and a key-based brain is set up, that brain takes it. Pony Basics handles opening apps, timers, simple math, Home, and Back with no key and no network.
- Requests Grok Bot takes finish on their own if it goes quiet, so nothing sits at "picked it up".
- If Android closes Pony or it updates while a request waits for Grok Bot, the request is handed over when Grok Bot is back (within its 15 minutes). A task that was midway is closed with a plain reason instead of staying "working" in history.
- The phone says why a session ended (your choice, the assistant, or the time limit), so `pony-phone listen` and the MCP server can tell you.
- `pony-phone listen` stays paired, keeps a request open on the phone at all times, reconnects by itself, remembers the pairing across restarts, and can run a command for each request (`--exec`) and send its output back.
- The MCP server has a `--listen` mode that does the same for Grok Bot, a `done` tool to report a result the phone shows and speaks, and it follows the phone's session length.

**Stop, calls, and pop-ups**
- Stop is scoped to one task. It is lifted by a new request, a new task, a new session, or the assistant coming back for its next request. It no longer outlives the session (0.4.0 bug).
- Calls: only actions aimed at the call screen or dialer wait, and only while that screen is in front of the main display. They run by themselves when it closes (up to 10 minutes) and report `deferredMs`. Background-display actions, screenshots, and reading the screen keep working. `MODE_IN_COMMUNICATION` alone (voice notes, meetings) no longer counts as a call.
- A heads-up, notification shade, or another app's window over the tap target makes Pony wait up to 3 seconds and retry, and it never taps the pop-up.
- The live pill stays on screen during calls, minimized, with Stop.
- The phone reports progress while it holds a command, so assistants don't time out.

**Connection**
- The relay keeps an established session through a dropped connection for both sides. A rejoin with the same keys resumes it; `bye` ends it for both. This needs the 0.5.0 relay. Against an older relay, a drop still ends the session.
- The phone rejoins with backoff from 1 to 30 seconds, and after reboots and app updates.
- A request handed out just before a drop is handed out again, and listeners drop repeats.
- When the phone ends the session, the bot client fails every request at once instead of timing out (0.4.0 bug).
- A Grok Bot link with a Tailscale relay no longer switches Connection to Private or blocks later Pony Cloud links (0.4.0 bug).
- Plain `ws://` is allowed for localhost, Tailscale (100.64.0.0/10), and private LAN ranges (10/8, 172.16/12, 192.168/16) only.

**Screens**
- Home: connection badge with one-tap reconnect, Ask Pony, suggestions, and recent tasks.
- Setup updates the moment you switch something on, with no reopening (0.4.0 bug).
- The microphone permission is asked for when you first tap the mic, with live feedback while Pony listens.
- Settings is grouped and in plain words. "Voice is on" is now "Voice", and Hey Pony starts off.
- Screenshots in history are small copies kept only on the phone.
- Pairing links work every time, including right after a session ends (0.4.0 bug).
- The assistant gesture panel closes after the task, on Back, and on Stop (0.4.0 bug).
- `ui_tree` and `screenshot` no longer flood the recent list; the screenshot JSON no longer contains `"jpeg":"[B@…"` (0.4.0 bugs).
- When an app refuses Pony's hidden display, Pony says so in plain English and asks before using the main screen.
- The keyboard stays available while an assistant is connected. It is held down only for a moment around each assistant command.

**Updates**
- Settings → Check for updates reads `https://download.pony.karlmagendavid.com/latest.json`, shows the version and these notes, downloads the APK, checks its SHA-256 and that it is signed with the same key, then hands it to the Android installer.
