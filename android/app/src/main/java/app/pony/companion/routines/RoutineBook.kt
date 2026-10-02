package app.pony.companion.routines

import android.content.Context
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskRuntime
import java.io.File

/**
 * Bridges the pure [RoutineStore] to the app: where routines live on disk, and
 * how the owner's last finished task becomes one. Saving and replaying both run
 * through here so the one place that knows "the last task" is shared.
 */
object RoutineBook {
    fun store(context: Context): RoutineStore =
        RoutineStore(File(context.applicationContext.filesDir, "routines/routines.json"))

    /** Saves the owner's last real task under [name]. Null when there's nothing worth saving. */
    fun saveLast(context: Context, name: String): Routine? {
        val last = Routines.lastSavable(recentTasks()) ?: return null
        return store(context).save(name, last.text)
    }

    /** The routine whose name the owner just said, if any. */
    fun match(context: Context, utterance: String): Routine? =
        runCatching { store(context).match(utterance) }.getOrNull()

    fun markRun(context: Context, id: String) {
        runCatching { store(context).markRun(id) }
    }

    private fun recentTasks(): List<TaskRecord> =
        buildList {
            TaskRuntime.live.value?.let { add(it) }
            addAll(TaskRuntime.history.value)
        }
}
