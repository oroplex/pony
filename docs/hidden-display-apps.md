# Which apps run on Pony's hidden screen

Pony prefers to act **out of sight**: it opens the app on a secondary
("hidden") display and drives it there, so your real screen stays yours.
Not every app will run on a hidden display, and what works depends on
whether **Shizuku** is turned on. This page is the honest matrix.

Pony never guesses. After it opens an app it watches, for up to three
seconds, whether that app actually leads the hidden display. If it does
not, Pony falls back (with your permission) and later steps target **"main"**
instead of **"background"**. The bounce warning is only on that open, so a
later Home press does not still name the app that refused.

## The two modes

**Default — app‑owned display.** With no extra setup Pony creates a normal
app‑owned `VirtualDisplay`. Android lets a normal app place only **its own**
activities there: a foreign activity may start only on a *trusted* display,
and only a holder of the privileged `ADD_TRUSTED_DISPLAY` permission can make
one. So on a stock phone every other app, system apps like Settings and
Calculator included, bounces back to the main screen. This mode is good for
Pony's own screens and little else; Pony detects the bounce and asks before
using your screen. (Pony still requests a trusted display as a best effort.
Rooted or privileged builds may honour it; stock phones drop the flag.)

**Enhanced — Shizuku trusted display.** When you install Shizuku and grant
Pony, a tiny service runs as the shell user and creates a **trusted**
virtual display — the same kind `scrcpy` uses, and the only kind the window
manager will populate with arbitrary apps. While it is up, Pony also flips
the "force resizable activities" developer setting so portrait‑locked apps
still open there, and restores it afterward. This is the mode that makes the
hidden screen broadly useful. The shell can create a trusted display on
Android 16, Android 14 and older, and custom ROMs that grant the shell the
display permissions. On Android 17 / One UI 9 the trusted display is created
in its own display group so foreign activities stay there instead of bouncing
to a freeform window on the main screen; Android 11–16 keep the previous
flags. If it can't on your phone, Pony doesn't launch other apps on the
shell display and falls back full-screen (not freeform) on the main display.

## Matrix by category

| App category | Default (app‑owned) | With Shizuku (trusted) |
| --- | --- | --- |
| Settings, Calculator, Clock, Files, system utilities | Bounces to main | Works |
| Google apps (Maps, Gmail, Photos, YouTube, Calendar) | Bounces to main | Works |
| Social & chat (WhatsApp, Messenger, Slack, X, Instagram) | Bounces to main | Works |
| Shopping & food (Amazon, Target, DoorDash, Uber Eats, Instacart) | Bounces to main | Works |
| Rides & travel (Uber, Lyft, Airbnb, United, HotelTonight) | Bounces to main | Works, but map/GPS views may look empty off‑screen |
| Media you own a session in (Spotify, Podcasts) | Bounces to main | Works |
| DRM video (Netflix, Disney+, Prime Video) | Bounces to main | Opens, but protected frames stay black on a virtual display |
| Banking, wallets, authenticators, anything `FLAG_SECURE` | Bounces to main | Usually refuses; the app itself blocks secondary displays |
| Camera, phone dialer, always‑on‑top overlays | Stays on main by design | Stays on main by design |

Without Shizuku, only Pony's own screens run on the hidden display; the
"Bounces to main" column is the platform rule on a stock phone, not a guess.
The Shizuku column describes the common case. The real answer varies by phone
maker, Android version, and app build, which is exactly why Pony verifies
landing at runtime rather than trusting a list.

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

Pony asks first (unless you set a standing choice in Settings → **Apps that
can't work out of sight**: *Ask me each time*, *Use a pop‑up window*, or
*Never use my screen*). If you allow it, Pony prefers a
**full-screen window** on the main display — not a freeform / Samsung pop-up,
whose chrome was taller than the panel on the Galaxy S26 Ultra. Choosing
*Never use my screen* means a refusing app is reported as blocked instead of
ever touching your screen. The warning is only on that open, so a later Home
press does not still name the app that bounced.

## Getting the widest coverage

1. In Pony → Settings, under **Run apps out of sight (Shizuku)**, tap
   **Set up Shizuku**. Pony walks you through installing Shizuku, pairing it
   with Wireless debugging, and tapping **Start** (adb or root also work).
2. Tap **Allow** when Shizuku asks whether Pony may use it, and keep
   **Use Shizuku** on. Shizuku stops after each reboot; open it and tap
   **Start** again.
3. Keep **Apps that can't work out of sight** on *Ask me each time* so a
   refusing app (e.g. your bank) never lands on your screen without you.

Even with Shizuku, `FLAG_SECURE` apps are expected to refuse — that is the
app protecting itself, not a Pony bug.
