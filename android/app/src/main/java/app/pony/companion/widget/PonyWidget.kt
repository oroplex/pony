package app.pony.companion.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import app.pony.companion.R
import app.pony.companion.voice.VoiceLaunch

/**
 * The home-screen "Ask Pony" pill: a mic and a short ask box. Tapping either
 * starts a voice ask without opening the app — the pill and the mic both fire the
 * same launch that brings up listening and gets out of the way.
 */
class PonyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, render(context))
    }

    private fun render(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_ask)
        val pending = VoiceLaunch.pending(context, VoiceLaunch.SOURCE_WIDGET)
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        views.setOnClickPendingIntent(R.id.widget_mic, pending)
        return views
    }
}
