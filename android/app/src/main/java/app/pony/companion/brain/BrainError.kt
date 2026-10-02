package app.pony.companion.brain

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns a model-call failure into something the loop can act on: whether it's
 * worth retrying (a timeout or a network blip), how long to wait before the
 * next try, and a plain sentence for the owner instead of a raw "timeout".
 */
object BrainError {
    enum class Kind { TIMEOUT, NETWORK, OTHER }

    const val BASE_BACKOFF_MS = 600L
    const val MAX_BACKOFF_MS = 4_000L

    fun kind(err: Throwable): Kind {
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

    /** A timeout or a transient network error is worth another try; a bad key or refusal is not. */
    fun retryable(err: Throwable): Boolean = kind(err) != Kind.OTHER

    /** A growing, capped backoff for the nth retry (1-based). */
    fun backoffMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 16)
        return (BASE_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
    }

    /** What to show the owner once the retries are spent, in plain words. */
    fun message(err: Throwable): String = when (kind(err)) {
        Kind.TIMEOUT -> "The brain took too long to answer — try again."
        Kind.NETWORK -> "I couldn't reach the brain just now. Check your connection and try again."
        Kind.OTHER -> err.message?.takeIf { it.isNotBlank() } ?: "The brain didn't answer."
    }
}
