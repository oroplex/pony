package app.pony.companion.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomesTest {
    @Test
    fun cappedSaysHowManyStepsWhatItTriedAndThatItResumes() {
        val message = Outcomes.capped(30, "Tapped “Languages and types”")
        assertTrue(message.contains("30 steps"))
        assertTrue(message.contains("Last thing I tried: Tapped “Languages and types”"))
        assertTrue(message.contains("Keep going"))
    }

    @Test
    fun cappedStillReadsWellWithNoLastAction() {
        val message = Outcomes.capped(30, null)
        assertFalse(message.contains("Last thing I tried"))
        assertTrue(message.contains("Keep going"))
    }

    @Test
    fun lastActionPrefersTheMostRecentRealAction() {
        val steps = listOf(
            TaskStep(1, StepKind.Open, "Opened Settings"),
            TaskStep(2, StepKind.Tap, "Tapped “Search”"),
            TaskStep(3, StepKind.Look, "Looked at the screen"),
            TaskStep(4, StepKind.Status, "Claude is on it"),
        )
        assertEquals("Tapped “Search”", Outcomes.lastAction(steps))
    }

    @Test
    fun lastActionIsNullWhenNothingWasDoneYet() {
        val steps = listOf(
            TaskStep(1, StepKind.Status, "Claude is on it"),
            TaskStep(2, StepKind.Look, "Looked at the screen"),
        )
        assertNull(Outcomes.lastAction(steps))
    }
}
