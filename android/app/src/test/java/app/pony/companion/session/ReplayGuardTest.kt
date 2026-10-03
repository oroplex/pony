package app.pony.companion.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGuardTest {
    @Test
    fun rejectsAReusedOrRewoundSeq() {
        val recv = DirectionCounter()
        assertTrue(recv.accept(1))
        assertFalse(recv.accept(1))
        assertFalse(recv.accept(0))
        assertTrue(recv.accept(2))
        assertEquals(2L, recv.lastReceived())
    }

    @Test
    fun restoreContinuesFromTheSavedCounters() {
        val live = DirectionCounter()
        live.nextSend()
        live.nextSend()
        live.accept(4)
        val copy = DirectionCounter()
        copy.restore(live.lastSent(), live.lastReceived())
        assertEquals(3L, copy.nextSend())
        assertFalse(copy.accept(4))
        assertTrue(copy.accept(5))
    }

    @Test
    fun remembersAnExecutedCommandId() {
        val seen = ExecutedIds()
        assertTrue(seen.remember("tap-1"))
        assertFalse(seen.remember("tap-1"))
        assertTrue(seen.has("tap-1"))
        assertTrue(seen.remember("tap-2"))
    }
}
