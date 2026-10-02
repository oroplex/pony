package app.pony.companion.display

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import app.pony.companion.BuildConfig
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Optional. Off until the owner installs Shizuku and taps Grant. */
object ShizukuBridge {
    private val remote = AtomicReference<IPonyDisplay?>(null)
    private var args: Shizuku.UserServiceArgs? = null
    private var bound: ServiceConnection? = null

    fun installed(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission(requestCode: Int) {
        Shizuku.requestPermission(requestCode)
    }

    fun ensureDisplay(context: Context, width: Int, height: Int, density: Int): Int {
        val service = bind(context) ?: return -1
        return try {
            service.ensureDisplay(width, height, density)
        } catch (_: Throwable) {
            -1
        }
    }

    fun launch(context: Context, component: String, displayId: Int): Boolean {
        val service = bind(context) ?: return false
        return try {
            service.launch(component, displayId)
        } catch (_: Throwable) {
            false
        }
    }

    /** True only when the shell could create a trusted display, the one kind that hosts other apps. */
    fun trusted(context: Context): Boolean {
        val service = remote.get() ?: bind(context) ?: return false
        return try {
            service.trusted()
        } catch (_: Throwable) {
            false
        }
    }

    fun pressKey(context: Context, displayId: Int, keyCode: Int): Boolean {
        if (!granted()) return false
        val service = remote.get() ?: bind(context) ?: return false
        return try {
            service.pressKey(displayId, keyCode)
        } catch (_: Throwable) {
            false
        }
    }

    fun release(context: Context) {
        val service = remote.getAndSet(null)
        runCatching { service?.releaseDisplay() }
        val current = args
        val connection = bound
        if (current != null && connection != null) {
            runCatching { Shizuku.unbindUserService(current, connection, true) }
        }
        args = null
        bound = null
    }

    private fun bind(context: Context): IPonyDisplay? {
        remote.get()?.let { return it }
        if (!granted()) return null
        synchronized(this) {
            remote.get()?.let { return it }
            val latch = CountDownLatch(1)
            val next = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, PonyDisplayUserService::class.java.name),
            )
                .daemon(false)
                .processNameSuffix("display")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
                .tag("pony-display")
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder != null) remote.set(IPonyDisplay.Stub.asInterface(binder))
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    remote.set(null)
                }
            }
            args = next
            bound = connection
            return try {
                Shizuku.bindUserService(next, connection)
                if (!latch.await(4, TimeUnit.SECONDS)) null else remote.get()
            } catch (_: Throwable) {
                null
            }
        }
    }
}
