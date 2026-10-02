package app.pony.companion.voice

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OwnerRequest(
    val id: String,
    val text: String,
    val source: String,
    val createdAt: Long,
)

data class InboxState(
    val queued: List<OwnerRequest> = emptyList(),
    val waiters: Int = 0,
    val lastWaitEndedAt: Long = 0L,
)

/**
 * Owner requests waiting for the paired assistant's `wait_for_request`.
 * A request stays queued until an assistant takes it, the owner cancels it,
 * a brain on the phone takes it over, or [ttlMs] passes.
 */
class RequestInbox(
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val listenGraceMs: Long = LISTEN_GRACE_MS,
) {
    private val lock = Object()
    private val queue = ArrayDeque<OwnerRequest>()
    private var waiters = 0
    private var lastWaitEndedAt = 0L
    private val deliveries = CopyOnWriteArrayList<(OwnerRequest) -> Unit>()
    private val _state = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = _state.asStateFlow()

    fun submit(
        text: String,
        source: String,
        id: String = UUID.randomUUID().toString(),
        createdAt: Long = clock(),
    ): OwnerRequest {
        val request = OwnerRequest(id, text, source, createdAt)
        synchronized(lock) {
            dropExpired()
            queue.removeAll { it.id == id }
            queue.addLast(request)
            publish()
            lock.notifyAll()
        }
        return request
    }

    fun isQueued(id: String): Boolean = synchronized(lock) { queue.any { it.id == id } }

    /** Removes a queued request so something else can handle it. Null if an assistant already took it. */
    fun take(id: String): OwnerRequest? = synchronized(lock) {
        val found = queue.firstOrNull { it.id == id } ?: return null
        queue.remove(found)
        publish()
        found
    }

    fun cancel(id: String): Boolean = take(id) != null

    /** Returns a request that was taken but never reached the assistant. It goes out first next time. */
    fun putBack(request: OwnerRequest) {
        synchronized(lock) {
            if (queue.none { it.id == request.id }) queue.addFirst(request)
            publish()
            lock.notifyAll()
        }
    }

    /** Blocks for the next request. The caller counts as a listening assistant until it returns. */
    fun awaitNext(timeoutMs: Long, cancelled: () -> Boolean = { false }): OwnerRequest? {
        val taken: OwnerRequest?
        synchronized(lock) {
            waiters += 1
            publish()
            try {
                val deadline = System.nanoTime() + timeoutMs * 1_000_000
                while (true) {
                    dropExpired()
                    if (queue.isNotEmpty() || cancelled()) break
                    val remaining = (deadline - System.nanoTime()) / 1_000_000
                    if (remaining <= 0) break
                    lock.wait(minOf(remaining, POLL_SLICE_MS))
                }
                taken = if (cancelled()) null else queue.removeFirstOrNull()
            } finally {
                waiters -= 1
                lastWaitEndedAt = clock()
                publish()
            }
        }
        if (taken != null) deliveries.forEach { it(taken) }
        return taken
    }

    /** An assistant is inside `wait_for_request`, or left it moments ago and is expected back. */
    fun listening(now: Long = clock()): Boolean = synchronized(lock) {
        waiters > 0 || (lastWaitEndedAt > 0 && now - lastWaitEndedAt < listenGraceMs)
    }

    fun onDelivered(listener: (OwnerRequest) -> Unit) {
        deliveries += listener
    }

    /** The session ended. Queued requests stay for the next assistant; the listener is gone. */
    fun forgetListener() {
        synchronized(lock) {
            lastWaitEndedAt = 0L
            publish()
            lock.notifyAll()
        }
    }

    fun wake() {
        synchronized(lock) { lock.notifyAll() }
    }

    fun queued(): List<OwnerRequest> = synchronized(lock) {
        dropExpired()
        queue.toList()
    }

    private fun dropExpired() {
        val now = clock()
        val before = queue.size
        queue.removeAll { now - it.createdAt > ttlMs }
        if (queue.size != before) publish()
    }

    private fun publish() {
        _state.value = InboxState(queue.toList(), waiters, lastWaitEndedAt)
    }

    companion object {
        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
        const val LISTEN_GRACE_MS = 90_000L
        private const val POLL_SLICE_MS = 500L
    }
}

/** The phone-wide inbox the relay session and the Ask screen share. */
object VoiceBus {
    val inbox = RequestInbox()
    val state: StateFlow<InboxState> get() = inbox.state

    fun submit(text: String, source: String): OwnerRequest = inbox.submit(text, source)

    fun awaitNext(timeoutMs: Long, cancelled: () -> Boolean = { false }): OwnerRequest? =
        inbox.awaitNext(timeoutMs, cancelled)

    fun listening(): Boolean = inbox.listening()
}
