package app.pony.companion.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.pony.companion.a11y.PonyAccessibilityService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Lifecycle for a Compose view that lives outside any activity. */
class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun start() {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/**
 * The live task pill over other apps. It is owned by the accessibility
 * service: when that service is destroyed or rebound, the view goes with it
 * and the next [show] builds a fresh one.
 */
object OverlayHost {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var service: AccessibilityService? = null
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var params: WindowManager.LayoutParams? = null
    @Volatile private var wanted = false
    /** The pill only takes touches while it's showing something to answer (Confirm/Note/Listening). */
    @Volatile private var interactive = false

    val attached: Boolean get() = view?.isAttachedToWindow == true

    fun attach(next: AccessibilityService) {
        main.post {
            if (service !== next) removeNow()
            service = next
            if (wanted) addNow()
        }
    }

    fun detach(gone: AccessibilityService) {
        runOnMain {
            if (service === gone) {
                removeNow()
                service = null
            }
        }
    }

    fun show() {
        wanted = true
        main.post { if (!attached) addNow() }
    }

    fun hide() {
        wanted = false
        main.post { removeNow() }
    }

    fun covers(x: Float, y: Float): Boolean {
        val v = view ?: return false
        if (!v.isAttachedToWindow) return false
        val location = IntArray(2)
        v.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + v.width, location[1] + v.height).contains(x.toInt(), y.toInt())
    }

    /**
     * Whether the pill accepts touches. It passes them through to the app
     * underneath — so it never covers an app's top bar or makes a protected
     * switch drop a tap — unless it's asking the owner something.
     */
    fun setInteractive(value: Boolean) {
        interactive = value
        setTouchable(value)
    }

    /** Lets a gesture reach the app under the pill, then restores its touch state. */
    fun <T> passThrough(block: () -> T): T {
        setTouchable(false)
        return try {
            Thread.sleep(32)
            block()
        } finally {
            setTouchable(interactive)
        }
    }

    private fun setTouchable(touchable: Boolean) {
        runOnMain {
            val v = view ?: return@runOnMain
            val p = params ?: return@runOnMain
            p.flags = if (touchable) {
                p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            runCatching { service?.getSystemService(WindowManager::class.java)?.updateViewLayout(v, p) }
        }
    }

    private fun addNow() {
        val host = service ?: return
        if (attached) return
        removeNow()
        val lifecycle = OverlayOwner().also { it.start() }
        val compose = ComposeView(host).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setViewTreeViewModelStoreOwner(lifecycle)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { LiveOverlay(onCollapsed = { }) }
        }
        val top = (host as? PonyAccessibilityService)?.statusBarHeight() ?: 0
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = top + (8 * host.resources.displayMetrics.density).toInt()
        }
        try {
            host.getSystemService(WindowManager::class.java).addView(compose, layout)
            view = compose
            owner = lifecycle
            params = layout
            setTouchable(interactive)
        } catch (_: Throwable) {
            lifecycle.stop()
        }
    }

    private fun removeNow() {
        val v = view
        view = null
        params = null
        owner?.stop()
        owner = null
        if (v != null) {
            runCatching { service?.getSystemService(WindowManager::class.java)?.removeViewImmediate(v) }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        main.post {
            try {
                block()
            } finally {
                latch.countDown()
            }
        }
        latch.await(1, TimeUnit.SECONDS)
    }
}
