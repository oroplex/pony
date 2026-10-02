package app.pony.companion.intent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * Opens the current keyboard's own settings screen — where Gboard keeps its
 * languages and voice typing — instead of the system "Select input method"
 * picker. Gboard keeps languages and voice typing behind its own settings, and
 * the picker list swallowed taps on some phones, so Pony lands on the real screen
 * directly via the IME's declared `settingsActivity`.
 */
object ImeSettings {
    fun settingsIntent(context: Context): Intent? {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return null
        val currentId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        }.getOrNull()
        val infos = runCatching { imm.enabledInputMethodList }.getOrNull().orEmpty()
            .ifEmpty { runCatching { imm.inputMethodList }.getOrNull().orEmpty() }
        val info = infos.firstOrNull { it.id == currentId } ?: infos.firstOrNull() ?: return null
        val activity = info.settingsActivity?.takeIf { it.isNotBlank() } ?: return null
        return Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(info.packageName, activity))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
