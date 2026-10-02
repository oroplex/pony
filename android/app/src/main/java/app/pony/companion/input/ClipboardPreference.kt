package app.pony.companion.input

import android.content.Context

/** Clipboard paste is off unless the owner turns it on in the app. */
object ClipboardPreference {
    private const val PREFS = "pony"
    const val KEY = "clipboard_paste"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, enabled)
            .apply()
    }
}
