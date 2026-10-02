package app.pony.companion.tasks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteStepTest {
    @Test
    fun looksAndReadsAreReadOnly() {
        assertTrue(RemoteStep.isReadOnly(StepKind.Look))
        assertTrue(RemoteStep.isReadOnly(StepKind.Read))
    }

    @Test
    fun actionsAreNotReadOnly() {
        for (kind in listOf(StepKind.Tap, StepKind.Type, StepKind.Swipe, StepKind.Key, StepKind.Open)) {
            assertFalse("$kind should act", RemoteStep.isReadOnly(kind))
            assertTrue("$kind should open the card", RemoteStep.opensWorkingCard(kind))
        }
    }

    @Test
    fun aLookNeverOpensTheWorkingCard() {
        assertFalse(RemoteStep.opensWorkingCard(StepKind.Look))
        assertFalse(RemoteStep.opensWorkingCard(StepKind.Read))
    }

    @Test
    fun doneAlwaysClearsAnImplicitCard() {
        // The client was never told the implicit id, so any (or no) ref clears it.
        assertTrue(RemoteStep.doneClears(taskImplicit = true, taskId = "abc", ref = null))
        assertTrue(RemoteStep.doneClears(taskImplicit = true, taskId = "abc", ref = ""))
        assertTrue(RemoteStep.doneClears(taskImplicit = true, taskId = "abc", ref = "something-else"))
    }

    @Test
    fun doneOnAnExplicitTaskStillRespectsTheRef() {
        assertTrue(RemoteStep.doneClears(taskImplicit = false, taskId = "abc", ref = null))
        assertTrue(RemoteStep.doneClears(taskImplicit = false, taskId = "abc", ref = "abc"))
        assertFalse(RemoteStep.doneClears(taskImplicit = false, taskId = "abc", ref = "other"))
    }
}
