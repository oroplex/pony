package app.pony.companion.display

/** A window as Pony sees it: plain data, so the hit test runs on the JVM. */
data class WindowShot(
    val type: WinType,
    val layer: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val packageName: String?,
    val focused: Boolean = false,
) {
    val height: Int get() = bottom - top

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
}

enum class WinType { APPLICATION, INPUT_METHOD, SYSTEM, ACCESSIBILITY_OVERLAY, DIVIDER, MAGNIFICATION, OTHER }

sealed class Cover {
    data object Clear : Cover()

    /** A heads-up, the notification shade, a volume panel, or an alarm or timer screen. */
    data class Popup(val packageName: String?, val kind: String) : Cover()

    /** The call screen, a call bubble, or an incoming-call alert. Never tapped. */
    data class CallUi(val packageName: String?) : Cover()
}

/**
 * What a tap at (x, y) would actually hit. Pony waits for pop-ups to leave
 * instead of tapping them, and never taps a call window.
 */
object CoverCheck {
    val ALERT_PACKAGES = setOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.samsung.android.app.clockpack",
        "com.oneplus.deskclock",
        "com.miui.clock",
    )

    data class Context(
        val ownPackage: String,
        val statusBarHeight: Int,
        val navBarHeight: Int,
        val screenHeight: Int,
        val expectedPackage: String? = null,
        val protectedPackages: Set<String> = CallPolicy.CALL_UI_PACKAGES,
    )

    fun at(x: Int, y: Int, windows: List<WindowShot>, ctx: Context): Cover {
        val top = windows
            .filter { it.contains(x, y) }
            .filter { it.type != WinType.ACCESSIBILITY_OVERLAY && it.type != WinType.MAGNIFICATION }
            .maxByOrNull { it.layer }
            ?: return Cover.Clear
        val pkg = top.packageName
        if (pkg != null && pkg in ctx.protectedPackages) return Cover.CallUi(pkg)
        return when (top.type) {
            WinType.SYSTEM -> if (isBar(top, ctx)) Cover.Clear else Cover.Popup(pkg, "system")
            WinType.APPLICATION -> when {
                pkg != null && pkg in ALERT_PACKAGES && pkg != ctx.expectedPackage -> Cover.Popup(pkg, "alert")
                pkg == ctx.ownPackage && ctx.expectedPackage != null && ctx.expectedPackage != ctx.ownPackage ->
                    Cover.Popup(pkg, "pony")
                else -> Cover.Clear
            }
            else -> Cover.Clear
        }
    }

    /** The status bar and navigation bar are always there; only something taller is a pop-up. */
    private fun isBar(window: WindowShot, ctx: Context): Boolean {
        val statusLimit = (ctx.statusBarHeight * 1.6f).toInt().coerceAtLeast(ctx.statusBarHeight + 8)
        val navLimit = (ctx.navBarHeight * 1.6f).toInt().coerceAtLeast(ctx.navBarHeight + 8)
        val atTop = window.top <= 0 && window.height <= statusLimit
        val atBottom = window.bottom >= ctx.screenHeight && window.height <= navLimit
        return atTop || atBottom
    }
}
