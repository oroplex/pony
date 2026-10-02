package app.pony.companion.a11y

import app.pony.companion.a11y.SettleWait.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class SettleWaitTest {
    @Test
    fun quietAndUnchangedScreenSettles() {
        val verdict = SettleWait.decide(SettleWait.Sample(elapsedMs = 400, quietForMs = 400, diffRatio = 0.0))
        assertEquals(Verdict.SETTLED, verdict)
    }

    @Test
    fun aMovingScreenKeepsWaitingEvenWhenAccessibilityIsQuiet() {
        val verdict = SettleWait.decide(SettleWait.Sample(elapsedMs = 400, quietForMs = 400, diffRatio = 0.5))
        assertEquals(Verdict.WAIT, verdict)
    }

    @Test
    fun aNoisyAccessibilityStreamKeepsWaitingEvenWhenTheFrameMatches() {
        val verdict = SettleWait.decide(SettleWait.Sample(elapsedMs = 400, quietForMs = 100, diffRatio = 0.0))
        assertEquals(Verdict.WAIT, verdict)
    }

    @Test
    fun aStillScreenIsNotCalledSettledBeforeTheFloor() {
        val verdict = SettleWait.decide(SettleWait.Sample(elapsedMs = 80, quietForMs = 400, diffRatio = 0.0))
        assertEquals(Verdict.WAIT, verdict)
    }

    @Test
    fun aScreenThatNeverSettlesTimesOut() {
        val verdict = SettleWait.decide(
            SettleWait.Sample(elapsedMs = 5_000, quietForMs = 10, diffRatio = 0.9),
            timeoutMs = 4_000,
        )
        assertEquals(Verdict.TIMED_OUT, verdict)
    }

    @Test
    fun settleReplaysASequenceAndStopsAtTheFirstVerdict() {
        val busy = SettleWait.Sample(elapsedMs = 200, quietForMs = 50, diffRatio = 0.4)
        val still = SettleWait.Sample(elapsedMs = 600, quietForMs = 400, diffRatio = 0.0)
        assertEquals(Verdict.SETTLED, SettleWait.settle(listOf(busy, busy, still)))
        assertEquals(Verdict.WAIT, SettleWait.settle(listOf(busy, busy)))
        val late = SettleWait.Sample(elapsedMs = 5_000, quietForMs = 50, diffRatio = 0.9)
        assertEquals(Verdict.TIMED_OUT, SettleWait.settle(listOf(busy, late), timeoutMs = 4_000))
    }

    @Test
    fun identicalFramesDifferByNothing() {
        val frame = intArrayOf(10, 20, 30, 40)
        assertEquals(0.0, SettleWait.diffRatio(frame, frame.copyOf()), 0.0)
    }

    @Test
    fun everyCellMovingIsAFullDifference() {
        assertEquals(1.0, SettleWait.diffRatio(intArrayOf(0, 0, 0), intArrayOf(200, 200, 200)), 0.0)
    }

    @Test
    fun halfTheCellsMovingIsHalfADifference() {
        val a = intArrayOf(0, 0, 0, 0)
        val b = intArrayOf(200, 200, 0, 0)
        assertEquals(0.5, SettleWait.diffRatio(a, b), 0.0)
    }

    @Test
    fun smallWobblesWithinToleranceDoNotCount() {
        val a = intArrayOf(100, 100, 100)
        val b = intArrayOf(108, 95, 100)
        assertEquals(0.0, SettleWait.diffRatio(a, b, tolerance = 12), 0.0)
    }

    @Test
    fun framesThatDoNotLineUpCountAsAFullChange() {
        assertEquals(1.0, SettleWait.diffRatio(intArrayOf(1, 2, 3), intArrayOf(1, 2)), 0.0)
        assertEquals(1.0, SettleWait.diffRatio(IntArray(0), IntArray(0)), 0.0)
    }
}
