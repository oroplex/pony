package app.pony.companion.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Wakes for a scheduled task's alarm and runs it, and re-arms every task after a
 * reboot or an app update (alarms don't survive either on their own).
 */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            ScheduleAlarms.ACTION_FIRE -> {
                val id = intent.getStringExtra(ScheduleAlarms.EXTRA_ID) ?: return
                runAsync { ScheduleAlarms.onFired(app, id) }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                runAsync { ScheduleAlarms.rescheduleAll(app) }
        }
    }

    private fun runAsync(work: () -> Unit) {
        val pending = goAsync()
        Thread {
            try {
                runCatching { work() }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
