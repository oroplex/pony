# Which apps run on Pony's hidden screen

Pony prefers to act **out of sight**: it opens the app on a secondary
("hidden") display and drives it there, so your real screen stays yours.
Not every app will run on a hidden display, and what works depends on
whether **Shizuku** is turned on. This page is the honest matrix.

Pony never guesses. After it opens an app it watches, for up to three
seconds, whether that app actually leads the hidden display. If it does
not, Pony falls back (with your permission) and every later step says
**"freeform"** or **"main"** instead of **"background"**, so the placement
you see is always the real one.

## The two modes

**Default — app‑owned display.** With no extra setup Pony creates a normal
app‑owned `VirtualDisplay`. Android's window manager refuses to place most
*other* apps' activities on an untrusted display, so this mode is best for
reading the screen and for the subset of apps the system is willing to host
off‑screen. Many apps bounce back to the main screen; Pony detects that and
asks before using it.

**Enhanced — Shizuku trusted display.** When you install Shizuku and grant
Pony, a tiny service runs as the shell user and creates a **trusted**
virtual display — the same kind `scrcpy` uses, and the only kind the window
manager will populate with arbitrary apps. While it is up, Pony also flips
the "force resizable activities" developer setting so portrait‑locked apps
still open there, and restores it afterward. This is the mode that makes the
hidden screen broadly useful.

## Matrix by category

| App category | Default (app‑owned) | With Shizuku (trusted) |
| --- | --- | --- |
| Settings, Calculator, Clock, Files, system utilities | Usually works | Works |
| Google apps (Maps, Gmail, Photos, YouTube, Calendar) | Often bounces to main | Works |
| Social & chat (WhatsApp, Messenger, Slack, X, Instagram) | Often bounces to main | Works |
| Shopping & food (Amazon, Target, DoorDash, Uber Eats, Instacart) | Often bounces to main | Works |
| Rides & travel (Uber, Lyft, Airbnb, United, HotelTonight) | Often bounces to main | Works, but map/GPS views may look empty off‑screen |
| Media you own a session in (Spotify, Podcasts) | Sometimes works | Works |
| DRM video (Netflix, Disney+, Prime Video) | Refuses / black frame | Opens, but protected frames stay black on a virtual display |
| Banking, wallets, authenticators, anything `FLAG_SECURE` | Refuses | Usually refuses; the app itself blocks secondary displays |
| Camera, phone dialer, always‑on‑top overlays | Stays on main by design | Stays on main by design |

"Usually works" / "often bounces" describe the common case. The real answer
varies by phone maker, Android version, and app build, which is exactly why
Pony verifies landing at runtime rather than trusting a list.

## Why an app refuses

- **`FLAG_SECURE`** — banking, wallet, and authenticator apps mark their
  windows secure; the OS keeps them off virtual displays and blanks capture.
- **Display affinity** — `singleInstance`/`singleTask` activities or apps
  that pin themselves to the default display re‑parent back to the main
  screen the moment they launch.
- **Untrusted display** — without Shizuku the display is app‑owned, and the
  window manager declines to host most foreign activities on it at all.
- **Protected media** — DRM surfaces render black on a non‑protected virtual
  display even when the activity itself opens.

## When an app can't go off‑screen

Pony asks first (unless you set a standing choice in Settings → main‑screen
fallback: *ask*, *allow*, or *never*). If you allow it, Pony prefers a
**freeform pop‑up** on phones that support one (Samsung pop‑up view or the
platform freeform feature) before taking over the full main screen, and it
tells you which happened. Choosing *never* means a refusing app is reported
as blocked instead of ever touching your screen.

## Getting the widest coverage

1. Install **Shizuku** and start it (wireless debugging, or `adb`, or root).
2. In Pony → Settings, turn on the enhanced hidden display and tap **Grant**.
3. Keep **main‑screen fallback** on *ask* so a refusing app (e.g. your bank)
   never lands on your screen without you.

Even with Shizuku, `FLAG_SECURE` apps are expected to refuse — that is the
app protecting itself, not a Pony bug.
