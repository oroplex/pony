package app.pony.companion.tasks

/** Plain-words results for tasks that didn't run all the way to the end. */
object Outcomes {
    private val ACTIONS = setOf(StepKind.Open, StepKind.Tap, StepKind.Type, StepKind.Swipe, StepKind.Key)

    /** The last thing Pony actually did, for telling the owner where it got to. */
    fun lastAction(steps: List<TaskStep>): String? =
        steps.lastOrNull { it.kind in ACTIONS && it.ok }?.label
            ?: steps.lastOrNull { it.kind in ACTIONS }?.label

    /**
     * Why a task stopped at the step cap, in plain words: how many steps it
     * used, the last thing it tried, and that the owner can pick it up again.
     */
    fun capped(steps: Int, lastAction: String?): String {
        val tail = lastAction?.takeIf { it.isNotBlank() }
            ?.let { " Last thing I tried: ${it.trim().trimEnd('.')}." }
            .orEmpty()
        return "I used all $steps steps and didn't finish yet.$tail Tap Keep going and I'll pick up from here."
    }
}
