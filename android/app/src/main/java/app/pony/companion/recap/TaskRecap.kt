package app.pony.companion.recap

import app.pony.companion.tasks.StepKind
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskState

/**
 * A short "here's what I did" for a finished ask: a one-line result, the handful
 * of steps worth showing, and — when it's safe — the one thing Pony can undo.
 * Before/after thumbnails are held separately (see [RecapShots]) so the record
 * on disk never carries another app's screen.
 */
data class Recap(
    val summary: String,
    val did: List<String>,
    val undo: UndoableAction?,
) {
    val hasUndo: Boolean get() = undo != null
}

object RecapBuilder {
    private val ACTIONS = setOf(StepKind.Open, StepKind.Tap, StepKind.Type, StepKind.Swipe, StepKind.Key)

    /** At most this many "what I did" lines; the rest are folded into a count. */
    const val MAX_LINES = 6

    /**
     * Build the recap from a finished task and the gated undo offer (already
     * null when the task committed something — see [UndoPlanner]).
     */
    fun build(record: TaskRecord, undo: UndoableAction?): Recap {
        val summary = record.outcome?.takeIf { it.isNotBlank() }
            ?: record.headline?.takeIf { it.isNotBlank() }
            ?: fallback(record.state)
        return Recap(summary, did(record), undo)
    }

    /** The meaningful action labels, consecutive repeats folded, capped at [MAX_LINES]. */
    fun did(record: TaskRecord): List<String> {
        val lines = mutableListOf<String>()
        for (step in record.steps) {
            if (step.kind !in ACTIONS || !step.ok) continue
            val label = step.label.trim()
            if (label.isEmpty()) continue
            if (lines.lastOrNull() == label) continue
            lines += label
        }
        if (lines.size <= MAX_LINES) return lines
        val extra = lines.size - (MAX_LINES - 1)
        return lines.take(MAX_LINES - 1) + "…and $extra more step${if (extra == 1) "" else "s"}"
    }

    private fun fallback(state: TaskState): String = when (state) {
        TaskState.Done -> "Done."
        TaskState.Stopped -> "You stopped this."
        else -> "It didn't finish."
    }
}
