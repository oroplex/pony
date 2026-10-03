package app.pony.companion.display

import android.content.Context
import android.os.Build
import android.view.WindowManager
import app.pony.companion.voice.VoicePrefs
import org.json.JSONObject

/**
 * Where a command should land. An explicit display name wins, then an explicit
 * background flag, then the phone toggle. A missing flag is not "off":
 * JSON callers must pass null when the key is absent, because optBoolean
 * defaults to false.
 */
object DisplayPolicy {
    const val SECURE_HINT =
        "This screen could not be captured. The app may block screenshots."
    const val BACKGROUND_EMPTY = "background_empty"
    const val BACKGROUND_EMPTY_HINT =
        "Nothing is on Pony's hidden screen yet. Open an app on it first, or read the main screen with display:'main'."
    const val KEY_NEEDS_SHELL = "background_key_needs_shell"
    const val TYPE_FAILED = "background_type_failed"

    const val CALL_UI_FOREGROUND = "call_ui_foreground"
    const val CALL_UI_TIMEOUT = "call_ui_timeout"
    const val COVERED_BY_POPUP = "covered_by_popup"
    const val BACKGROUND_REFUSED = "background_refused"

    /** warn codes. The matching sentence travels as warnText. */
    const val POPUP_ON_MAIN = "popup_on_main_screen"
    const val ON_MAIN = "main_screen"

    /**
     * The caller explicitly asked for the hidden screen (display:'background' or
     * background:true) but the phone couldn't host it there. This is louder than
     * the default fallback so the brain is never silently handed the main screen
     * after asking for background.
     */
    const val BACKGROUND_UNAVAILABLE = "background_unavailable"

    fun unavailableText(app: String, placement: String): String {
        val where = if (placement == "freeform") "in a pop-up on your screen" else "on your screen"
        return "You asked to keep $app on Pony's hidden screen, but this phone can't run it there, so it opened $where instead."
    }

    fun popupText(app: String) =
        "$app can't run on Pony's hidden screen on this phone, so it opened in a pop-up window on the main screen."

    fun mainText(app: String) =
        "$app can't run on Pony's hidden screen on this phone, so it opened on the main screen."

    fun refusedText(app: String) =
        "$app can't run on Pony's hidden screen. Banking and other secure apps often refuse it. The owner chose not to open it on the main screen."

    /**
     * A refused app falls back full-screen on the main display. Bounds are the
     * real panel, not the app window plus freeform chrome (which ran past
     * y=3269 on a 3120px Galaxy S26 Ultra).
     */
    fun maximizedBounds(width: Int, height: Int): IntArray =
        intArrayOf(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))

    /** Physical display size, so a fallback window matches the panel. */
    fun screenSize(widthPx: Int, heightPx: Int, realWidth: Int, realHeight: Int): IntArray {
        val w = realWidth.coerceAtLeast(widthPx).coerceAtLeast(1)
        val h = realHeight.coerceAtLeast(heightPx).coerceAtLeast(1)
        return intArrayOf(w, h)
    }

    fun screenSize(context: Context): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        val real = if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
                bounds.width() to bounds.height()
            }.getOrNull()
        } else {
            null
        }
        val sized = screenSize(metrics.widthPixels, metrics.heightPixels, real?.first ?: 0, real?.second ?: 0)
        return sized[0] to sized[1]
    }

    /** What the owner is asked. Fallback is always full-screen, never a freeform pop-up. */
    @Suppress("UNUSED_PARAMETER")
    fun consentText(app: String, popups: Boolean) =
        "$app can't open out of sight on this phone. Open it full screen on your screen instead? Pony will only ask once this session."

    enum class Choice { MAIN, BACKGROUND }

    fun choice(prefOn: Boolean, background: Boolean?, display: String?): Choice {
        when (display?.trim()?.lowercase()) {
            "main", "foreground" -> return Choice.MAIN
            "background" -> return Choice.BACKGROUND
        }
        if (background != null) return if (background) Choice.BACKGROUND else Choice.MAIN
        return if (prefOn) Choice.BACKGROUND else Choice.MAIN
    }

    fun choiceFrom(context: Context, params: JSONObject?): Choice {
        val body = params ?: JSONObject()
        val named = if (body.has("display")) body.optString("display") else null
        val background = if (body.has("background")) body.getBoolean("background") else null
        return choice(VoicePrefs.backgroundMode(context), background, named)
    }

    /**
     * True when the command itself asked for the hidden screen — display:'background'
     * or background:true — regardless of the phone's toggle. An explicit request that
     * can't be met is warned about loudly ([BACKGROUND_UNAVAILABLE]); the phone's
     * default toggle falling back is not.
     */
    fun requestedBackground(params: JSONObject?): Boolean {
        val body = params ?: return false
        when (if (body.has("display")) body.optString("display").trim().lowercase() else "") {
            "background" -> return true
            "main", "foreground" -> return false
        }
        return body.has("background") && body.optBoolean("background", false)
    }
}
