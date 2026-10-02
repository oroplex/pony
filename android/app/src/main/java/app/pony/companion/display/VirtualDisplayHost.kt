package app.pony.companion.display

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread

/**
 * Pony's own virtual display. Android lets a normal app put only its own
 * activities here: other apps are refused by
 * [android.app.ActivityManager.isActivityStartAllowedOnDisplay], which allows a
 * foreign activity only on a *trusted* display. A display is trusted only when
 * its creator holds the signature|privileged ADD_TRUSTED_DISPLAY permission, so
 * a normal app's display is untrusted by design (it stops tap-jacking from an
 * off-screen surface). That is why Settings, Google, Gboard and the rest bounce
 * back, and why the real out-of-sight path is Shizuku (a shell-owned trusted
 * display). This host still asks for a trusted display as a best effort — the
 * flag is honoured on rooted or privileged builds and silently dropped on a
 * stock phone, where Pony then falls back to a maximized pop-up.
 */
class VirtualDisplayHost(private val context: Context) {
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null

    fun ensure(width: Int, height: Int, density: Int): Int? {
        display?.display?.displayId?.let { if (it > 0) return it }
        return try {
            val worker = HandlerThread("pony-display").also { it.start() }
            thread = worker
            val handler = Handler(worker.looper)
            val images = ImageReader.newInstance(width.coerceAtLeast(1), height.coerceAtLeast(1), PixelFormat.RGBA_8888, 2)
            images.setOnImageAvailableListener({ imageReader ->
                imageReader.acquireLatestImage()?.close()
            }, handler)
            val base = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            // Try trusted first (lets other apps' activities start here), then the
            // plain untrusted display if the platform refuses the flag.
            val created = create(width, height, density, images.surface, base or FLAG_TRUSTED)
                ?: create(width, height, density, images.surface, base)
            if (created == null) {
                images.close()
                worker.quitSafely()
                thread = null
                null
            } else {
                reader = images
                display = created
                created.display.displayId
            }
        } catch (_: Throwable) {
            release()
            null
        }
    }

    private fun create(width: Int, height: Int, density: Int, surface: android.view.Surface, flags: Int): VirtualDisplay? = runCatching {
        context.getSystemService(DisplayManager::class.java)
            .createVirtualDisplay("Pony", width, height, density, surface, flags)
    }.getOrNull()

    fun release() {
        runCatching { display?.release() }
        display = null
        runCatching { reader?.close() }
        reader = null
        thread?.quitSafely()
        thread = null
    }

    private companion object {
        /**
         * Hidden VIRTUAL_DISPLAY_FLAG_TRUSTED (1 shl 10). Referenced by value
         * because it isn't in the public SDK; the platform clears it unless the
         * caller holds ADD_TRUSTED_DISPLAY, so passing it is safe on stock.
         */
        const val FLAG_TRUSTED = 1 shl 10
    }
}
