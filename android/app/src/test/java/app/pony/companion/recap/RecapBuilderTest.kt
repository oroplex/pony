package app.pony.companion.recap

import app.pony.companion.tasks.StepKind
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskState
import app.pony.companion.tasks.TaskStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapBuilderTest {
    private fun step(kind: StepKind, label: String, ok: Boolean = true) =
        TaskStep(at = 0L, kind = kind, label = label, ok = ok)

    private fun record(
        state: TaskState = TaskState.Done,
        outcome: String? = null,
        headline: String? = null,
        steps: List<TaskStep> = emptyList(),
    ) = TaskRecord(
        id = "t1",
        text = "text a message to mum",
        source = "typed",
        brain = "phone",
        brainLabel = "Claude",
        createdAt = 0L,
        state = state,
        steps = steps,
        outcome = outcome,
        headline = headline,
    )

    @Test
    fun summaryPrefersTheOutcomeThenHeadlineThenAFallback() {
        assertEquals("Texted mum.", RecapBuilder.build(record(outcome = "Texted mum.", headline = "h"), null).summary)
        assertEquals("h", RecapBuilder.build(record(outcome = null, headline = "h"), null).summary)
        assertEquals("Done.", RecapBuilder.build(record(outcome = "", headline = ""), null).summary)
        assertEquals("You stopped this.", RecapBuilder.build(record(state = TaskState.Stopped, outcome = null), null).summary)
    }

    @Test
    fun didKeepsOnlyRealActionsAndFoldsRepeats() {
        val recap = RecapBuilder.build(
            record(
                steps = listOf(
                    step(StepKind.Open, "Opened Messages"),
                    step(StepKind.Read, "Read the screen"),
                    step(StepKind.Tap, "Tapped “New message”"),
                    step(StepKind.Tap, "Tapped “New message”"),
                    step(StepKind.Type, "Typed 20 characters"),
                    step(StepKind.Status, "Thinking"),
                    step(StepKind.Tap, "Tapped a dead control", ok = false),
                ),
            ),
            null,
        )
        assertEquals(
            listOf("Opened Messages", "Tapped “New message”", "Typed 20 characters"),
            recap.did,
        )
    }

    @Test
    fun didCapsLongRunsWithACount() {
        val steps = (1..9).map { step(StepKind.Tap, "Tapped item $it") }
        val did = RecapBuilder.build(record(steps = steps), null).did
        assertEquals(RecapBuilder.MAX_LINES, did.size)
        assertEquals("Tapped item 1", did.first())
        assertEquals("…and 4 more steps", did.last())
    }

    @Test
    fun carriesTheUndoOfferThrough() {
        val undo = UndoableAction.ClearDraft(8, "com.example.chat")
        val recap = RecapBuilder.build(record(), undo)
        assertTrue(recap.hasUndo)
        assertEquals(undo, recap.undo)

        assertNull(RecapBuilder.build(record(), null).undo)
    }
}
