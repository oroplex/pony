package app.pony.companion.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StopGateTest {
    @Test
    fun aStoppedExternalSessionHoldsUntilTheOwnerPicksItBackUp() {
        val gate = StopGate()
        gate.stop("task-1")
        assertTrue(gate.isStopped())
        assertTrue(gate.blocks("task-1"))
        // The assistant looping back to wait_for_request no longer lifts it, so
        // nothing has released the stop yet and it still blocks the task.
        assertNull(gate.lastRelease)
        assertTrue(gate.isStopped())
        // Keep going re-asks the same request as a new task — that is what lifts it.
        gate.release(StopGate.Release.NEW_TASK)
        assertFalse(gate.isStopped())
        assertFalse(gate.blocks("task-1"))
        assertEquals(StopGate.Release.NEW_TASK, gate.lastRelease)
    }

    @Test
    fun aNewPairingSessionIsNeverStoppedByAnOldStop() {
        val gate = StopGate()
        gate.stop(null)
        assertTrue(gate.blocks(null))
        gate.release(StopGate.Release.NEW_SESSION)
        assertFalse(gate.isStopped())
        assertNull(gate.stoppedTask())
    }

    @Test
    fun aNewRequestOrTaskReleasesTheStop() {
        val gate = StopGate()
        gate.stop("a")
        gate.release(StopGate.Release.NEW_REQUEST)
        assertFalse(gate.isStopped())
        gate.stop("b")
        gate.release(StopGate.Release.NEW_TASK)
        assertFalse(gate.isStopped())
    }

    @Test
    fun aStopForOneTaskDoesNotBlockAnother() {
        val gate = StopGate()
        gate.stop("first")
        assertTrue(gate.blocks("first"))
        assertFalse(gate.blocks("second"))
        assertTrue(gate.blocks(null))
    }

    @Test
    fun stoppingTwiceKeepsTheLatestTask() {
        var now = 10L
        val gate = StopGate(clock = { now })
        gate.stop("one")
        now = 20L
        gate.stop("two")
        assertEquals("two", gate.stoppedTask())
    }
}
