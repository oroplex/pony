package app.pony.companion.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pony.companion.session.PonySessionService

/**
 * After a reboot or an app update: rejoin the paired session if it is still
 * inside its window, and restart Hey Pony if the owner turned it on.
 */
class WakeWordBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (VoicePrefs.autoReconnect(context)) {
            runCatching { PonySessionService.resume(context) }
        }
        if (VoicePrefs.wakeWord(context)) runCatching { WakeWordService.start(context) }
    }
}
