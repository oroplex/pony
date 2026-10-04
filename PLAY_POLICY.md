# Google Play and the Accessibility API

Pony Companion is meant to ship as a direct APK and on F-Droid. A Play listing is a separate track, and the likely outcome of a listing framed as "an assistant drives your phone" is rejection.

This is not legal advice. It is the checklist a Play submission would have to meet, based on Play's Accessibility, User Data, and Malware policies as described in the product scope.

## Why Play is a poor first channel

Play's Accessibility API policy says the API is not for an app that autonomously initiates, plans, and executes actions. An assistant that taps and types is close to that description. Reviewers have accepted supervised remote-control apps (the user is present, a persistent notice is showing, the user can stop the session). They do not have to accept an agent product. Treat approval as uncertain.

## `isAccessibilityTool` is false

That flag is only for apps whose core purpose is helping people with disabilities. Assistants and automation tools are called out as not qualifying. `pony_accessibility.xml` sets `android:isAccessibilityTool="false"`. Setting it to true to skip the restricted-settings step would be a policy violation. On Android 13+ the owner still has to allow restricted settings before turning Pony control on. Banking and OTP apps can hide `accessibilityDataSensitive` content from this service.

## What a submission would need

- A Play Console accessibility declaration, with a demo video of the real flow: disclosure, user turns the service on, user starts a session, user stops it.
- The Play listing describes the Accessibility API use in plain language. The same description has to match the in-app disclosure.
- A prominent in-app disclosure in the normal flow, separate from the privacy policy and from other dialogs, with its own affirmative action. The consent screen in this app is that disclosure. Do not bury it, and do not pre-check the box.
- A persistent notification for the whole time the service can act, with a way to stop. The session notification and the Disconnect button are that control. There is no always-on mode.
- No use of the API to click through system permission dialogs, the screen-capture dialog, or the restricted-settings dialog. The app must not do that in a later revision.
- `QUERY_ALL_PACKAGES` is declared so the app can open an app by package name. Play requires a separate declaration for that permission. If Play will not grant it, drop the permission and accept that `open_app` only sees packages that are visible without it.
- Foreground service types `mediaProjection` and `specialUse` need the corresponding Play declarations. `specialUse` needs a written justification. The manifest subtype string is the start of that text.
- A privacy policy URL that matches the disclosure: what is read (UI tree, screenshots), where it goes (the user's own paired computer, end-to-end encrypted through a relay that cannot read it), and how long the phone keeps the action log.
- Accountable signing, and no dynamic code loading.

## What this beta already does that a review would look for

The user reads what the assistant can see and do, checks a box, and only then can turn on accessibility. Each session needs a fresh screen-capture grant. A notification with Disconnect stays up. Sessions end after 30 minutes. Password fields are omitted from the tree and refused for typing.

## What a Play build would still have to add

Supervised confirmation before risky actions (send, pay, delete, install), a per-app allow list, and copy that describes the product as user-started remote assist rather than an autonomous agent. Even then, budget time for rejection and appeal. Direct distribution does not wait on that.
