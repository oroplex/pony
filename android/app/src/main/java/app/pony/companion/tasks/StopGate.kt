package app.pony.companion.tasks

/**
 * Stop belongs to the task that was running when the owner pressed it, not
 * to the session. The assistant's commands for that task are refused until
 * the owner picks it back up: a new request, a new task (including Keep
 * going on the stopped one), or a new pairing session. The assistant going
 * back to `wait_for_request` does NOT lift it, so a stopped external session
 * stays stopped cleanly until the owner approves resuming instead of letting
 * the bot quietly carry on the moment it loops back to waiting.
 */
class StopGate(private val clock: () -> Long = System::currentTimeMillis) {
    data class Stopped(val taskId: String?, val at: Long)

    enum class Release { NEW_REQUEST, NEW_TASK, NEW_SESSION }

    @Volatile
    private var stopped: Stopped? = null

    @Volatile
    var lastRelease: Release? = null
        private set

    fun stop(taskId: String?) {
        stopped = Stopped(taskId, clock())
    }

    fun isStopped(): Boolean = stopped != null

    fun stoppedTask(): String? = stopped?.taskId

    /** True when [taskId] is the task the owner stopped. Other tasks run normally. */
    fun blocks(taskId: String?): Boolean {
        val current = stopped ?: return false
        return current.taskId == null || taskId == null || current.taskId == taskId
    }

    fun release(reason: Release) {
        if (stopped != null) lastRelease = reason
        stopped = null
    }
}

object StopState {
    val gate = StopGate()
}
