# Owner regression asks

Real tasks the owner ran on a phone that Pony got wrong, kept here so they stay
fixed. Each one should now work end to end with a key brain (for example Claude)
on the phone. The behaviour is driven by the agent system prompt
(`AgentLoop.SYSTEM`), the safety policy (`SafetyPolicy`), and the task tracker.

## 1. "When I type there are lots of typos and autocorrect isn't good. Can you fix this?"

- Pony must **not** answer by describing its own Ask screen. The first look
  leaves Pony's own UI (`VoiceController.leaveOwnUi` presses home on the main
  screen) and the prompt says Pony's own app is never the task.
- Pony must act, not refuse. It opens **Settings**, uses the **search box**
  (type `keyboard` or `autocorrect`), opens the active keyboard's settings
  (e.g. Samsung Keyboard → Smart typing), and turns on autocorrect / predictive
  text / auto spell check.
- It reports what it changed in short, plain sentences — no markdown.
- No confirmation is required: changing a setting is normal work.

## 2. "I want to dictate in Hebrew and have the keyboard type Hebrew text; set that up for me."

- Settings → search `language` → **Samsung Keyboard → Languages and types →
  Manage input languages** → add **Hebrew**.
- Settings → search `voice input` → **Google voice typing / Samsung voice
  input → Languages** → add **Hebrew**.
- The old 8-step cap killed this task early. The cap is now 30
  (`AgentLoop.MAX_STEPS`). If Pony still runs out, the result says **why** in
  plain words (how many steps, the last thing it tried) and offers **Keep
  going**, which resumes from the live screen.

## 3. "I want to speak in Hebrew and have my phone text it in Hebrew."

- Same Hebrew setup as #2. This is a configuration task, so Pony goes straight
  to **Settings** and uses **search** — it does **not** open Google Messages or
  any messaging app, and it does not browse into the Samsung account row.
- Pony asks the pop-up / visible-screen fallback **once per run** (after the
  first yes it remembers for the rest of the session), instead of asking again
  for every app. Owners who never want to be asked can pick **Use a pop-up
  window** under Settings → Hidden screen.
- Stuck detection: if a tap leaves the screen unchanged, Pony is told to stop
  repeating it and press back / use the search box instead (`StallCheck`).

## 4. "Open the Google app, go to my profile, and set voice typing to Hebrew." (0.6, end to end)

The owner's hardest path, because every screen here is a freeform pop-up that
isn't the active accessibility window — the Google app's account sheet, the
Settings panel it hands off to, and the keyboard's own language picker. In 0.5.1
Pony read and tapped only `rootInActiveWindow`, so taps landed on the app
underneath and the task stalled. 0.6 fixes make it work:

- **Topmost-window targeting** (`a11y/WindowStack`): `ui_tree`, tap labelling,
  focus, and foreground all walk every window root topmost-first with full
  bounds, so a tap on the pop-up lands on the pop-up, not the app behind it, and
  the sheet is no longer clipped.
- **`open_settings`**: Pony jumps straight to `voice_input` (and `languages` /
  `keyboard_settings`) instead of tapping through account rows that swallow
  touches — it lands on the real screen even inside a protected menu.
- **Direct IME settings**: the keyboard's own settings activity
  (`InputMethodInfo.getSettingsActivity`) is launched directly, so **Languages**
  opens without the "Select input method" picker in the way.
- With the 30-step cap and **Keep going**, the full chain (Google app → profile
  → Settings → Voice → Languages → Hebrew) finishes in one run.



Only **send, post, pay, buy, book, order, delete, and call** wait for the
owner. Settings changes never ask. Genuinely destructive security actions
(factory reset, changing the screen lock or passwords, turning off Find My)
still confirm through `SafetyPolicy` as a backstop.
