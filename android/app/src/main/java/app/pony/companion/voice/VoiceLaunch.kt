package app.pony.companion.voice

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Starts a voice ask from outside the app — the home-screen widget and the Quick
 * Settings tile — by bouncing through [AskLaunchActivity], which brings Pony to
 * the front just long enough for the microphone to be allowed, starts listening,
 * and then gets out of the way. The full app UI never opens.
 */
object VoiceLaunch {
    const val EXTRA_SOURCE = "source"
    const val SOURCE_WIDGET = "widget"
    const val SOURCE_TILE = "qs_tile"
    const val ACTION = "app.pony.companion.action.ASK_VOICE"

    private val SOURCES = setOf(SOURCE_WIDGET, SOURCE_TILE)

    /** Keep a known source; fall back to the widget label for anything unexpected. */
    fun source(raw: String?): String = raw?.takeIf { it in SOURCES } ?: SOURCE_WIDGET

    fun intent(context: Context, source: String): Intent =
        Intent(context, AskLaunchActivity::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_SOURCE, source(source))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** A PendingIntent the widget's pill and the tile fire to start listening. */
    fun pending(context: Context, source: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            source(source).hashCode(),
            intent(context, source),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
