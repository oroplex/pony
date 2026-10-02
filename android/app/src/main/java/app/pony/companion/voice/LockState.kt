package app.pony.companion.voice

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object LockState {
    fun isLocked(context: Context): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return false
        return keyguard.isKeyguardLocked
    }

    /** Waits for the owner to unlock. Does not dismiss or bypass the lock screen. */
    fun awaitUnlock(context: Context, timeoutMs: Long = 120_000, cancelled: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cancelled()) return false
            if (!isLocked(context)) return true
            try {
                Thread.sleep(400)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return !isLocked(context)
    }

    /** Plugged in counts, including a full battery sitting on a charger. */
    fun isPlugged(context: Context): Boolean {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0
    }
}
