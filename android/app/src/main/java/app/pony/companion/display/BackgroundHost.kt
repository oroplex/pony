package app.pony.companion.display

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import app.pony.companion.a11y.PonyAccessibilityService
import app.pony.companion.voice.VoicePrefs
import java.util.concurrent.ConcurrentHashMap

data class ScreenTarget(
    val name: String,
    val displayId: Int,
    val width: Int,
    val height: Int,
    val warn: String?,
    val warnText: String? = null,
)

data class OpenResult(val ok: Boolean, val error: String?, val target: ScreenTarget, val deferredMs: Long = 0L)

/**
 * Owns the secondary display and reports honestly where an app ended up.
 * When an app can't run out of sight, Pony asks the owner before using the
 * main screen, and every later command says "freeform" or "main" — never
 * "background" — until an app opens on a real background display again.
 */
object BackgroundHost {
    private var own: VirtualDisplayHost? = null

    data class Fallback(val placement: String, val warn: String, val warnText: String, val packageName: String)

    @Volatile
    var fallback: Fallback? = null
        private set

    private val approved = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var approvedAll = false

    fun release(context: Context) {
        own?.release()
        own = null
        fallback = null
        runCatching { ShizukuBridge.release(context) }
        BackgroundNotifier.hide(context)
    }

    /** A new session starts with no remembered main-screen approvals. */
    fun resetSession() {
        approved.clear()
        approvedAll = false
        fallback = null
    }

    fun resolve(context: Context, choice: DisplayPolicy.Choice, explicitBackground: Boolean = false): ScreenTarget {
        val (width, height) = DisplayPolicy.screenSize(context)
        if (choice == DisplayPolicy.Choice.MAIN) return ScreenTarget("main", Display.DEFAULT_DISPLAY, width, height, null)
        // A bounced open_app keeps later taps on the real screen, but the warning
        // is per-action — Home must not still say "Keep Notes can't run…". An
        // explicit background ask tries the hidden display again instead of
        // silently screenshotting the main screen.
        if (!explicitBackground) {
            fallback?.let { return ScreenTarget(it.placement, Display.DEFAULT_DISPLAY, width, height, null, null) }
        }
        if (VoicePrefs.shizukuEnhanced(context) && ShizukuBridge.granted()) {
            val id = ShizukuBridge.ensureDisplay(context, width, height, context.resources.displayMetrics.densityDpi)
            if (id > 0) return ScreenTarget("background", id, width, height, null)
        }
        val host = own ?: VirtualDisplayHost(context.applicationContext).also { own = it }
        val id = host.ensure(width, height, context.resources.displayMetrics.densityDpi)
        if (id != null && id != Display.DEFAULT_DISPLAY) {
            return ScreenTarget("background", id, width, height, null)
        }
        return ScreenTarget(
            "main",
            Display.DEFAULT_DISPLAY,
            width,
            height,
            DisplayPolicy.ON_MAIN,
            "Pony's hidden screen isn't available on this phone, so this ran on the main screen.",
        )
    }

    /**
     * Opens [packageName]. [beforeMain] runs right before anything lands on the
     * main screen (it waits out a call screen); [consent] asks the owner.
     */
    fun open(
        context: Context,
        packageName: String,
        choice: DisplayPolicy.Choice,
        consent: (String) -> Boolean,
        explicitBackground: Boolean = false,
        beforeMain: () -> ScreenGuard.Wait = { ScreenGuard.Wait(true, 0) },
    ): OpenResult {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return OpenResult(false, "app_not_found", main(context, null, null))
        return openIntent(context, launch, packageName, choice, consent, explicitBackground, beforeMain)
    }

    /**
     * Opens a prebuilt [intent] whose activity lives in [packageName]: onto the
     * hidden display when it will host there, and otherwise asking the owner
     * before it lands on the main screen — the same placement path [open] uses
     * for apps. Pony jumps straight to settings screens this way.
     */
    fun openIntent(
        context: Context,
        intent: Intent,
        packageName: String,
        choice: DisplayPolicy.Choice,
        consent: (String) -> Boolean,
        explicitBackground: Boolean = false,
        beforeMain: () -> ScreenGuard.Wait = { ScreenGuard.Wait(true, 0) },
    ): OpenResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (choice == DisplayPolicy.Choice.MAIN) {
            val wait = beforeMain()
            if (!wait.ok) return OpenResult(false, wait.error, main(context, null, null), wait.waitedMs)
            val ok = start(context, intent, null)
            return OpenResult(ok, if (ok) null else "app_not_found", main(context, null, null), wait.waitedMs)
        }
        if (VoicePrefs.shizukuEnhanced(context) && ShizukuBridge.granted()) {
            openWithShell(context, packageName, intent)?.let { return it }
        }
        val (width, height) = DisplayPolicy.screenSize(context)
        val host = own ?: VirtualDisplayHost(context.applicationContext).also { own = it }
        val displayId = host.ensure(width, height, context.resources.displayMetrics.densityDpi)
        if (displayId != null && allowed(context, displayId, intent)) {
            val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }.toBundle()
            if (start(context, intent, options) && landed(context, packageName, displayId, launchOk = true)) {
                fallback = null
                return OpenResult(true, null, ScreenTarget("background", displayId, width, height, null))
            }
        }
        val label = appLabel(context, packageName)
        if (!mayUseMain(context, packageName, label, consent)) {
            return OpenResult(
                false,
                DisplayPolicy.BACKGROUND_REFUSED,
                main(context, null, DisplayPolicy.refusedText(label)),
            )
        }
        val wait = beforeMain()
        if (!wait.ok) return OpenResult(false, wait.error, main(context, null, null), wait.waitedMs)
        // Full-screen on the main display — not a freeform / Samsung pop-up.
        // Freeform launch bounds were taller than the panel (y=3269 on a
        // 3120px S26 Ultra) and cut off the bottom toolbar.
        val ok = tryFullscreenMain(context, intent)
        if (ok) fallback = fallbackFor("main", label, packageName, explicitBackground)
        return OpenResult(
            ok,
            if (ok) null else "app_not_found",
            if (ok) main(context, fallback?.warn, fallback?.warnText) else main(context, null, null),
            wait.waitedMs,
        )
    }

    /**
     * How a main-screen landing is described. An explicit background request that
     * couldn't be met says so loudly ([DisplayPolicy.BACKGROUND_UNAVAILABLE]); the
     * phone's default toggle falling back keeps the quieter pop-up/main notice.
     */
    private fun fallbackFor(placement: String, label: String, packageName: String, explicitBackground: Boolean): Fallback {
        val freeform = placement == "freeform"
        val warn = when {
            explicitBackground -> DisplayPolicy.BACKGROUND_UNAVAILABLE
            freeform -> DisplayPolicy.POPUP_ON_MAIN
            else -> DisplayPolicy.ON_MAIN
        }
        val text = when {
            explicitBackground -> DisplayPolicy.unavailableText(label, if (freeform) "freeform" else "main")
            freeform -> DisplayPolicy.popupText(label)
            else -> DisplayPolicy.mainText(label)
        }
        return Fallback(if (freeform) "freeform" else "main", warn, text, packageName)
    }

    fun appLabel(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() })

    private fun mayUseMain(context: Context, packageName: String, label: String, consent: (String) -> Boolean): Boolean {
        if (approvedAll || packageName in approved) return true
        return when (VoicePrefs.mainScreenFallback(context)) {
            VoicePrefs.FALLBACK_ALLOW -> true.also { approvedAll = true }
            VoicePrefs.FALLBACK_NEVER -> false
            else -> consent(DisplayPolicy.consentText(label, popupsLikely(context)))
                .also { yes -> if (yes) { approved += packageName; approvedAll = true } }
        }
    }

    private fun openWithShell(context: Context, packageName: String, intent: Intent): OpenResult? {
        val (width, height) = DisplayPolicy.screenSize(context)
        val id = ShizukuBridge.ensureDisplay(context, width, height, context.resources.displayMetrics.densityDpi)
        if (id <= 0) return null
        // An untrusted shell display can't host another app: am start would bounce
        // it onto the main screen without the owner's say-so. Leave that to the
        // consent-gated full-screen path below.
        if (!ShizukuBridge.trusted(context)) return null
        runCatching { ShizukuBridge.setImePolicy(context, id) }
        // Use the intent's own component. Only fall back to the launcher activity
        // for a launcher intent — a settings-action intent must not be redirected
        // to com.android.settings' home screen.
        val component = intent.component?.flattenToShortString()
            ?: (if (isLauncherIntent(intent)) resolveComponent(context, packageName) else null)
            ?: return null
        val launched = ShizukuBridge.launch(context, component, id)
        if (!launched && !landed(context, packageName, id, launchOk = false)) return null
        if (!landed(context, packageName, id, launchOk = launched)) return null
        fallback = null
        return OpenResult(true, null, ScreenTarget("background", id, width, height, null))
    }

    private fun isLauncherIntent(intent: Intent): Boolean =
        intent.action == Intent.ACTION_MAIN && intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true

    /**
     * Waits briefly for [packageName] to actually lead the hidden display. A
     * refused app never arrives (it bounces to the main screen or fails), so this
     * returns false and the caller falls back instead of reporting "background".
     */
    private fun landed(context: Context, packageName: String, displayId: Int, launchOk: Boolean, timeoutMs: Long = 3_000L): Boolean {
        val a11y = PonyAccessibilityService.instance
        val own = context.packageName
        if (a11y == null) {
            val dumped = ShizukuBridge.topPackage(context, displayId)
            return LaunchCheck.decide(packageName, dumped, null, own, launchOk, hiddenDisplayVisibleToA11y = false) ==
                LaunchCheck.Landing.HIDDEN
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: LaunchCheck.Landing = LaunchCheck.Landing.UNKNOWN
        while (System.currentTimeMillis() < deadline) {
            val hidden = a11y.foregroundPackage(displayId) ?: ShizukuBridge.topPackage(context, displayId)
            val main = a11y.foregroundPackage(Display.DEFAULT_DISPLAY)
            val sees = a11y.seesDisplay(displayId)
            last = LaunchCheck.decide(packageName, hidden, main, own, launchOk, sees)
            if (last == LaunchCheck.Landing.HIDDEN) return true
            if (last == LaunchCheck.Landing.BOUNCED && sees && hidden != null && hidden != packageName) {
                // A11y can see a different app on the hidden display — stop waiting.
                break
            }
            try {
                Thread.sleep(150)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        if (last == LaunchCheck.Landing.HIDDEN) return true
        if (last == LaunchCheck.Landing.UNKNOWN && launchOk) return true
        return last == LaunchCheck.Landing.HIDDEN
    }

    private fun resolveComponent(context: Context, packageName: String): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val activity = info.activityInfo ?: return null
        return "$packageName/${activity.name}"
    }

    /** Samsung's pop-up view, or the platform freeform feature. Used only in the consent question. */
    private fun popupsLikely(context: Context): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            runCatching { Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1 }.getOrDefault(false)

    /** Full-screen on the owner's display. No freeform chrome, no Samsung pop-up extras. */
    private fun tryFullscreenMain(context: Context, launch: Intent): Boolean {
        val options = ActivityOptions.makeBasic()
        runCatching {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, ShellLaunch.WINDOWING_FULLSCREEN)
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launch.putExtra("android.activity.windowingMode", ShellLaunch.WINDOWING_FULLSCREEN)
        return start(context, launch, options.toBundle())
    }

    private fun allowed(context: Context, displayId: Int, intent: Intent): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        return try {
            manager.isActivityStartAllowedOnDisplay(context, displayId, intent)
        } catch (_: Throwable) {
            false
        }
    }

    private fun start(context: Context, intent: Intent, options: Bundle?): Boolean {
        val starter = PonyAccessibilityService.instance
        return try {
            if (starter != null) {
                starter.launch(intent, options)
            } else {
                context.startActivity(intent, options)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun main(context: Context, warn: String?, warnText: String?): ScreenTarget {
        val (width, height) = DisplayPolicy.screenSize(context)
        return ScreenTarget("main", Display.DEFAULT_DISPLAY, width, height, warn, warnText)
    }
}
