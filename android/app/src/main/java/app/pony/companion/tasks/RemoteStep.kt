package app.pony.companion.tasks

/**
 * Policy for how a paired assistant's commands touch the visible task card.
 *
 * A read-only look or screen read is passive: while an external client (Grok
 * Bot) is merely watching, it must not replace the owner's Ask box with a
 * "Pony is working" card, nor keep one alive. Only acting commands open or feed
 * the implicit card, and `done` or a short idle clears it.
 */
object RemoteStep {
    /** Looks (screenshot) and reads (ui_tree) observe without changing anything. */
    val READ_ONLY: Set<StepKind> = setOf(StepKind.Look, StepKind.Read)

    fun isReadOnly(kind: StepKind): Boolean = kind in READ_ONLY

    /** An acting command (tap, type, swipe, key, open) may open the working card; a look/read never does. */
    fun opensWorkingCard(kind: StepKind): Boolean = !isReadOnly(kind)

    /**
     * Whether a `done` should finish this task. An explicit [ref] must match a
     * known task, but an implicit card — one the client was never told the id of
     * — is always cleared by `done`, so it never gets stuck on screen.
     */
    fun doneClears(taskImplicit: Boolean, taskId: String, ref: String?): Boolean =
        ref.isNullOrBlank() || ref == taskId || taskImplicit
}
