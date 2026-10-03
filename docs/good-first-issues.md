# Good first issues (drafts)

Paste these into GitHub Issues when you want them public. They are drafts only — **do not file them from an automated pass**. Suggested labels are `good first issue` plus the extra label in each header.

Each one is sized so someone new to the tree can finish it without a signing key, the relay, or Caddy.

---

## 1. Add more money-app package names

**Title:** Add missing payment / bank package names to `MoneyScreens`  
**Labels:** `good first issue`, `enhancement`

On payment and checkout screens Pony asks before **every** tap. The catalog is `MoneyScreens.KNOWN_APPS` in [`android/app/src/main/java/app/pony/companion/voice/MoneyScreens.kt`](../android/app/src/main/java/app/pony/companion/voice/MoneyScreens.kt). A row with no `activities` treats the whole package as a money screen; named activities limit the match to checkout (Amazon is the example).

**Ask**

1. Pick apps people actually pay with that are not already listed. High-value gaps: Stripe / Shop Pay / Apple-adjacent wallets that exist on Android, regional banks (EU, LatAm, IN, JP, KR), food-delivery checkout packages if they are distinct from the store app.
2. Add `KnownApp("exact.package.name", "Label")` rows. Package names must be exact — install the app or look them up; do not guess.
3. Extend the existing JVM tests so a tap on the new package confirms, and a non-checkout screen in a shopping app with `activities` still does not.
4. One PR per region or family of apps is better than a 40-row dump with no tests.

Do not weaken the generic heuristics (`Pay`, `Transfer`, `Checkout`, …) to make a row unnecessary. The list is the whole catalog; no other file needs a matching change.

---

## 2. Saved routines: export, import, and a clearer empty state

**Title:** Export / import saved routines and tighten the empty state  
**Labels:** `good first issue`, `enhancement`

Routines already save the owner’s last finished ask and replay it by name (`RoutineBook`, `RoutineStore`, Settings › Saved routines). Replay still goes through the brain and every Send this? / Pay? gate. What is missing is a way to take routines off one phone and onto another, and a first-run empty state that tells you *how* to save one.

**Ask**

1. Add export / import of `routines.json` (share sheet out, file picker in). Reject a file that is not a routine list; never import secrets (there should be none).
2. Rewrite the empty state so it says: finish a task, then say “save that as a routine” or use the save control, then tap the name to run it.
3. Keep `RoutineStore` pure and JVM-tested. Add a test for a round-trip file and for a garbage file.

Out of scope: cloud sync, encrypting the file, or skipping safety on replay.

---

## 3. Watch-and-alert: first slice

**Title:** Watch a screen phrase and notify when it appears  
**Labels:** `good first issue`, `enhancement`

Owners ask for “tell me when my Uber is arriving” or “watch this tracking page and ping me when the status changes.” Scheduled tasks already fire an ask at a clock time. This issue is the smallest *watch*: look at the current screen on an interval and alert if a phrase shows up.

**Ask**

1. Spec the smallest product that is still useful: a phrase, an app package (optional), an interval (30s–2min), a notification when `ui_tree` / visible text matches, and a way to cancel.
2. Reuse the existing notification channels and the safety policy. A watch must **not** tap, type, send, or pay. Alert only.
3. Persist the watch like a scheduled task (plain JSON, no secrets). Survive a process death.
4. JVM tests for the matcher (phrase hit / miss, package filter, cancelled watch).

Out of scope: OCR on a screenshot, watching notifications from other apps, and any path that acts on the owner’s behalf when the phrase hits.

---

## 4. Translations: extract strings and add one locale

**Title:** Extract user-facing strings and add a first translation  
**Labels:** `good first issue`, `enhancement`

[`android/app/src/main/res/values/strings.xml`](../android/app/src/main/res/values/strings.xml) has only the Accessibility, IME, notification, and widget copy. Almost all Compose UI is hardcoded English. A first translation is blocked until those strings are resources.

**Ask**

1. Move one coherent screen — suggested: the first-run privacy promise, or Settings › About / Check for updates — to `strings.xml`.
2. Add `values-<locale>/strings.xml` for **one** locale you speak (for example `es`, `de`, `fr`, `pt-rBR`, `ja`, or `zh-rCN`).
3. Do not translate the Accessibility description into something weaker. The user must still learn that Pony can see the screen, tap, and that passwords are off-limits.
4. Screenshot or Roborazzi test for the extracted screen so a later locale cannot silently drop a paragraph.

A follow-up issue can take the next screen. Do not machine-translate the whole app in one PR.

---

## 5. Docs: OEM install notes and a real demo slot

**Title:** Document OEM install gotchas; leave the README demo slot ready for a real recording  
**Labels:** `good first issue`, `documentation`

The README now has a commented TODO for a demo GIF/video and an install-troubleshooting section (Chrome, Play Protect, country-level blocks). What is still thin is OEM-specific friction after the APK is on the phone.

**Ask**

1. Add a short `docs/install-oem.md` (or a section in the README) for Xiaomi / HyperOS, Oppo / ColorOS, Vivo, One UI, and stock Pixel: autostart, battery restrictions, “display over other apps,” and the Android 13 **Allow restricted settings** path.
2. Do not invent a demo GIF. If you record one on a real phone, drop it at `docs/demo.gif` (or a release asset) and uncomment the README slot. No stock footage, no mock UI.
3. Keep Android 10 (`minSdk` 29) as the stated minimum. Do not write “Android 11+” unless you are talking about Shizuku wireless debugging.

---

## 6. MCP host recipes: Cursor, VS Code, Windsurf

**Title:** Add copy-paste MCP configs for Cursor, VS Code, and Windsurf  
**Labels:** `good first issue`, `documentation`

The README covers Claude Desktop, Claude Code, and a generic stdio / HTTP setup. Other hosts each want a slightly different JSON file.

**Ask**

1. Add a `docs/mcp-hosts.md` (link it from the README MCP section) with a verified snippet for Cursor, VS Code Copilot / MCP, and Windsurf.
2. Use the from-clone `tsx` command, not `npx pony-mcp`, until the package is published. Call out that `npm run mcp` breaks stdio hosts because npm writes a banner to stdout.
3. One sentence each for pair → safety code → Share entire screen.
4. If you cannot run a host, mark that snippet **unverified** and say so.

Do not publish `pony-mcp` to npm as part of this issue.

---

## 7. Hidden-display matrix: add apps you actually tried

**Title:** Extend the hidden-display app matrix with phones you have  
**Labels:** `good first issue`, `documentation`

[`docs/hidden-display-apps.md`](hidden-display-apps.md) is the honest list of what runs on Pony’s hidden screen, with and without Shizuku. It is only as good as the phones people have tried.

**Ask**

1. On a real phone, try a few apps that are missing or marked vaguely (maps, food delivery, airline, a regional bank).
2. Record: phone model, Android version, One UI / skin, Shizuku on or off, whether the app stayed on the hidden display, bounced to main, or blocked screenshots.
3. Add a row or a footnote. Do not “correct” a working category from memory.

A Pixel 8 and a Galaxy on One UI 8 that disagree is useful; pick a winner in prose, do not delete the other.

---

## 8. Money-screen tests for a checkout you can open

**Title:** Capture one real checkout tree and lock it in a JVM test  
**Labels:** `good first issue`, `enhancement`

Heuristics catch screens that say Pay / Checkout even when the package is unknown. They are only as good as the fixtures.

**Ask**

1. Pick a checkout you can open without paying (a food-delivery cart, a store’s order-review page, a sandbox wallet).
2. Dump the visible labels / window title / activity name (from `ui_tree` or a log). Redact amounts and names.
3. Add a JVM test that `MoneyScreens.matches(...)` is true for that fixture and false for a nearby non-pay screen in the same app (menu, search, order history).
4. If the package is missing from `KNOWN_APPS` and every screen in that app moves money, add the row too.

Do not commit screenshots that show a card number, a balance, or someone else’s address.
