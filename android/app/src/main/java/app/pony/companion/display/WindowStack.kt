package app.pony.companion.display

/**
 * Picks which on-screen window a tap, a type, or a tree read should use. On the
 * main display, Settings, the Google app, and Gboard can open as freeform
 * pop-ups that are **not** the active accessibility window, so Pony must target
 * the topmost one (highest layer) and never the app underneath it. Pony's own
 * overlays (the orb and the status pill) are never targets. Pure data in, so the
 * choice runs on the JVM.
 */
object WindowStack {
    /** Windows Pony may read or touch: everything except its own overlays. */
    fun targetable(windows: List<WindowShot>): List<WindowShot> =
        windows.filter { it.type != WinType.ACCESSIBILITY_OVERLAY && it.type != WinType.MAGNIFICATION }

    /** Targetable windows, topmost (highest layer, then focused) first. */
    fun ordered(windows: List<WindowShot>): List<WindowShot> =
        targetable(windows).sortedWith(
            compareByDescending<WindowShot> { it.layer }.thenByDescending { it.focused },
        )

    /** The topmost targetable window under (x, y), or null when nothing covers it. */
    fun topmostAt(x: Int, y: Int, windows: List<WindowShot>): WindowShot? =
        targetable(windows)
            .filter { it.contains(x, y) }
            .maxWithOrNull(compareBy({ it.layer }, { if (it.focused) 1 else 0 }))

    /**
     * The window a tree read should lead with: the focused window, else the
     * topmost application window, else the topmost of anything targetable.
     */
    fun leadWindow(windows: List<WindowShot>): WindowShot? {
        val targets = targetable(windows)
        return targets.filter { it.focused }.maxByOrNull { it.layer }
            ?: targets.filter { it.type == WinType.APPLICATION }.maxByOrNull { it.layer }
            ?: targets.maxByOrNull { it.layer }
    }
}
