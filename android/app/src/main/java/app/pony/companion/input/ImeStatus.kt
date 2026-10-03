package app.pony.companion.input

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

data class ImeSnapshot(
    val currentId: String?,
    val ponyEnabled: Boolean,
    val ponySelected: Boolean,
    val ponyActive: Boolean,
) {
    val ponyUsable: Boolean get() = ponyEnabled && (ponySelected || ponyActive)
}

object ImeStatus {
    fun component(context: Context): ComponentName =
        ComponentName(context, PonyInputMethodService::class.java)

    fun currentId(context: Context): String? = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    } catch (_: SecurityException) {
        null
    }

    fun snapshot(context: Context, ponyActive: Boolean = ImeBridge.active): ImeSnapshot {
        val current = currentId(context)
        val name = component(context)
        return ImeSnapshot(
            currentId = current,
            ponyEnabled = enabled(context),
            ponySelected = listed(current, name.flattenToString(), name.flattenToShortString()),
            ponyActive = ponyActive,
        )
    }

    fun enabled(context: Context): Boolean {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        val id = component(context)
        return imm.enabledInputMethodList.any { info ->
            info.packageName == id.packageName && info.serviceName == id.className
        }
    }

    fun selected(context: Context): Boolean {
        return try {
            val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val name = component(context)
            listed(current, name.flattenToString(), name.flattenToShortString())
        } catch (_: SecurityException) {
            false
        }
    }

    /** `ENABLED_INPUT_METHODS` is colon-separated and each entry may carry a `;subtype`. */
    fun listed(raw: String?, full: String, shortId: String): Boolean {
        if (raw.isNullOrBlank()) return false
        return raw.split(':').any { entry ->
            val id = entry.substringBefore(';')
            id == full || id == shortId
        }
    }
}
