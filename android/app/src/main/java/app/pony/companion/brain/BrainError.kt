package app.pony.companion.brain

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A failed provider HTTP call, with the status, sanitized body, and optional
 * Retry-After header so [BrainError] can tell a 429 from a hard quota.
 */
class ProviderHttpException(
    val status: Int,
    val body: String,
    val retryAfter: String? = null,
    detail: String = defaultMessage(status, body),
) : IllegalStateException(detail) {
    companion object {
        fun defaultMessage(status: Int, body: String): String {
            val snippet = body.trim().take(240)
            return if (snippet.isEmpty() || snippet == "HTTP $status") "HTTP $status"
            else "HTTP $status: $snippet"
        }
    }
}

/**
 * Turns a model-call failure into something the loop can act on: whether it's
 * worth retrying (a timeout, a network blip, or a per-minute rate limit), how
 * long to wait, and a plain sentence for the owner instead of a raw "timeout".
 */
object BrainError {
    enum class Kind { TIMEOUT, NETWORK, RATE_LIMIT, HARD_QUOTA, AUTH, OTHER }

    data class Classification(
        val kind: Kind,
        val retryAfterMs: Long? = null,
    )

    const val BASE_BACKOFF_MS = 600L
    const val MAX_BACKOFF_MS = 4_000L

    /** Rate-limit retries for one model step, not counting the first try. */
    const val RATE_LIMIT_RETRIES = 5

    /** Cap on time spent waiting for rate limits during one model step. */
    const val RATE_LIMIT_MAX_WAIT_MS = 60_000L

    const val RATE_LIMIT_BASE_MS = 1_000L

    /** Backoff is multiplied by `1 ± JITTER`, so 0.2 is ±20%. */
    const val RATE_LIMIT_JITTER = 0.2

    fun kind(err: Throwable): Kind = classify(err).kind

    fun classify(err: Throwable, nowMs: Long = System.currentTimeMillis()): Classification {
        val http = err as? ProviderHttpException
        val status = http?.status ?: statusFrom(err.message)
        val body = http?.body ?: ""
        val blob = buildString {
            append(err.message.orEmpty())
            if (body.isNotBlank() && !err.message.orEmpty().contains(body)) {
                append('\n').append(body)
            }
        }
        val lower = blob.lowercase()

        if (isAuth(status, lower)) return Classification(Kind.AUTH)
        if (isHardQuota(lower)) return Classification(Kind.HARD_QUOTA)
        if (isRateLimit(status, lower)) {
            return Classification(Kind.RATE_LIMIT, retryDelayMs(http?.retryAfter, blob, nowMs))
        }
        return Classification(legacyKind(err))
    }

    /** A timeout, a dropped connection, or a per-minute rate limit is worth another try. */
    fun retryable(err: Throwable): Boolean = when (kind(err)) {
        Kind.TIMEOUT, Kind.NETWORK, Kind.RATE_LIMIT -> true
        else -> false
    }

    /** A growing, capped backoff for the nth network/timeout retry (1-based). */
    fun backoffMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 16)
        return (BASE_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
    }

    /**
     * Exponential backoff with jitter for the nth rate-limit retry (1-based).
     * [random] is in `[0, 1]` and is injectable so tests can pin the bounds.
     */
    fun rateLimitBackoffMs(attempt: Int, random: () -> Double = { Math.random() }): Long {
        val shift = (attempt - 1).coerceIn(0, 16)
        val exp = RATE_LIMIT_BASE_MS shl shift
        val unit = random().coerceIn(0.0, 1.0)
        val factor = 1.0 + RATE_LIMIT_JITTER * (2.0 * unit - 1.0)
        return (exp * factor).toLong().coerceAtLeast(0L)
    }

    /**
     * How long to sleep before the next rate-limit retry: Retry-After or a
     * provider hint when present, otherwise jittered backoff, never more than
     * [remainingBudgetMs].
     */
    fun waitMs(
        classification: Classification,
        attempt: Int,
        remainingBudgetMs: Long,
        random: () -> Double = { Math.random() },
    ): Long {
        val suggested = classification.retryAfterMs?.takeIf { it > 0 }
            ?: rateLimitBackoffMs(attempt, random)
        return suggested.coerceAtMost(remainingBudgetMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    /**
     * Retry-After as milliseconds. Accepts a delta in seconds, an HTTP-date,
     * or a provider hint in [body] (`retryDelay`, `retry_after_ms`, …).
     */
    fun retryDelayMs(retryAfter: String?, body: String, nowMs: Long = System.currentTimeMillis()): Long? {
        parseRetryAfter(retryAfter, nowMs)?.let { return it }
        return parseRetryHint(body)
    }

    fun parseRetryAfter(value: String?, nowMs: Long = System.currentTimeMillis()): Long? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        trimmed.toLongOrNull()?.let { return (it * 1_000L).coerceAtLeast(0L) }
        trimmed.toDoubleOrNull()?.let { return (it * 1_000.0).toLong().coerceAtLeast(0L) }
        val at = runCatching {
            ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull() ?: return null
        return (at - nowMs).coerceAtLeast(0L)
    }

    fun parseRetryHint(body: String): Long? {
        DURATION_HINT.find(body)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let {
            return (it * 1_000.0).toLong().coerceAtLeast(0L)
        }
        RETRY_AFTER_MS.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
            return it.coerceAtLeast(0L)
        }
        RETRY_AFTER_SEC.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
            return (it * 1_000L).coerceAtLeast(0L)
        }
        RETRY_DELAY_SECONDS_OBJ.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
            return (it * 1_000L).coerceAtLeast(0L)
        }
        return null
    }

    fun statusLine(remainingMs: Long): String {
        val secs = (remainingMs + 999) / 1_000
        return if (secs >= 1) "Waiting for rate limit… ${secs}s" else "Waiting for rate limit…"
    }

    /** What to show the owner once the retries are spent, in plain words. */
    fun message(err: Throwable): String = when (kind(err)) {
        Kind.TIMEOUT -> "The brain took too long to answer — try again."
        Kind.NETWORK -> "I couldn't reach the brain just now. Check your connection and try again."
        Kind.RATE_LIMIT -> "The brain is rate-limited right now — try again in a moment."
        Kind.HARD_QUOTA -> "The brain's quota is used up — check the plan and billing."
        Kind.AUTH -> err.message?.takeIf { it.isNotBlank() } ?: "The brain key was rejected."
        Kind.OTHER -> err.message?.takeIf { it.isNotBlank() } ?: "The brain didn't answer."
    }

    private fun legacyKind(err: Throwable): Kind {
        val message = err.message?.lowercase().orEmpty()
        return when {
            err is SocketTimeoutException -> Kind.TIMEOUT
            err is InterruptedIOException ->
                if (message.contains("timeout") || message.contains("timed out")) Kind.TIMEOUT else Kind.NETWORK
            err is UnknownHostException || err is ConnectException || err is SocketException -> Kind.NETWORK
            message.contains("timeout") || message.contains("timed out") -> Kind.TIMEOUT
            message.contains("unable to resolve host") || message.contains("failed to connect") ||
                message.contains("connection reset") || message.contains("network is unreachable") -> Kind.NETWORK
            err is IOException -> Kind.NETWORK
            else -> Kind.OTHER
        }
    }

    private fun statusFrom(message: String?): Int? =
        HTTP_STATUS.find(message.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun isAuth(status: Int?, lower: String): Boolean {
        if (status == 401 || status == 403) return true
        return AUTH_MARKERS.any { it in lower }
    }

    private fun isHardQuota(lower: String): Boolean {
        if (HARD_QUOTA_MARKERS.any { it in lower }) return true
        if (lower.contains("billing") && (lower.contains("quota") || lower.contains("exceeded") || lower.contains("plan"))) {
            return true
        }
        if (DAILY_MARKERS.any { it in lower }) return true
        if (lower.contains("exceeded your current quota") && !isPerMinute(lower)) return true
        return false
    }

    private fun isRateLimit(status: Int?, lower: String): Boolean {
        if (status == 429) return true
        if (RATE_LIMIT_MARKERS.any { it in lower }) return true
        if (lower.contains("resource_exhausted") && (isPerMinute(lower) || !isHardQuota(lower))) return true
        return false
    }

    private fun isPerMinute(lower: String): Boolean = PER_MINUTE_MARKERS.any { it in lower }

    private val HTTP_STATUS = Regex("""HTTP\s+(\d{3})""", RegexOption.IGNORE_CASE)
    private val DURATION_HINT = Regex(""""retryDelay"\s*:\s*"([0-9.]+)\s*s"""", RegexOption.IGNORE_CASE)
    private val RETRY_AFTER_MS = Regex(""""retry_after_ms"\s*:\s*([0-9]+)""", RegexOption.IGNORE_CASE)
    private val RETRY_AFTER_SEC = Regex(""""retry_after"\s*:\s*"?([0-9]+)""", RegexOption.IGNORE_CASE)
    private val RETRY_DELAY_SECONDS_OBJ = Regex(""""retryDelay"\s*:\s*\{\s*"seconds"\s*:\s*([0-9]+)""", RegexOption.IGNORE_CASE)

    private val AUTH_MARKERS = listOf(
        "invalid api key",
        "invalid_api_key",
        "incorrect api key",
        "authentication_error",
        "invalid x-api-key",
        "api key not valid",
        "api_key_invalid",
    )

    private val HARD_QUOTA_MARKERS = listOf(
        "insufficient_quota",
        "insufficient quota",
        "billing_not_active",
        "quota exceeded for your current billing",
    )

    private val DAILY_MARKERS = listOf(
        "per day",
        "perday",
        "per-day",
        "daily quota",
        "daily_quota",
        "requestdaily",
        "requests per day",
    )

    private val RATE_LIMIT_MARKERS = listOf(
        "rate_limit_exceeded",
        "rate_limit_error",
        "rate limit",
        "rate-limit",
        "ratelimit",
        "too many requests",
        "resource has been exhausted",
    )

    private val PER_MINUTE_MARKERS = listOf(
        "per minute",
        "perminute",
        "per-minute",
        "per_minute",
        "requests per minute",
    )
}
