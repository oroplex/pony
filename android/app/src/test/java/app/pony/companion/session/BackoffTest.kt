package app.pony.companion.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {
    @Test
    fun delaysDoubleUpToTheCapAndResetAfterSuccess() {
        val backoff = Backoff(baseMs = 1_000, maxMs = 30_000, jitter = 0.0)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), List(7) { backoff.next() })
        backoff.reset()
        assertEquals(1_000L, backoff.next())
    }

    @Test
    fun jitterStaysWithinTwentyPercent() {
        val low = Backoff(jitter = 0.2, random = { 0.0 })
        val high = Backoff(jitter = 0.2, random = { 0.999 })
        repeat(3) {
            low.next()
            high.next()
        }
        val lowDelay = low.next()
        val highDelay = high.next()
        assertTrue(lowDelay in 6_400L..8_000L)
        assertTrue(highDelay in 8_000L..9_600L)
    }
}
