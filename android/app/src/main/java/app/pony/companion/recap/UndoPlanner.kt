package app.pony.companion.recap

/**
 * Something Pony did that it can safely take back with one tap. Kept deliberately
 * narrow: only a genuinely reversible, local edit belongs here. Anything that left
 * the phone — a message sent, a payment made, an order placed — is never an
 * [UndoableAction]; see [UndoPlanner.COMMITTING].
 */
sealed interface UndoableAction {
    /** Short button text, e.g. "Clear the draft". */
    val label: String
    /** The confirm prompt shown before it runs. Undo still asks first. */
    val confirm: String
    /** One line for the recap: what will be undone. */
    val summary: String

    /**
     * Pony typed a draft into a field and did not send it. Undo reopens [app] and
     * clears the text. [app] is the package Pony typed into, so the undo acts on
     * that screen and not on whatever happens to be in front when the user taps it.
     */
    data class ClearDraft(val chars: Int, val app: String?) : UndoableAction {
        override val label get() = "Clear the draft"
        override val confirm get() = "Clear the text Pony typed?"
        override val summary get() = "Typed a draft of $chars character${if (chars == 1) "" else "s"}"
    }
}

/**
 * The one safety rule the owner asked for: never undo anything already sent or
 * paid. A task becomes *committed* the moment Pony confirms and runs a sending,
 * paying, buying, posting, transferring, deleting, or calling action — after
 * that, no undo is offered, even if the last thing Pony did was reversible.
 */
object UndoPlanner {
    /**
     * [app.pony.companion.voice.SafetyPolicy] reasons that put something out in
     * the world. Settings and security changes are deliberately not here: those
     * are exactly what "turn a setting back" undoes.
     */
    val COMMITTING = setOf("send", "payment", "payment_app", "money_screen", "purchase", "transfer", "post", "delete", "call")

    /** True when a confirmed action with this safety reason commits the task. */
    fun committedBy(reason: String?): Boolean = reason != null && reason in COMMITTING

    /** The action to offer, or null when the task is committed or nothing is reversible. */
    fun offer(latest: UndoableAction?, committed: Boolean): UndoableAction? =
        if (committed) null else latest
}
