package app.pony.companion.session

/**
 * Per-action time-to-live and screen fingerprint. Pure so the "don't run a
 * stale tap five minutes later" rule is unit-tested without Android.
 *
 * A command that sat in the phone's single-thread queue after the connector
 * gave up must come back as `expired` and never touch the screen.
 */
object ActionExpiry {
    const val DEFAULT_TTL_MS = 15_000L
    const val OPEN_TTL_MS = 30_000L
    const val TYPE_TTL_MS = 45_000L
    const val IDLE_TTL_MS = 20_000L
    const val OWNER_PROMPT_MS = 60_000L
    const val OWNER_CLIENT_WAIT_MS = 70_000L
    const val CLOCK_SKEW_MS = 120_000L
    const val EXPIRED = "expired"
    const val CLOCK_SKEW = "clock_skew"
    const val SCREEN_CHANGED = "screen_changed"
    const val REPLAYED = "replayed"

    fun ttlMs(op: String?): Long = when (op) {
        "confirm", "ask_user" -> OWNER_CLIENT_WAIT_MS
        "open_app", "open_settings" -> OPEN_TTL_MS
        "type" -> TYPE_TTL_MS
        "wait_idle" -> IDLE_TTL_MS
        "wait_for_request" -> 0L
        else -> DEFAULT_TTL_MS
    }

    sealed class IssuedAt {
        data class Ok(val at: Long) : IssuedAt()
        data object Skewed : IssuedAt()
    }

    /**
     * Prefer the client's stamp when it is close to receive time. A missing
     * stamp falls back to when the phone got the frame. A wildly skewed stamp
     * is [IssuedAt.Skewed] — never restamped to now.
     */
    fun resolveIssuedAt(clientIssuedAt: Long?, receivedAt: Long): IssuedAt {
        if (clientIssuedAt == null || clientIssuedAt <= 0L) return IssuedAt.Ok(receivedAt)
        return if (kotlin.math.abs(clientIssuedAt - receivedAt) > CLOCK_SKEW_MS) IssuedAt.Skewed else IssuedAt.Ok(clientIssuedAt)
    }

    fun effectiveIssuedAt(clientIssuedAt: Long?, receivedAt: Long): Long {
        return when (val resolved = resolveIssuedAt(clientIssuedAt, receivedAt)) {
            is IssuedAt.Ok -> resolved.at
            IssuedAt.Skewed -> error(CLOCK_SKEW)
        }
    }

    fun expired(issuedAt: Long?, ttlMs: Long?, now: Long, fallbackTtlMs: Long): Boolean {
        val ttl = ttlMs ?: fallbackTtlMs
        if (ttl <= 0L) return false
        if (issuedAt == null || issuedAt <= 0L) return false
        return now - issuedAt > ttl
    }

    fun screenChanged(expectedPkg: String?, actualPkg: String?): Boolean {
        if (expectedPkg.isNullOrBlank() || actualPkg.isNullOrBlank()) return false
        return expectedPkg != actualPkg
    }
}
