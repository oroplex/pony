package app.pony.companion.input

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide link between the accessibility typer and [PonyInputMethodService].
 * Commit and keyboard switching run on the main thread. Waiting runs on the
 * caller thread, which must not be the main thread.
 */
object ImeBridge {
    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    var password: Boolean = false
        private set

    @Volatile
    var editorPackage: String? = null
        private set

    /** Set only while an agent type needs the keyboard. Ordinary focus must not pop it. */
    @Volatile
    var wantsKeyboard: Boolean = false
        private set

    private val main = Handler(Looper.getMainLooper())
    private val lock = Object()

    fun onStart(isPassword: Boolean, packageName: String?) {
        password = isPassword
        editorPackage = packageName
        active = true
        synchronized(lock) { lock.notifyAll() }
    }

    fun onStop() {
        active = false
        password = false
        editorPackage = null
        synchronized(lock) { lock.notifyAll() }
    }

    fun prepareToType() {
        wantsKeyboard = true
    }

    fun typingFinished() {
        wantsKeyboard = false
    }

    fun awaitActive(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (!active && System.currentTimeMillis() < deadline) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                lock.wait(remaining)
            }
            return active
        }
    }

    fun commit(text: String): Boolean = onMain { PonyInputMethodService.commitFromMain(text) }

    fun switchBack() {
        onMain {
            PonyInputMethodService.switchBackFromMain()
            true
        }
    }

    private fun onMain(block: () -> Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val ok = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        main.post {
            ok.set(block())
            latch.countDown()
        }
        latch.await(3, TimeUnit.SECONDS)
        return ok.get()
    }
}
