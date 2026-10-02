package app.pony.companion.display

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pony.companion.voice.VoiceController

/** Stop cancels the task and the secondary display. It does not disconnect a paired assistant. */
class BackgroundStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BackgroundNotifier.ACTION_STOP) return
        VoiceController.stop()
        BackgroundHost.release(context.applicationContext)
    }
}
