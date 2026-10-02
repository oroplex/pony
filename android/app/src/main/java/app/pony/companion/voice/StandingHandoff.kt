package app.pony.companion.voice

/**
 * The last request handed to a standing listener (`wait_for_request` with
 * `listen`). Each poll acks the last request the listener actually received.
 * A poll that doesn't ack the one handed out means the reply was lost in a
 * drop, so that request goes out again. Listeners drop repeats by id.
 */
class StandingHandoff(
    private val clock: () -> Long = System::currentTimeMillis,
    private val keepMs: Long = KEEP_MS,
) {
    private var pending: OwnerRequest? = null
    private var handedAt = 0L

    /** The request to hand out again, or null to wait for a new one. [stillOpen] says whether its task is still going. */
    @Synchronized
    fun redeliver(ack: String?, stillOpen: (String) -> Boolean = { true }): OwnerRequest? {
        val current = pending ?: return null
        if (ack != null && ack == current.id) {
            pending = null
            return null
        }
        if (clock() - handedAt > keepMs || !stillOpen(current.id)) {
            pending = null
            return null
        }
        return current
    }

    @Synchronized
    fun handedOut(request: OwnerRequest) {
        pending = request
        handedAt = clock()
    }

    /** The assistant finished [id], or everything when null. */
    @Synchronized
    fun finished(id: String?) {
        if (id == null || pending?.id == id) pending = null
    }

    @Synchronized
    fun pendingId(): String? = pending?.id

    companion object {
        const val KEEP_MS = 10 * 60 * 1000L
    }
}
