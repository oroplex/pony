package app.pony.companion.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.pony.companion.voice.VoiceController
import java.io.File
import java.time.ZoneId

/**
 * Bridges the pure [ScheduleStore] to Android's [AlarmManager]. One exact alarm
 * is kept per enabled task, pointed at its next fire. When a task fires it runs
 * as a normal ask, then a recurring task is re-armed for the next occurrence and
 * a one-shot switches itself off.
 *
 * Exact alarms are best-effort: on Android 12+ without the exact-alarm
 * permission we fall back to an inexact idle alarm rather than crash, so a
 * schedule still fires, just not to the minute.
 */
object ScheduleAlarms {
    const val ACTION_FIRE = "app.pony.companion.action.SCHEDULE_FIRE"
    const val EXTRA_ID = "schedule_id"
    const val SOURCE = "schedule"

    fun store(context: Context): ScheduleStore =
        ScheduleStore(File(context.applicationContext.filesDir, "schedule/schedule.json"))

    /** Creates a task, arms its alarm, and returns it. */
    fun add(context: Context, text: String, schedule: Schedule): ScheduledTask {
        val task = store(context).add(text, schedule)
        arm(context, task)
        return task
    }

    /** Switches a task on or off and arms or cancels its alarm to match. */
    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val task = store(context).setEnabled(id, enabled) ?: return
        if (enabled) arm(context, task) else cancel(context, id)
    }

    fun delete(context: Context, id: String) {
        store(context).delete(id)
        cancel(context, id)
    }

    /** Cancels every alarm and drops every task. Backs the "delete all" button. */
    fun clearAll(context: Context) {
        val app = context.applicationContext
        val store = store(app)
        store.all().forEach { cancel(app, it.id) }
        store.clear()
    }

    /** Re-arms every enabled task. Called on boot, update, and app start. */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        store(app).all().forEach { task ->
            if (task.enabled) arm(app, task) else cancel(app, task.id)
        }
    }

    /** An alarm went off: run the task, then re-arm (recurring) or switch off (one-shot). */
    fun onFired(context: Context, id: String) {
        val app = context.applicationContext
        val store = store(app)
        val task = store.get(id) ?: return
        if (!task.enabled) return
        VoiceController.ask(app, task.text, source = SOURCE)
        if (task.schedule.repeats) {
            arm(app, task)
        } else {
            store.setEnabled(id, false)
            cancel(app, id)
        }
    }

    private fun arm(context: Context, task: ScheduledTask) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(app, task.id)
        val next = task.schedule.nextFireAt(System.currentTimeMillis(), ZoneId.systemDefault())
        if (next == null) {
            manager.cancel(pending)
            return
        }
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }

    private fun cancel(context: Context, id: String) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(app, id))
    }

    private fun pendingIntent(context: Context, id: String): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
