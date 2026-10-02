package app.pony.companion.tasks

import app.pony.companion.text.Prose
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The task the owner can see right now, and the history behind it.
 * One task is live at a time. Calls, notifications, and reconnects never end
 * it; only a result, Stop, or a failure does.
 */
class TaskTracker(
    private val store: TaskStore?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()
    private val _live = MutableStateFlow<TaskRecord?>(null)
    val live: StateFlow<TaskRecord?> = _live.asStateFlow()
    private val _history = MutableStateFlow(store?.list().orEmpty())
    val history: StateFlow<List<TaskRecord>> = _history.asStateFlow()

    fun current(): TaskRecord? = _live.value?.takeIf { it.running }

    fun begin(
        text: String,
        source: String,
        brain: String,
        brainLabel: String,
        state: TaskState = TaskState.Running,
        id: String = ids(),
        implicit: Boolean = false,
        headline: String? = null,
    ): TaskRecord = synchronized(lock) {
        current()?.let { finishLocked(it, TaskState.Stopped, "Replaced by a new task.") }
        val record = TaskRecord(
            id = id,
            text = text,
            source = source,
            brain = brain,
            brainLabel = brainLabel,
            createdAt = clock(),
            state = state,
            headline = headline,
            implicit = implicit,
        )
        publish(record, persist = true)
        record
    }

    fun setState(state: TaskState, headline: String? = null, id: String? = null) = mutate(id) {
        it.copy(state = state, headline = headline ?: it.headline)
    }

    fun headline(text: String, id: String? = null) = mutate(id) { it.copy(headline = text) }

    /** Hands a task to another brain, for example when Grok Bot didn't pick it up. */
    fun reassign(brain: String, brainLabel: String, headline: String? = null, id: String? = null) = mutate(id) {
        it.copy(brain = brain, brainLabel = brainLabel, headline = headline ?: it.headline, state = TaskState.Running)
    }

    fun step(
        kind: StepKind,
        label: String,
        ok: Boolean = true,
        detail: String? = null,
        shot: ByteArray? = null,
        id: String? = null,
    ): TaskStep? = synchronized(lock) {
        val task = current() ?: return null
        if (id != null && task.id != id) return null
        val now = clock()
        val last = task.steps.lastOrNull()
        val stored = shot?.let { store?.saveShot(task.id, it) }
        val folds = stored == null && last != null && last.kind == kind && last.label == label &&
            last.shot == null && kind in FOLDABLE && now - last.at < FOLD_WINDOW_MS
        val steps = if (folds) {
            task.steps.dropLast(1) + last!!.copy(at = now, count = last.count + 1, ok = ok)
        } else {
            (task.steps + TaskStep(now, kind, label, ok, detail, stored)).takeLast(MAX_STEPS)
        }
        val state = if (kind == StepKind.Wait || kind == StepKind.Status) task.state else runningState(task.state)
        // A passive look/read shouldn't flip the pill to "Looked at the screen": keep
        // the last real action as the headline, while still recording the step.
        val headline = if (RemoteStep.isReadOnly(kind)) task.headline else label
        val next = task.copy(steps = steps, headline = headline, state = state)
        publish(next, persist = true)
        next.steps.last()
    }

    fun finish(state: TaskState, outcome: String?, id: String? = null, resumable: Boolean = false): TaskRecord? = synchronized(lock) {
        val task = current() ?: return null
        if (id != null && task.id != id) return null
        finishLocked(task, state, outcome, resumable)
    }

    fun find(id: String): TaskRecord? =
        _live.value?.takeIf { it.id == id } ?: _history.value.firstOrNull { it.id == id }

    /**
     * Starts a detached session entry: a record kept in history for a paired
     * assistant's own actions. Unlike [begin] it never becomes the live task, so
     * it can't hide the owner's Ask box, replace the Ask input, or supersede a
     * finished task's recap — and it doesn't end whatever is live.
     */
    fun beginDetached(
        text: String,
        brain: String,
        brainLabel: String,
        headline: String? = null,
        source: String = "assistant",
        id: String = ids(),
    ): TaskRecord = synchronized(lock) {
        val record = TaskRecord(
            id = id,
            text = text,
            source = source,
            brain = brain,
            brainLabel = brainLabel,
            createdAt = clock(),
            state = TaskState.Running,
            headline = headline,
            implicit = true,
        )
        publishDetached(record)
        record
    }

    /**
     * Appends a step to a specific record by [id] — live or detached — folding
     * repeats like [step]. Returns null if that record is gone or finished. A
     * detached record is updated in history only; it never becomes live.
     */
    fun stepOn(
        id: String,
        kind: StepKind,
        label: String,
        ok: Boolean = true,
        detail: String? = null,
        shot: ByteArray? = null,
    ): TaskStep? = synchronized(lock) {
        val task = recordFor(id)?.takeIf { it.running } ?: return null
        val now = clock()
        val last = task.steps.lastOrNull()
        val stored = shot?.let { store?.saveShot(task.id, it) }
        val folds = stored == null && last != null && last.kind == kind && last.label == label &&
            last.shot == null && kind in FOLDABLE && now - last.at < FOLD_WINDOW_MS
        val steps = if (folds) {
            task.steps.dropLast(1) + last!!.copy(at = now, count = last.count + 1, ok = ok)
        } else {
            (task.steps + TaskStep(now, kind, label, ok, detail, stored)).takeLast(MAX_STEPS)
        }
        val headline = if (RemoteStep.isReadOnly(kind)) task.headline else label
        val next = task.copy(steps = steps, headline = headline, state = runningState(task.state))
        publishDetached(next)
        next.steps.last()
    }

    /** Finishes a detached record by [id] without touching whatever is live. */
    fun finishDetached(id: String, state: TaskState, outcome: String?): TaskRecord? = synchronized(lock) {
        val task = recordFor(id)?.takeIf { it.running } ?: return null
        val clean = outcome?.let { Prose.plain(it) }?.takeIf { it.isNotBlank() }
        val done = task.copy(state = state, outcome = clean ?: task.outcome, endedAt = clock(), headline = clean ?: task.headline)
        publishDetached(done)
        done
    }

    fun delete(id: String) = synchronized(lock) {
        if (_live.value?.id == id) _live.value = null
        _history.value = store?.delete(id) ?: _history.value.filter { it.id != id }
    }

    fun clear() = synchronized(lock) {
        if (current() == null) _live.value = null
        store?.clear()
        _history.value = listOfNotNull(current())
    }

    fun shotFile(taskId: String, name: String) = store?.shotFile(taskId, name)

    /**
     * Settles tasks a previous run of the app left open, for example after a
     * low-memory kill or an update. The newest request still waiting for the
     * assistant goes back in line through [requeue] if it is younger than
     * [requeueWindowMs]. A task that was already running can't pick up where
     * it stopped, so it is closed with a plain reason.
     */
    fun recover(requeueWindowMs: Long, requeue: (TaskRecord) -> Boolean): TaskRecord? = synchronized(lock) {
        val now = clock()
        val open = _history.value.filter { !it.state.finished }.sortedByDescending { it.createdAt }
        if (open.isEmpty()) return null
        var revived: TaskRecord? = null
        for (task in open) {
            val waiting = task.state == TaskState.Queued || task.state == TaskState.Sent || task.state == TaskState.Waiting
            val forAssistant = waiting && task.brain == Brains.GROK && task.actionCount == 0
            when {
                forAssistant && revived == null && now - task.createdAt < requeueWindowMs && requeue(task) ->
                    revived = task.copy(state = TaskState.Queued, headline = "Waiting for ${task.brainLabel} to check in")
                forAssistant -> finishLocked(task, TaskState.Expired, "${task.brainLabel} didn't pick this up in time.")
                task.implicit && task.actionCount > 0 -> finishLocked(task, TaskState.Done, "${task.brainLabel} finished.")
                else -> finishLocked(task, TaskState.Failed, "Pony restarted before this finished.")
            }
        }
        val back = revived
        if (back != null) publish(back, persist = true) else _live.value = null
        back
    }

    private fun finishLocked(task: TaskRecord, state: TaskState, outcome: String?, resumable: Boolean = false): TaskRecord {
        val clean = outcome?.let { Prose.plain(it) }?.takeIf { it.isNotBlank() }
        val done = task.copy(
            state = state,
            outcome = clean ?: task.outcome,
            endedAt = clock(),
            headline = clean ?: task.headline,
            resumable = resumable,
        )
        publish(done, persist = true)
        return done
    }

    private fun mutate(id: String?, transform: (TaskRecord) -> TaskRecord): TaskRecord? = synchronized(lock) {
        val task = current() ?: return null
        if (id != null && task.id != id) return null
        val next = transform(task)
        publish(next, persist = next.state != task.state)
        next
    }

    private fun publish(record: TaskRecord, persist: Boolean) {
        _live.value = record
        val saved = if (persist && store != null) store.upsert(record) else null
        _history.value = saved ?: (listOf(record) + _history.value.filter { it.id != record.id })
            .sortedByDescending { it.createdAt }
    }

    private fun recordFor(id: String): TaskRecord? =
        _live.value?.takeIf { it.id == id } ?: _history.value.firstOrNull { it.id == id }

    /**
     * Writes a record to history and the store, and to the live slot only when
     * it already is the live one. A detached session is never the live task, so
     * this leaves [current] and the owner's visible card alone.
     */
    private fun publishDetached(record: TaskRecord) {
        if (_live.value?.id == record.id) _live.value = record
        val saved = store?.upsert(record)
        _history.value = saved ?: (listOf(record) + _history.value.filter { it.id != record.id })
            .sortedByDescending { it.createdAt }
    }

    private fun runningState(state: TaskState): TaskState =
        if (state == TaskState.Queued || state == TaskState.Sent || state == TaskState.Waiting) TaskState.Running else state

    companion object {
        const val MAX_STEPS = 200
        const val FOLD_WINDOW_MS = 20_000L
        private val FOLDABLE = setOf(StepKind.Read, StepKind.Look, StepKind.Wait)
    }
}
