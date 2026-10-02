package app.pony.companion.display

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.pony.companion.R
import java.util.concurrent.atomic.AtomicInteger

/** Ongoing notice while a background action is in progress. Idle phones stay quiet. */
object BackgroundNotifier {
    private const val CHANNEL = "pony_background"
    private const val NOTIF_ID = 62
    const val ACTION_STOP = "app.pony.companion.action.BACKGROUND_STOP"

    private val holds = AtomicInteger()
    private val ops = AtomicInteger()

    fun hold(context: Context) {
        holds.incrementAndGet()
    }

    fun releaseHold(context: Context) {
        if (holds.decrementAndGet() <= 0) {
            holds.set(0)
            if (ops.get() == 0) hide(context)
        }
    }

    fun begin(context: Context, text: String) {
        ops.incrementAndGet()
        show(context, text)
    }

    fun end(context: Context) {
        if (ops.decrementAndGet() <= 0) {
            ops.set(0)
            if (holds.get() == 0) hide(context)
        }
    }

    fun show(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Pony is working", NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getBroadcast(
            context,
            6,
            Intent(context, BackgroundStopReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notice = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pony)
            .setContentTitle("Pony is working")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stop)
            .build()
        manager.notify(NOTIF_ID, notice)
    }

    fun hide(context: Context) {
        holds.set(0)
        ops.set(0)
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
    }
}
