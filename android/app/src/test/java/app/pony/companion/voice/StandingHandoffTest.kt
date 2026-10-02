package app.pony.companion.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StandingHandoffTest {
    private fun request(id: String) = OwnerRequest(id, "open the calculator", "typed", 0L)

    @Test
    fun aPollThatAcksTheRequestMovesOn() {
        val handoff = StandingHandoff()
        handoff.handedOut(request("r1"))
        assertNull(handoff.redeliver("r1"))
        assertNull(handoff.pendingId())
    }

    @Test
    fun aPollWithoutTheAckGetsTheSameRequestAgain() {
        val handoff = StandingHandoff()
        handoff.handedOut(request("r2"))
        assertEquals("r2", handoff.redeliver(null)?.id)
        assertEquals("r2", handoff.redeliver("r1")?.id)
        assertNull(handoff.redeliver("r2"))
    }

    @Test
    fun aFinishedOrOldRequestIsNotSentAgain() {
        var now = 0L
        val handoff = StandingHandoff(clock = { now }, keepMs = 1_000)
        handoff.handedOut(request("r3"))
        assertNull(handoff.redeliver(null) { false })

        handoff.handedOut(request("r4"))
        now += 1_001
        assertNull(handoff.redeliver(null))

        handoff.handedOut(request("r5"))
        handoff.finished("other")
        assertEquals("r5", handoff.pendingId())
        handoff.finished("r5")
        assertNull(handoff.redeliver(null))

        handoff.handedOut(request("r6"))
        handoff.finished(null)
        assertNull(handoff.redeliver(null))
    }
}
