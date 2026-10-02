package app.pony.companion.display

import android.content.Context

/** Wakes waiters when the window list changes, instead of polling blindly. */
object WindowSignal {
    private val lock = Object()
    private var serial = 0L

    fun changed() {
        synchronized(lock) {
            serial += 1
            lock.notifyAll()
        }
    }

    fun await(maxMs: Long) {
        synchronized(lock) {
            val start = serial
            val deadline = System.currentTimeMillis() + maxMs
            while (serial == start) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return
                lock.wait(remaining)
            }
        }
    }

    /** Waits up to [maxMs] for the next window change; true if one happened, false if it stayed quiet. */
    fun awaitChange(maxMs: Long): Boolean {
        synchronized(lock) {
            val start = serial
            val deadline = System.currentTimeMillis() + maxMs
            while (serial == start) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) return false
                lock.wait(remaining)
            }
            return true
        }
    }
}

/** Hooks a caller passes so a long wait can report progress and honor Stop. */
interface Watch {
    fun deferred(reason: String, waitedMs: Long) = Unit
    fun cancelled(): Boolean = false

    companion object {
        val None = object : Watch {}
    }
}

/**
 * Waiting instead of failing. Calls and pop-ups interrupt a tap, not a task:
 * main-screen touches wait for the call screen to leave (up to [CALL_LIMIT_MS]),
 * and a tap waits up to [COVER_WAIT_MS] for a heads-up or alarm to move.
 */
object ScreenGuard {
    const val CALL_LIMIT_MS = 10 * 60 * 1000L
    const val COVER_WAIT_MS = 3_000L
    private const val COVER_POLL_MS = 250L
    private const val CALL_POLL_MS = 1_000L
    private const val NOTICE_EVERY_MS = 15_000L

    data class Wait(val ok: Boolean, val waitedMs: Long, val error: String? = null)

    data class CoverWait(val cover: Cover, val waitedMs: Long)

    @Volatile var callLimitMs: Long = CALL_LIMIT_MS

    fun awaitCallScreen(context: Context, watch: Watch, inFront: () -> Boolean = { CallGuard.callScreenInFront(context) }): Wait {
        if (!inFront()) return Wait(true, 0)
        val start = System.currentTimeMillis()
        var noticed = -NOTICE_EVERY_MS
        while (true) {
            val waited = System.currentTimeMillis() - start
            if (watch.cancelled()) return Wait(false, waited, "stopped")
            if (!inFront()) return Wait(true, waited)
            if (waited >= callLimitMs) return Wait(false, waited, DisplayPolicy.CALL_UI_TIMEOUT)
            if (waited - noticed >= NOTICE_EVERY_MS) {
                noticed = waited
                watch.deferred(DisplayPolicy.CALL_UI_FOREGROUND, waited)
            }
            WindowSignal.await(CALL_POLL_MS)
        }
    }

    fun awaitUncovered(
        watch: Watch,
        limitMs: Long = COVER_WAIT_MS,
        check: () -> Cover,
    ): CoverWait {
        val start = System.currentTimeMillis()
        var cover = check()
        if (cover is Cover.Popup) watch.deferred(DisplayPolicy.COVERED_BY_POPUP, 0)
        while (cover is Cover.Popup) {
            val waited = System.currentTimeMillis() - start
            if (watch.cancelled() || waited >= limitMs) return CoverWait(cover, waited)
            WindowSignal.await(COVER_POLL_MS)
            cover = check()
        }
        return CoverWait(cover, System.currentTimeMillis() - start)
    }
}
