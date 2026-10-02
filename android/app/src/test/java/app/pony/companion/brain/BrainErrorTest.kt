package app.pony.companion.brain

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainErrorTest {
    @Test
    fun aReadTimeoutIsATimeoutWorthRetrying() {
        val err = SocketTimeoutException("timeout")
        assertEquals(BrainError.Kind.TIMEOUT, BrainError.kind(err))
        assertTrue(BrainError.retryable(err))
        assertEquals("The brain took too long to answer — try again.", BrainError.message(err))
    }

    @Test
    fun theBareWordTimeoutIsTreatedAsATimeout() {
        // The on-device bug surfaced the raw word "timeout" as the result.
        val err = IllegalStateException("timeout")
        assertEquals(BrainError.Kind.TIMEOUT, BrainError.kind(err))
        assertTrue(BrainError.retryable(err))
        assertEquals("The brain took too long to answer — try again.", BrainError.message(err))
    }

    @Test
    fun aGatewayTimeoutFromTheProviderStillReadsAsATimeout() {
        val err = IllegalStateException("HTTP 504 Gateway Timeout")
        assertEquals(BrainError.Kind.TIMEOUT, BrainError.kind(err))
        assertTrue(BrainError.retryable(err))
    }

    @Test
    fun aDroppedConnectionIsANetworkErrorWorthRetrying() {
        for (err in listOf(UnknownHostException("Unable to resolve host"), ConnectException("failed to connect"), IOException("connection reset"))) {
            assertEquals(BrainError.Kind.NETWORK, BrainError.kind(err))
            assertTrue(BrainError.retryable(err))
            assertTrue(BrainError.message(err).contains("Check your connection"))
        }
    }

    @Test
    fun aCallTimeoutInterruptedIoIsATimeoutButAPlainInterruptIsNetwork() {
        assertEquals(BrainError.Kind.TIMEOUT, BrainError.kind(InterruptedIOException("timeout")))
        assertEquals(BrainError.Kind.NETWORK, BrainError.kind(InterruptedIOException("interrupted")))
    }

    @Test
    fun aRejectedKeyIsNotRetriedAndKeepsItsOwnMessage() {
        val err = IllegalStateException("HTTP 401 Unauthorized: the key was rejected")
        assertEquals(BrainError.Kind.OTHER, BrainError.kind(err))
        assertFalse(BrainError.retryable(err))
        assertEquals("HTTP 401 Unauthorized: the key was rejected", BrainError.message(err))
    }

    @Test
    fun backoffGrowsAndIsCapped() {
        assertEquals(600L, BrainError.backoffMs(1))
        assertEquals(1_200L, BrainError.backoffMs(2))
        assertEquals(2_400L, BrainError.backoffMs(3))
        assertEquals(BrainError.MAX_BACKOFF_MS, BrainError.backoffMs(20))
    }
}
