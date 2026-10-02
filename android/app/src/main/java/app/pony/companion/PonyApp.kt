package app.pony.companion

import android.app.Application
import app.pony.companion.schedule.ScheduleAlarms
import app.pony.companion.session.Readiness
import app.pony.companion.tasks.TaskRuntime
import app.pony.companion.voice.VoiceController

class PonyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskRuntime.init(this)
        VoiceController.init(this)
        Readiness.watch(this)
        runCatching { ScheduleAlarms.rescheduleAll(this) }
    }
}
