package app.pony.companion.display

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread

/**
 * Runs inside Shizuku as the shell user. That is the optional path that can
 * create a trusted display on Android 16 (and Android 14 and below, and custom
 * ROMs that grant the shell the display permissions), the same kind scrcpy uses.
 * A trusted display is the only kind that hosts other apps' activities; on a
 * plain app-owned display the window manager refuses them. While a trusted
 * display is up it also flips the "force resizable activities" developer setting
 * so portrait-locked apps open there, and restores it on release.
 * Missing Shizuku must never crash the app: nothing here runs until the owner turns the option on.
 */
class PonyDisplayUserService(private val context: Context) : IPonyDisplay.Stub() {
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    @Volatile private var isTrusted = false
    private var priorResizable: String? = null
    private var changedResizable = false

    override fun ensureDisplay(width: Int, height: Int, density: Int): Int {
        display?.display?.displayId?.let { if (it > 0) return it }
        val trusted = create(width, height, density, ShellLaunch.displayFlags(trusted = true))
        isTrusted = trusted != null
        val created = trusted
            ?: create(width, height, density, ShellLaunch.displayFlags(trusted = false))
            ?: return -1
        display = created
        if (isTrusted) forceResizable(true)
        return created.display.displayId
    }

    override fun trusted(): Boolean = isTrusted && display != null

    override fun launch(component: String, displayId: Int): Boolean {
        if (component.isBlank() || displayId <= 0) return false
        return exec(ShellLaunch.startArgs(component, displayId))
    }

    override fun pressKey(displayId: Int, keyCode: Int): Boolean {
        if (displayId <= 0 || keyCode <= 0) return false
        return exec(arrayOf("input", "-d", displayId.toString(), "keyevent", keyCode.toString()))
    }

    override fun releaseDisplay() {
        if (changedResizable) forceResizable(false)
        runCatching { display?.release() }
        display = null
        isTrusted = false
        runCatching { reader?.close() }
        reader = null
        thread?.quitSafely()
        thread = null
    }

    override fun destroy() {
        releaseDisplay()
        System.exit(0)
    }

    private fun create(width: Int, height: Int, density: Int, flags: Int): VirtualDisplay? {
        return try {
            val worker = thread ?: HandlerThread("pony-shell-display").also {
                it.start()
                thread = it
            }
            val images = reader ?: ImageReader.newInstance(
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                PixelFormat.RGBA_8888,
                2,
            ).also {
                it.setOnImageAvailableListener({ imageReader ->
                    imageReader.acquireLatestImage()?.close()
                }, Handler(worker.looper))
                reader = it
            }
            context.getSystemService(DisplayManager::class.java)
                .createVirtualDisplay("Pony shell", width, height, density, images.surface, flags)
        } catch (_: SecurityException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    /** Remembers the owner's setting the first time it changes, so release puts it back. */
    private fun forceResizable(on: Boolean) {
        if (on) {
            if (!changedResizable) {
                priorResizable = execOut(ShellLaunch.readResizableArgs())
                changedResizable = true
            }
            exec(ShellLaunch.resizableArgs(true))
        } else {
            exec(ShellLaunch.resizableArgs(ShellLaunch.resizableWasOn(priorResizable)))
            changedResizable = false
            priorResizable = null
        }
    }

    private fun exec(argv: Array<String>): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(argv)
            proc.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun execOut(argv: Array<String>): String? {
        return try {
            val proc = Runtime.getRuntime().exec(argv)
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor()
            out
        } catch (_: Throwable) {
            null
        }
    }
}
