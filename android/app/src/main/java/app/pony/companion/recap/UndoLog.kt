package app.pony.companion.recap

/**
 * Remembers the one reversible thing Pony last did on the current task, so the
 * recap can offer to take it back. In memory only — it never persists, it empties
 * when a new task begins, and it locks shut the moment the task commits something
 * that left the phone (a message sent, a payment made). See [UndoPlanner].
 */
object UndoLog {
    private val lock = Any()
    private var taskId: String? = null
    private var action: UndoableAction? = null
    private var committed = false

    /** A new task starts clean: no prior undo carries over. */
    fun start(id: String) = synchronized(lock) {
        taskId = id
        action = null
        committed = false
    }

    /** Record the latest reversible action for [id], unless the task already committed. */
    fun record(id: String?, latest: UndoableAction) = synchronized(lock) {
        if (id != null && id == taskId && !committed) action = latest
    }

    /** Mark the task committed (sent, paid, posted…). No undo is offered after this. */
    fun commit(id: String?) = synchronized(lock) {
        if (id != null && id == taskId) {
            committed = true
            action = null
        }
    }

    /** The undo to offer for [id], gated by [UndoPlanner], or null. */
    fun offer(id: String?): UndoableAction? = synchronized(lock) {
        if (id != null && id == taskId) UndoPlanner.offer(action, committed) else null
    }

    /** Drop the slot once an undo has run, so it can't run twice. */
    fun consume(id: String?) = synchronized(lock) {
        if (id != null && id == taskId) action = null
    }

    fun clear() = synchronized(lock) {
        taskId = null
        action = null
        committed = false
    }
}
