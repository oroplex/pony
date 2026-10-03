package app.pony.companion.display

/**
 * Pure helpers for the Shizuku shell display path, kept free of Android types so
 * they unit test. The shell user is the only caller that can create a trusted
 * display, which is the only kind that hosts other apps' activities. On an
 * untrusted display Android refuses every third-party activity no matter the
 * intent flags, so the app process checks [LaunchCheck.landedOnHidden] and falls
 * back to the owner's screen instead of pretending the launch worked.
 */
object ShellLaunch {
    // VirtualDisplay flags, mirrored from DisplayManager so the service can pass
    // them straight through without importing hidden constants.
    const val PUBLIC = 1 shl 0
    const val PRESENTATION = 1 shl 1
    const val CAN_SHOW_WITH_INSECURE_KEYGUARD = 1 shl 5
    const val SUPPORTS_TOUCH = 1 shl 6
    const val ROTATES = 1 shl 7
    const val DESTROY_ON_REMOVAL = 1 shl 8
    const val DECORATIONS = 1 shl 9
    const val TRUSTED = 1 shl 10
    const val OWN_DISPLAY_GROUP = 1 shl 11
    const val ALWAYS_UNLOCKED = 1 shl 12
    const val OWN_FOCUS = 1 shl 14
    const val DEVICE_DISPLAY_GROUP = 1 shl 16

    /** Fullscreen. Forces the app to fill the hidden display instead of a small floating window. */
    const val WINDOWING_FULLSCREEN = 1

    /** Show the IME on this display, not on the owner's screen. `wm set-display-ime-policy`. */
    const val IME_POLICY_LOCAL = 0

    /** First Android version that needs the isolated-group trusted-display flags. */
    const val ANDROID_17 = 37

    /**
     * System decorations only work on a trusted display, so they ride with the
     * trusted and public flags. A plain presentation display never shows home,
     * the nav bar, or other apps.
     *
     * Android 17 / One UI 9 stopped treating a trusted virtual display in the
     * *default* display group as a valid host for foreign activities: Settings,
     * Keep, Chrome and the rest bounce to a freeform window on the main screen.
     * Isolating the display (`OWN_DISPLAY_GROUP` + `ALWAYS_UNLOCKED`) is the
     * scrcpy Android 15+ path and restores the hidden screen. Android 11–16
     * keep the original flag set.
     */
    fun displayFlags(trusted: Boolean, sdk: Int = 33): Int {
        var flags = PRESENTATION or SUPPORTS_TOUCH or ROTATES or DESTROY_ON_REMOVAL or OWN_FOCUS
        if (trusted) {
            flags = flags or TRUSTED or PUBLIC or DECORATIONS
            if (sdk >= ANDROID_17) {
                flags = flags or OWN_DISPLAY_GROUP or ALWAYS_UNLOCKED or CAN_SHOW_WITH_INSECURE_KEYGUARD
            }
        }
        return flags
    }

    /**
     * Android 17 tries the isolated-group flags first, then the Android 16
     * combo, so a phone that rejects `DEVICE_DISPLAY_GROUP` still gets a
     * trusted display. Older releases have one attempt.
     */
    fun flagAttempts(trusted: Boolean, sdk: Int): List<Int> {
        if (!trusted || sdk < ANDROID_17) return listOf(displayFlags(trusted, sdk))
        val isolated = displayFlags(true, sdk)
        return listOf(isolated or DEVICE_DISPLAY_GROUP, isolated, displayFlags(true, 36)).distinct()
    }

    /** `am start` onto a specific display, fullscreen, waiting so the exit code reflects the launch. */
    fun startArgs(component: String, displayId: Int, windowingMode: Int = WINDOWING_FULLSCREEN): Array<String> =
        arrayOf("am", "start", "-W", "--display", displayId.toString(), "--windowingMode", windowingMode.toString(), "-n", component)

    fun imePolicyArgs(displayId: Int, policy: Int = IME_POLICY_LOCAL): Array<String> =
        arrayOf("wm", "set-display-ime-policy", displayId.toString(), policy.toString())

    fun activitiesDumpArgs(): Array<String> = arrayOf("dumpsys", "activity", "activities")

    /**
     * Reads the top package on [displayId] from `dumpsys activity activities`.
     * Android 17 often hides virtual-display windows from accessibility, so
     * this is the landing check when a11y reports nothing.
     */
    fun parseTopPackage(dump: String?, displayId: Int): String? {
        if (dump.isNullOrBlank() || displayId < 0) return null
        val displayMark = Regex("""(?:mDisplayId|displayId)\s*=\s*$displayId\b""")
        val component = Regex("""([a-zA-Z0-9._]+)/[a-zA-Z0-9._/]+""")
        val blocks = dump.split(Regex("(?=ActivityRecord\\{)|(?=TaskRecord\\{)|(?=\\* Task)"))
        for (block in blocks) {
            if (!displayMark.containsMatchIn(block)) continue
            val match = component.find(block) ?: continue
            val pkg = match.groupValues[1]
            if (pkg.contains('.') && pkg != "android") return pkg
        }
        return null
    }

    /**
     * The developer toggle that lets non-resizable, portrait-locked apps open on
     * a secondary display. Without it the window manager keeps apps like food
     * delivery and camera apps on the main screen even when the display is trusted.
     */
    fun resizableArgs(on: Boolean): Array<String> =
        arrayOf("settings", "put", "global", "force_resizable_activities", if (on) "1" else "0")

    fun readResizableArgs(): Array<String> =
        arrayOf("settings", "get", "global", "force_resizable_activities")

    /** `settings get` prints `null` for an unset global; treat that and blanks as off. */
    fun resizableWasOn(read: String?): Boolean = read?.trim() == "1"
}

/** Decides whether an app actually reached the hidden display, or bounced back to the owner's screen. */
object LaunchCheck {
    enum class Landing { HIDDEN, BOUNCED, UNKNOWN }

    /**
     * [observed] is the top app on the hidden display after the launch. The app
     * landed only when its own window leads there: nothing yet, Pony's own UI, or
     * some other package all mean the system refused it and we must fall back.
     */
    fun landedOnHidden(expected: String?, observed: String?, ownPackage: String?): Boolean {
        if (observed.isNullOrBlank()) return false
        if (observed == ownPackage) return false
        return expected == null || observed == expected
    }

    /**
     * Android 17 / One UI 9 often omits virtual-display windows from
     * accessibility. An empty a11y read is then *not* a bounce — launching
     * again as a freeform window on the main screen is what the owner saw.
     * When a11y cannot see the display at all, a successful `am start -W`
     * is trusted. When the app is already leading the main screen and not
     * the hidden one, that is a real bounce.
     */
    fun decide(
        expected: String?,
        hiddenObserved: String?,
        mainObserved: String?,
        ownPackage: String?,
        launchOk: Boolean,
        hiddenDisplayVisibleToA11y: Boolean,
    ): Landing {
        if (landedOnHidden(expected, hiddenObserved, ownPackage)) return Landing.HIDDEN
        if (!expected.isNullOrBlank() && mainObserved == expected && hiddenObserved != expected) {
            return Landing.BOUNCED
        }
        if (hiddenDisplayVisibleToA11y) return Landing.BOUNCED
        return if (launchOk) Landing.HIDDEN else Landing.UNKNOWN
    }
}
