package app.pony.companion.session

/**
 * Per-direction AEAD counters and the executed-id denylist. Pure so the
 * "don't run a replayed tap" rule is unit-tested without Android.
 */
class DirectionCounter {
    private var send = 0L
    private var recv = 0L

    @Synchronized
    fun nextSend(): Long {
        send += 1
        return send
    }

    @Synchronized
    fun accept(seq: Long?): Boolean {
        if (seq == null || seq <= 0L) return false
        if (seq <= recv) return false
        recv = seq
        return true
    }

    @Synchronized
    fun lastReceived(): Long = recv
}

class ExecutedIds(private val limit: Int = 4_096) {
    private val ids = LinkedHashSet<String>()

    @Synchronized
    fun remember(id: String): Boolean {
        if (id.isBlank() || !ids.add(id)) return false
        while (ids.size > limit) ids.remove(ids.first())
        return true
    }

    @Synchronized
    fun has(id: String): Boolean = ids.contains(id)
}
