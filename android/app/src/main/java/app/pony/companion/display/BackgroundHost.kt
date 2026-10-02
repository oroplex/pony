package app.pony.companion.display

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
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

    fun resolve(context: Context, choice: DisplayPolicy.Choice): ScreenTarget {
        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        if (choice == DisplayPolicy.Choice.MAIN) return ScreenTarget("main", Display.DEFAULT_DISPLAY, width, height, null)
        fallback?.let { return ScreenTarget(it.placement, Display.DEFAULT_DISPLAY, width, height, it.warn, it.warnText) }
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
        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val host = own ?: VirtualDisplayHost(context.applicationContext).also { own = it }
        val displayId = host.ensure(width, height, context.resources.displayMetrics.densityDpi)
        if (displayId != null && allowed(context, displayId, intent)) {
            val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }.toBundle()
            if (start(context, intent, options) && landed(context, packageName, displayId)) {
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
        if (tryFreeform(context, intent)) {
            try {
                Thread.sleep(700)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            val placement = PonyAccessibilityService.instance?.placementOf(packageName) ?: "main"
            val next = fallbackFor(placement, label, packageName, explicitBackground)
            fallback = next
            return OpenResult(true, null, ScreenTarget(next.placement, Display.DEFAULT_DISPLAY, width, height, next.warn, next.warnText), wait.waitedMs)
        }
        val ok = start(context, intent, null)
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
        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val id = ShizukuBridge.ensureDisplay(context, width, height, context.resources.displayMetrics.densityDpi)
        if (id <= 0) return null
        // An untrusted shell display can't host another app: am start would bounce
        // it onto the main screen without the owner's say-so. Leave that to the
        // consent-gated pop-up path below.
        if (!ShizukuBridge.trusted(context)) return null
        // Use the intent's own component. Only fall back to the launcher activity
        // for a launcher intent — a settings-action intent must not be redirected
        // to com.android.settings' home screen.
        val component = intent.component?.flattenToShortString()
            ?: (if (isLauncherIntent(intent)) resolveComponent(context, packageName) else null)
            ?: return null
        if (!ShizukuBridge.launch(context, component, id)) return null
        if (!landed(context, packageName, id)) return null
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
    private fun landed(context: Context, packageName: String, displayId: Int, timeoutMs: Long = 3_000L): Boolean {
        val a11y = PonyAccessibilityService.instance ?: return true
        val own = context.packageName
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (LaunchCheck.landedOnHidden(packageName, a11y.foregroundPackage(displayId), own)) return true
            try {
                Thread.sleep(150)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    private fun resolveComponent(context: Context, packageName: String): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val activity = info.activityInfo ?: return null
        return "$packageName/${activity.name}"
    }

    /** Samsung's pop-up view, or the platform freeform feature. */
    private fun popupsLikely(context: Context): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            runCatching { Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1 }.getOrDefault(false)

    private fun tryFreeform(context: Context, launch: Intent): Boolean {
        val metrics = context.resources.displayMetrics
        // Open maximized (the whole screen), not a small pop-up the owner has to
        // maximize by hand. On OEMs with freeform windows this is a maximized
        // freeform window; elsewhere it fills the screen.
        val edges = DisplayPolicy.maximizedBounds(metrics.widthPixels, metrics.heightPixels)
        val bounds = Rect(edges[0], edges[1], edges[2], edges[3])
        val options = ActivityOptions.makeBasic()
        runCatching {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, 5)
        }
        runCatching {
            ActivityOptions::class.java
                .getMethod("setLaunchBounds", Rect::class.java)
                .invoke(options, bounds)
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        launch.putExtra("android.activity.windowingMode", 5)
        launch.putExtra("com.samsung.android.multiwindow.activity.LAUNCH_IN_POPUP", true)
        launch.putExtra("com.samsung.android.multiwindow.activity.LAUNCH_IN_POPUP_MAXIMIZE", true)
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
        val metrics = context.resources.displayMetrics
        return ScreenTarget("main", Display.DEFAULT_DISPLAY, metrics.widthPixels, metrics.heightPixels, warn, warnText)
    }
}
