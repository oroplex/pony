package app.pony.companion.display

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Display
import app.pony.companion.a11y.PonyAccessibilityService
import app.pony.companion.a11y.RetryLadder
import app.pony.companion.a11y.TapStrategy
import app.pony.companion.a11y.TextEntry
import app.pony.companion.intent.ImeSettings
import app.pony.companion.intent.SettingsIntents
import org.json.JSONObject

data class ActionResult(
    val ok: Boolean,
    val error: String? = null,
    val target: ScreenTarget,
    val fields: Map<String, Any?> = emptyMap(),
) {
    /** Wire fields only. Raw image bytes travel separately as base64, never as a field. */
    fun json(): JSONObject {
        val body = JSONObject()
        body.put("display", target.name)
        if (target.warn != null) body.put("warn", target.warn)
        if (target.warnText != null) body.put("warnText", target.warnText)
        for ((key, value) in wireFields()) body.put(key, value)
        return body
    }

    fun wireFields(): Map<String, Any> = fields
        .filterValues { it != null && it !is ByteArray && it !is android.graphics.Bitmap }
        .mapValues { it.value!! }
}

/**
 * Sends screen actions to the display the phone toggle (or the command)
 * selected. On the main screen, touches wait for a call screen to leave and
 * for pop-ups to move; they never land on the call UI or a heads-up.
 */
object ScreenRouter {
    @Volatile
    private var expected: String? = null

    /** Names that open the current keyboard's own settings, not the system picker list. */
    private val IME_DIRECT = setOf("keyboard_settings", "ime_settings", "ime", "current_keyboard", "keyboard_options")

    /** How long a retried tap waits for a slow screen to settle before judging whether it changed. */
    private const val RETRY_SETTLE_MS = 1_600L

    fun tap(context: Context, x: Double, y: Double, params: JSONObject?, watch: Watch = Watch.None): ActionResult {
        return work(context, "Tapping") {
            val target = targetFor(context, params)
            val a11y = PonyAccessibilityService.instance ?: return@work off(target)
            val px = axis(x, target.width)
            val py = axis(y, target.height)
            val guard = guardTouch(context, a11y, target, px, py, watch)
            if (!guard.ok) return@work guard.failure(target, mapOf("x" to px.toDouble(), "y" to py.toDouble()))
            val ok = a11y.tap(px, py, target.displayId)
            ActionResult(
                ok,
                if (ok) null else "tap_failed",
                target,
                mapOf("x" to px.toDouble(), "y" to py.toDouble()) + guard.extra() + windowFields(a11y, target, px, py),
            )
        }
    }

    /**
     * A tap that tries to actually land. It taps where the model asked; if the
     * point sat on a real control, that's taken as landed. Otherwise — only when
     * the screen didn't change — it escalates through [RetryLadder]: the control
     * clicking itself, a fresh gesture, scrolling the target into view and
     * clicking it, then a search for the control by [label]. Capped, so a stuck
     * screen reports a clean miss instead of looping or double-acting.
     */
    fun tapReliably(context: Context, x: Double, y: Double, label: String, params: JSONObject?, watch: Watch = Watch.None): ActionResult {
        return work(context, "Tapping") {
            val target = targetFor(context, params)
            val a11y = PonyAccessibilityService.instance ?: return@work off(target)
            val px = axis(x, target.width)
            val py = axis(y, target.height)
            val guard = guardTouch(context, a11y, target, px, py, watch)
            if (!guard.ok) return@work guard.failure(target, mapOf("x" to px.toDouble(), "y" to py.toDouble()))
            val display = target.displayId
            val onClickable = a11y.clickableAt(px, py, display)
            val before = if (onClickable) "" else a11y.screenSignature(display)
            a11y.tap(px, py, display)
            var via = "gesture"
            var landed = onClickable
            if (!landed) {
                a11y.waitUntilIdle(display, RETRY_SETTLE_MS)
                landed = RetryLadder.landedFirstTap(a11y.screenSignature(display) != before, onClickable = false)
                if (!landed) {
                    for (strategy in RetryLadder.plan(label.isNotBlank())) {
                        val mark = a11y.screenSignature(display)
                        val worked = when (strategy) {
                            TapStrategy.A11yClick -> a11y.clickAt(px, py, display)
                            TapStrategy.Gesture -> {
                                a11y.tap(px, py, display)
                                a11y.waitUntilIdle(display, RETRY_SETTLE_MS)
                                a11y.screenSignature(display) != mark
                            }
                            TapStrategy.ScrollIntoView -> {
                                val scrolled = a11y.scrollToward(px, py, display)
                                if (scrolled) a11y.waitUntilIdle(display, RETRY_SETTLE_MS)
                                scrolled && a11y.clickAt(px, py, display)
                            }
                            TapStrategy.SearchPath -> a11y.pressControl(listOf(label), emptyList()) != null
                        }
                        if (worked) {
                            via = strategy.wire
                            landed = true
                            break
                        }
                    }
                }
            }
            val ok = landed
            ActionResult(
                ok,
                if (ok) null else "tap_failed",
                target,
                mapOf("x" to px.toDouble(), "y" to py.toDouble(), "via" to via, "retried" to !onClickable) +
                    guard.extra() + windowFields(a11y, target, px, py),
            )
        }
    }


    fun swipe(
        context: Context,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        durationMs: Long,
        params: JSONObject?,
        watch: Watch = Watch.None,
    ): ActionResult {
        return work(context, "Swiping") {
            val target = targetFor(context, params)
            val a11y = PonyAccessibilityService.instance ?: return@work off(target)
            val sx = axis(x1, target.width)
            val sy = axis(y1, target.height)
            val guard = guardTouch(context, a11y, target, sx, sy, watch)
            if (!guard.ok) return@work guard.failure(target)
            val ok = a11y.swipe(sx, sy, axis(x2, target.width), axis(y2, target.height), durationMs, target.displayId)
            ActionResult(ok, if (ok) null else "swipe_failed", target, guard.extra() + windowFields(a11y, target, sx, sy))
        }
    }

    fun longPress(
        context: Context,
        x: Double,
        y: Double,
        durationMs: Long,
        params: JSONObject?,
        watch: Watch = Watch.None,
    ): ActionResult {
        return work(context, "Pressing and holding") {
            val target = targetFor(context, params)
            val a11y = PonyAccessibilityService.instance ?: return@work off(target)
            val px = axis(x, target.width)
            val py = axis(y, target.height)
            val guard = guardTouch(context, a11y, target, px, py, watch)
            if (!guard.ok) return@work guard.failure(target, mapOf("x" to px.toDouble(), "y" to py.toDouble()))
            val ok = a11y.longPress(px, py, durationMs, target.displayId)
            ActionResult(
                ok,
                if (ok) null else "gesture_failed",
                target,
                mapOf("x" to px.toDouble(), "y" to py.toDouble()) + guard.extra() + windowFields(a11y, target, px, py),
            )
        }
    }

    fun drag(
        context: Context,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        durationMs: Long,
        params: JSONObject?,
        watch: Watch = Watch.None,
    ): ActionResult {
        return work(context, "Dragging") {
            val target = targetFor(context, params)
            val a11y = PonyAccessibilityService.instance ?: return@work off(target)
            val sx = axis(x1, target.width)
            val sy = axis(y1, target.height)
            val guard = guardTouch(context, a11y, target, sx, sy, watch)
            if (!guard.ok) return@work guard.failure(target)
            val ok = a11y.drag(sx, sy, axis(x2, target.width), axis(y2, target.height), durationMs, target.displayId)
            ActionResult(ok, if (ok) null else "gesture_failed", target, guard.extra() + windowFields(a11y, target, sx, sy))
        }
    }

    fun pinch(
        context: Context,
        x: Double,
        y: Double,
        fromDistance: Double,
        toDistance: Double,
        durationMs: Long,
        params: JSONObject?,
        watch: Watch = Watch.None,
    ): ActionResult {
        return work(context, "Pinching") {
            val target = targetFor(context, params)
            val a11y = PonyAccessibilityService.instance ?: return@work off(target)
            val cx = axis(x, target.width)
            val cy = axis(y, target.height)
            val guard = guardTouch(context, a11y, target, cx, cy, watch)
            if (!guard.ok) return@work guard.failure(target)
            val ok = a11y.pinch(cx, cy, axis(fromDistance, target.height), axis(toDistance, target.height), durationMs, target.displayId)
            ActionResult(ok, if (ok) null else "gesture_failed", target, guard.extra() + windowFields(a11y, target, cx, cy))
        }
    }

    fun type(context: Context, text: String, append: Boolean, params: JSONObject?, watch: Watch = Watch.None): ActionResult {
        return work(context, "Typing") {
            val target = targetFor(context, params)
            val a11y = PonyAccessibilityService.instance ?: return@work off(target)
            val guard = guardTouch(context, a11y, target, null, null, watch)
            if (!guard.ok) return@work guard.failure(target)
            val typed = a11y.typeText(text, append, target.displayId)
            typed.fold(
                onSuccess = {
                    val fields = mutableMapOf<String, Any?>("length" to it.length, "method" to it.method)
                    fields += guard.extra()
                    val warned = if (it.method == TextEntry.METHOD_KEY_EVENTS) {
                        target.copy(warn = target.warn ?: "key_events", warnText = TextEntry.KEY_EVENT_WARNING)
                    } else {
                        target
                    }
                    ActionResult(true, null, warned, fields)
                },
                onFailure = { ActionResult(false, it.message ?: "type_failed", target, guard.extra()) },
            )
        }
    }

    fun press(context: Context, key: String, params: JSONObject?, watch: Watch = Watch.None): ActionResult {
        return work(context, "Pressing $key") {
            val target = targetFor(context, params)
            if (target.displayId != Display.DEFAULT_DISPLAY) {
                val code = when (key) {
                    "back" -> 4
                    "home" -> 3
                    "recents" -> 187
                    "enter" -> 66
                    "search" -> 84
                    else -> return@work ActionResult(false, "unknown_key", target, mapOf("key" to key))
                }
                val ok = ShizukuBridge.pressKey(context, target.displayId, code)
                val error = if (ok) null else DisplayPolicy.KEY_NEEDS_SHELL
                return@work ActionResult(ok, error, target, mapOf("key" to key))
            }
            val a11y = PonyAccessibilityService.instance ?: return@work off(target)
            val guard = guardTouch(context, a11y, target, null, null, watch)
            if (!guard.ok) return@work guard.failure(target, mapOf("key" to key))
            val ok = a11y.press(key)
            ActionResult(ok, if (ok) null else "unknown_key", target, mapOf("key" to key) + guard.extra())
        }
    }

    fun open(
        context: Context,
        packageName: String,
        params: JSONObject?,
        consent: (String) -> Boolean,
        watch: Watch = Watch.None,
    ): ActionResult {
        return work(context, "Opening an app") {
            val choice = DisplayPolicy.choiceFrom(context, params)
            val opened = BackgroundHost.open(
                context,
                packageName,
                choice,
                consent,
                explicitBackground = DisplayPolicy.requestedBackground(params),
                beforeMain = { ScreenGuard.awaitCallScreen(context, watch) },
            )
            if (opened.ok && opened.target.displayId == Display.DEFAULT_DISPLAY) expected = packageName
            val fields = mutableMapOf<String, Any?>("packageName" to packageName, "app" to BackgroundHost.appLabel(context, packageName))
            if (opened.deferredMs > 0) fields["deferredMs"] = opened.deferredMs
            ActionResult(opened.ok, opened.error, opened.target, fields)
        }
    }

    fun openSettings(
        context: Context,
        name: String,
        pkg: String?,
        params: JSONObject?,
        consent: (String) -> Boolean,
        watch: Watch = Watch.None,
    ): ActionResult {
        return work(context, "Opening settings") {
            val target = targetFor(context, params)
            val intent = settingsIntentFor(context, name, pkg)
                ?: return@work ActionResult(false, "unknown_settings", target, mapOf("settings" to name))
            val resolvedPkg = resolveSettingsPackage(context, intent)
            val opened = BackgroundHost.openIntent(
                context,
                intent,
                resolvedPkg,
                DisplayPolicy.choiceFrom(context, params),
                consent,
                explicitBackground = DisplayPolicy.requestedBackground(params),
                beforeMain = { ScreenGuard.awaitCallScreen(context, watch) },
            )
            if (opened.ok && opened.target.displayId == Display.DEFAULT_DISPLAY) expected = resolvedPkg
            val fields = mutableMapOf<String, Any?>("settings" to name)
            if (opened.deferredMs > 0) fields["deferredMs"] = opened.deferredMs
            ActionResult(opened.ok, opened.error, opened.target, fields)
        }
    }

    /** The current keyboard's own settings for an IME name, else a named/raw settings screen. */
    private fun settingsIntentFor(context: Context, name: String, pkg: String?): Intent? {
        val key = name.trim().lowercase().replace(' ', '_').replace('-', '_')
        if (key in IME_DIRECT) {
            ImeSettings.settingsIntent(context)?.let { return it }
        }
        val target = SettingsIntents.resolve(name, pkg)
            ?: (if (key in IME_DIRECT) SettingsIntents.resolve("input_method") else null)
            ?: return null
        val intent = Intent(target.action)
        target.data?.let { intent.data = Uri.parse(it) }
        return intent
    }

    private fun resolveSettingsPackage(context: Context, intent: Intent): String =
        intent.component?.packageName
            ?: runCatching { context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName }.getOrNull()
            ?: "com.android.settings"

    fun tree(context: Context, params: JSONObject?): ActionResult {
        return work(context, "Reading the screen") {
            val target = targetFor(context, params)
            val a11y = PonyAccessibilityService.instance ?: return@work off(target)
            val hidden = if (target.displayId == Display.DEFAULT_DISPLAY) CallGuard.protectedPackages(context) else emptySet()
            val text = a11y.uiTree(target.displayId, hidden)
            if (target.displayId == Display.DEFAULT_DISPLAY) expected = a11y.foregroundPackage(target.displayId) ?: expected
            ActionResult(true, null, target, mapOf("tree" to text) + windowFields(a11y, target, null, null))
        }
    }

    fun shot(context: Context, params: JSONObject?): ActionResult {
        return work(context, "Looking at the screen") {
            val target = targetFor(context, params)
            val a11y = PonyAccessibilityService.instance ?: return@work off(target)
            val jpeg = a11y.screenshotJpeg(target.displayId).getOrNull()
            if (target.displayId == Display.DEFAULT_DISPLAY) expected = a11y.foregroundPackage(target.displayId) ?: expected
            if (jpeg == null || jpeg.isEmpty()) {
                if (target.displayId != Display.DEFAULT_DISPLAY) {
                    // The hidden display exists but nothing has been launched on it, so the
                    // capture is blank. Say that plainly instead of a bare screenshot_failed.
                    ActionResult(false, DisplayPolicy.BACKGROUND_EMPTY, target.copy(warn = DisplayPolicy.BACKGROUND_EMPTY, warnText = DisplayPolicy.BACKGROUND_EMPTY_HINT))
                } else {
                    ActionResult(false, "screenshot_failed", target.copy(warn = "secure_window", warnText = DisplayPolicy.SECURE_HINT))
                }
            } else {
                ActionResult(
                    true,
                    null,
                    target,
                    mapOf(
                        "jpeg" to jpeg,
                        "width" to target.width,
                        "height" to target.height,
                    ),
                )
            }
        }
    }

    fun waitIdle(context: Context, timeoutMs: Long, params: JSONObject?): ActionResult {
        return work(context, "Waiting for the screen to settle") {
            val target = targetFor(context, params)
            val a11y = PonyAccessibilityService.instance ?: return@work off(target)
            val idle = a11y.waitUntilIdle(target.displayId, timeoutMs.coerceIn(500L, 15_000L))
            ActionResult(
                true,
                null,
                target,
                mapOf("settled" to idle.settled, "waitedMs" to idle.waitedMs, "reason" to idle.reason),
            )
        }
    }

    fun labelAt(context: Context, x: Double, y: Double, params: JSONObject?): String {
        val target = targetFor(context, params)
        val a11y = PonyAccessibilityService.instance ?: return ""
        return a11y.labelAt(axis(x, target.width), axis(y, target.height), target.displayId)
    }

    fun tapTarget(context: Context, x: Double, y: Double, params: JSONObject?): app.pony.companion.voice.TapTarget {
        val target = targetFor(context, params)
        val a11y = PonyAccessibilityService.instance ?: return app.pony.companion.voice.TapTarget("")
        return a11y.tapTarget(axis(x, target.width), axis(y, target.height), target.displayId)
    }

    fun targetFor(context: Context, params: JSONObject?): ScreenTarget {
        return BackgroundHost.resolve(context, DisplayPolicy.choiceFrom(context, params))
    }

    fun expectPackage(packageName: String?) {
        expected = packageName
    }

    private data class Guarded(val ok: Boolean, val error: String? = null, val deferredMs: Long = 0L, val coveredBy: String? = null, val waitedMs: Long = 0L) {
        fun extra(): Map<String, Any?> = buildMap {
            if (deferredMs > 0) put("deferredMs", deferredMs)
            if (waitedMs > 0) put("waitedMs", waitedMs)
        }

        fun failure(target: ScreenTarget, fields: Map<String, Any?> = emptyMap()): ActionResult =
            ActionResult(false, error, target, fields + extra() + mapOf("coveredBy" to coveredBy))
    }

    private fun guardTouch(
        context: Context,
        a11y: PonyAccessibilityService,
        target: ScreenTarget,
        x: Float?,
        y: Float?,
        watch: Watch,
    ): Guarded {
        if (target.displayId != Display.DEFAULT_DISPLAY) return Guarded(true)
        fun cover(): Cover {
            if (x == null || y == null) return Cover.Clear
            val ctx = CoverCheck.Context(
                ownPackage = context.packageName,
                statusBarHeight = a11y.statusBarHeight(),
                navBarHeight = a11y.navBarHeight(),
                screenHeight = target.height,
                expectedPackage = expected,
                protectedPackages = CallGuard.protectedPackages(context),
            )
            return CoverCheck.at(x.toInt(), y.toInt(), a11y.windowShots(Display.DEFAULT_DISPLAY), ctx)
        }
        val call = ScreenGuard.awaitCallScreen(context, watch) {
            CallGuard.callScreenInFront(context) || cover() is Cover.CallUi
        }
        if (!call.ok) return Guarded(false, call.error, deferredMs = call.waitedMs)
        val covered = ScreenGuard.awaitUncovered(watch) { cover() }
        return when (val c = covered.cover) {
            is Cover.Popup -> Guarded(false, DisplayPolicy.COVERED_BY_POPUP, call.waitedMs, c.packageName, covered.waitedMs)
            is Cover.CallUi -> Guarded(false, DisplayPolicy.CALL_UI_FOREGROUND, call.waitedMs, c.packageName, covered.waitedMs)
            Cover.Clear -> Guarded(true, deferredMs = call.waitedMs, waitedMs = covered.waitedMs)
        }
    }

    private fun off(target: ScreenTarget) = ActionResult(false, "accessibility_off", target)

    /** The window a touch lands in, or a read leads with, as "l,t,r,b" plus its package — so a moving pop-up is reported per call. */
    private fun windowFields(a11y: PonyAccessibilityService, target: ScreenTarget, x: Float?, y: Float?): Map<String, Any?> {
        if (target.displayId != Display.DEFAULT_DISPLAY) return emptyMap()
        val shot = if (x != null && y != null) a11y.windowAt(x, y, target.displayId) else a11y.leadWindow(target.displayId)
        shot ?: return emptyMap()
        return mapOf(
            "window" to "${shot.left},${shot.top},${shot.right},${shot.bottom}",
            "windowPkg" to shot.packageName,
        )
    }

    private fun axis(value: Double, screen: Int): Float {
        return if (value in 0.0..1.0) (value * screen).toFloat() else value.toFloat()
    }

    private fun work(context: Context, label: String, block: () -> ActionResult): ActionResult {
        BackgroundNotifier.begin(context, label)
        return try {
            block()
        } finally {
            BackgroundNotifier.end(context)
        }
    }
}
