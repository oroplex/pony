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
        assertEquals(BrainError.Kind.AUTH, BrainError.kind(err))
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

    @Test
    fun retryAfterSecondsAndHttpDateAreHonored() {
        assertEquals(12_000L, BrainError.parseRetryAfter("12"))
        assertEquals(1_500L, BrainError.parseRetryAfter("1.5"))
        val whenMs = 1_000_000_000_000L
        val date = java.time.Instant.ofEpochMilli(whenMs + 8_000)
            .atZone(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
        assertEquals(8_000L, BrainError.parseRetryAfter(date, nowMs = whenMs))
    }

    @Test
    fun providerRetryDelayHintsAreRead() {
        assertEquals(8_000L, BrainError.parseRetryHint("""{"retryDelay":"8s"}"""))
        assertEquals(1_500L, BrainError.parseRetryHint("""{"retryDelay":"1.5s"}"""))
        assertEquals(2_500L, BrainError.parseRetryHint("""{"retry_after_ms":2500}"""))
        assertEquals(5_000L, BrainError.parseRetryHint("""{"retry_after":5}"""))
        assertEquals(9_000L, BrainError.parseRetryHint("""{"retryDelay":{"seconds":9}}"""))
    }

    @Test
    fun rateLimitBackoffGrowsInsideJitterBounds() {
        assertEquals(1_000L, BrainError.rateLimitBackoffMs(1) { 0.5 })
        assertEquals(2_000L, BrainError.rateLimitBackoffMs(2) { 0.5 })
        assertEquals(4_000L, BrainError.rateLimitBackoffMs(3) { 0.5 })
        assertEquals(800L, BrainError.rateLimitBackoffMs(1) { 0.0 })
        assertEquals(1_200L, BrainError.rateLimitBackoffMs(1) { 1.0 })
        assertEquals(1_600L, BrainError.rateLimitBackoffMs(2) { 0.0 })
        assertEquals(2_400L, BrainError.rateLimitBackoffMs(2) { 1.0 })
        val low = BrainError.RATE_LIMIT_BASE_MS * (1.0 - BrainError.RATE_LIMIT_JITTER)
        val high = BrainError.RATE_LIMIT_BASE_MS * (1.0 + BrainError.RATE_LIMIT_JITTER)
        assertTrue(BrainError.rateLimitBackoffMs(1) { 0.0 } >= low.toLong())
        assertTrue(BrainError.rateLimitBackoffMs(1) { 1.0 } <= high.toLong())
    }

    @Test
    fun waitMsHonorsRetryAfterAndTheTotalWaitCap() {
        val classified = BrainError.Classification(BrainError.Kind.RATE_LIMIT, retryAfterMs = 12_000L)
        assertEquals(12_000L, BrainError.waitMs(classified, attempt = 1, remainingBudgetMs = 60_000L) { 0.5 })
        assertEquals(5_000L, BrainError.waitMs(classified, attempt = 1, remainingBudgetMs = 5_000L) { 0.5 })
        val backoff = BrainError.Classification(BrainError.Kind.RATE_LIMIT)
        assertEquals(1_000L, BrainError.waitMs(backoff, attempt = 1, remainingBudgetMs = 60_000L) { 0.5 })
        assertEquals(0L, BrainError.waitMs(backoff, attempt = 1, remainingBudgetMs = 0L) { 0.5 })
    }

    @Test
    fun http429AndProviderRateLimitShapesAreRetryable() {
        val gemini = ProviderHttpException(
            429,
            """{"error":{"status":"RESOURCE_EXHAUSTED","message":"Quota exceeded for GenerateRequestsPerMinutePerProjectPerModel","details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"8s"}]}}""",
            retryAfter = "8",
        )
        val classified = BrainError.classify(gemini)
        assertEquals(BrainError.Kind.RATE_LIMIT, classified.kind)
        assertEquals(8_000L, classified.retryAfterMs)
        assertTrue(BrainError.retryable(gemini))

        val openai = IllegalStateException("""HTTP 429: {"error":{"code":"rate_limit_exceeded","message":"Rate limit reached"}}""")
        assertEquals(BrainError.Kind.RATE_LIMIT, BrainError.kind(openai))
        assertTrue(BrainError.retryable(openai))

        val anthropic = IllegalStateException("""{"type":"error","error":{"type":"rate_limit_error","message":"Number of request tokens has exceeded your per-minute rate limit"}}""")
        assertEquals(BrainError.Kind.RATE_LIMIT, BrainError.kind(anthropic))
        assertTrue(BrainError.retryable(anthropic))
    }

    @Test
    fun hardQuotaAndAuthErrorsAreNotRetried() {
        val daily = ProviderHttpException(
            429,
            """{"error":{"status":"RESOURCE_EXHAUSTED","message":"GenerateRequestsPerDayPerProjectPerModel quota exceeded"}}""",
        )
        assertEquals(BrainError.Kind.HARD_QUOTA, BrainError.kind(daily))
        assertFalse(BrainError.retryable(daily))

        val billing = IllegalStateException("""{"error":{"code":"insufficient_quota","message":"You exceeded your current quota, please check your plan and billing details."}}""")
        assertEquals(BrainError.Kind.HARD_QUOTA, BrainError.kind(billing))
        assertFalse(BrainError.retryable(billing))

        val forbidden = ProviderHttpException(403, "Forbidden")
        assertEquals(BrainError.Kind.AUTH, BrainError.kind(forbidden))
        assertFalse(BrainError.retryable(forbidden))

        val badKey = IllegalStateException("invalid_api_key: the key was rejected")
        assertEquals(BrainError.Kind.AUTH, BrainError.kind(badKey))
        assertFalse(BrainError.retryable(badKey))
    }

    @Test
    fun rateLimitStatusNamesTheRemainingTime() {
        assertEquals("Waiting for rate limit… 12s", BrainError.statusLine(12_000L))
        assertEquals("Waiting for rate limit… 1s", BrainError.statusLine(200L))
        assertEquals("Waiting for rate limit…", BrainError.statusLine(0L))
    }
}
