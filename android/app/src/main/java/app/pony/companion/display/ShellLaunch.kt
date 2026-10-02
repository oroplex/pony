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
    const val SUPPORTS_TOUCH = 1 shl 6
    const val ROTATES = 1 shl 7
    const val DESTROY_ON_REMOVAL = 1 shl 8
    const val DECORATIONS = 1 shl 9
    const val TRUSTED = 1 shl 10
    const val OWN_FOCUS = 1 shl 14

    /** Fullscreen. Forces the app to fill the hidden display instead of a small floating window. */
    const val WINDOWING_FULLSCREEN = 1

    /**
     * System decorations only work on a trusted display, so they ride with the
     * trusted and public flags. A plain presentation display never shows home,
     * the nav bar, or other apps.
     */
    fun displayFlags(trusted: Boolean): Int {
        var flags = PRESENTATION or SUPPORTS_TOUCH or ROTATES or DESTROY_ON_REMOVAL or OWN_FOCUS
        if (trusted) flags = flags or TRUSTED or PUBLIC or DECORATIONS
        return flags
    }

    /** `am start` onto a specific display, fullscreen, waiting so the exit code reflects the launch. */
    fun startArgs(component: String, displayId: Int, windowingMode: Int = WINDOWING_FULLSCREEN): Array<String> =
        arrayOf("am", "start", "-W", "--display", displayId.toString(), "--windowingMode", windowingMode.toString(), "-n", component)

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
}
