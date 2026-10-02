package app.pony.companion.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.pony.companion.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * On-device "Hey Pony" spotter. Audio stays in this process until the phrase hits.
 * Vosk scores it locally. Nothing is uploaded.
 */
class WakeWordService : Service() {
    private val running = AtomicBoolean(false)
    private val thread = Thread(::loop, "pony-wake")
    private var plugged = false
    private var lastNotified = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        plugged = LockState.isPlugged(this)
        createChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(powerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(powerReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        plugged = LockState.isPlugged(this)
        if (intent?.action == ACTION_STOP) {
            stopListening()
            return START_NOT_STICKY
        }
        val notification = notification(VoicePrefs.status(this).ifBlank { "Starting Hey Pony" })
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
        )
        if (running.compareAndSet(false, true)) thread.start()
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        runCatching { unregisterReceiver(powerReceiver) }
        super.onDestroy()
    }

    private fun stopListening() {
        running.set(false)
        paused.set(true)
        started.set(false)
        lastNotified = ""
        VoicePrefs.setStatus(this, "")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun loop() {
        var model: Model? = null
        var recognizer: Recognizer? = null
        var recorder: android.media.AudioRecord? = null
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            while (running.get()) {
                if (!VoicePrefs.wakeWord(this)) break
                val holdMic = !paused.get() && (!VoicePrefs.chargingOnly(this) || plugged || LockState.isPlugged(this))
                if (!holdMic) {
                    recorder?.release()
                    recorder = null
                    val why = if (paused.get()) "Hey Pony is paused while you talk to Pony"
                    else "Hey Pony is paused until the phone is charging"
                    update(why)
                    Thread.sleep(1000)
                    continue
                }
                if (model == null) {
                    val dir = ensureModel()
                    if (dir == null) {
                        update("Couldn't download the on-device wake word model")
                        Thread.sleep(5000)
                        continue
                    }
                    model = Model(dir.absolutePath)
                    recognizer = Recognizer(model, 16_000f, "[\"hey pony\",\"[unk]\"]")
                }
                if (recorder == null) {
                    recorder = openMic()
                    if (recorder == null) {
                        update("Allow the microphone so Hey Pony can listen")
                        Thread.sleep(2000)
                        continue
                    }
                    update("Listening for Hey Pony")
                }
                val buffer = ByteArray(4096)
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val spotter = recognizer ?: continue
                val json = if (spotter.acceptWaveForm(buffer, read)) spotter.result else spotter.partialResult
                if (isHeyPony(json)) {
                    spotter.reset()
                    paused.set(true)
                    recorder.stop()
                    recorder.release()
                    recorder = null
                    update("Heard Hey Pony")
                    alertOwner()
                    VoiceController.listen(this, "wake_word")
                    Thread.sleep(1500)
                }
            }
        } catch (err: Exception) {
            update(err.message ?: "Wake word stopped")
        } finally {
            recorder?.release()
            recognizer?.close()
            model?.close()
            running.set(false)
        }
    }

    private fun ensureModel(): File? {
        val root = File(filesDir, "vosk-model")
        findModel(root)?.let { return it }
        update("Downloading the Hey Pony model. Audio stays on the phone.")
        val zip = File(cacheDir, "vosk-model.zip")
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).build()
        val request = Request.Builder().url(MODEL_URL).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                FileOutputStream(zip).use { out -> body.byteStream().copyTo(out) }
            }
            unzip(zip, root)
            zip.delete()
            return findModel(root)
        } catch (_: Exception) {
            return null
        }
    }

    private fun openMic(): android.media.AudioRecord? {
        val rate = 16_000
        val min = android.media.AudioRecord.getMinBufferSize(
            rate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) return null
        val recorder = android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
            rate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
            min * 2,
        )
        return try {
            recorder.startRecording()
            if (recorder.recordingState != android.media.AudioRecord.RECORDSTATE_RECORDING) {
                recorder.release()
                null
            } else {
                recorder
            }
        } catch (_: SecurityException) {
            recorder.release()
            null
        }
    }

    private fun update(text: String) {
        if (!VoicePrefs.wakeWord(this)) {
            if (VoicePrefs.status(this).isNotEmpty()) VoicePrefs.setStatus(this, "")
            return
        }
        if (text == lastNotified) return
        lastNotified = text
        VoicePrefs.setStatus(this, text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, app.pony.companion.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pony)
            .setContentTitle("Hey Pony")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun alertOwner() {
        if (app.pony.companion.display.CallGuard.inCall(this)) {
            update("Heard Hey Pony. A call is on the phone, so Pony is staying quiet.")
            return
        }
        val wake = PendingIntent.getActivity(
            this,
            2,
            Intent(this, VoiceWakeActivity::class.java)
                .putExtra(VoiceWakeActivity.EXTRA_SOURCE, "wake_word")
                .putExtra(VoiceWakeActivity.EXTRA_START, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alert = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pony)
            .setContentTitle("Pony")
            .setContentText("Heard Hey Pony")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(wake, true)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(ALERT_ID, alert)
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Hey Pony", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "Hey Pony heard you", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private val powerReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            plugged = LockState.isPlugged(this@WakeWordService)
        }
    }

    companion object {
        private const val ACTION_STOP = "app.pony.companion.WAKE_STOP"
        private const val CHANNEL_ID = "pony_wake"
        private const val ALERT_CHANNEL = "pony_wake_alert"
        private const val NOTIF_ID = 48
        private const val ALERT_ID = 49
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private val paused = AtomicBoolean(false)
        private val started = AtomicBoolean(false)

        fun start(context: Context) {
            paused.set(false)
            started.set(true)
            context.startForegroundService(Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            if (!started.get()) return
            context.startService(Intent(context, WakeWordService::class.java).setAction(ACTION_STOP))
        }

        fun pause(context: Context) {
            paused.set(true)
        }

        fun resume(context: Context) {
            if (VoicePrefs.wakeWord(context)) paused.set(false)
        }

        fun isHeyPony(json: String): Boolean {
            val flat = json.lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ")
            return flat.contains("hey pony")
        }
    }
}

private fun unzip(zip: File, dest: File) {
    dest.mkdirs()
    ZipInputStream(zip.inputStream()).use { input ->
        var entry = input.nextEntry
        while (entry != null) {
            val out = File(dest, entry.name)
            val canonical = out.canonicalPath
            if (!canonical.startsWith(dest.canonicalPath + File.separator) && canonical != dest.canonicalPath) {
                throw IllegalStateException("bad zip entry")
            }
            if (entry.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                FileOutputStream(out).use { input.copyTo(it) }
            }
            input.closeEntry()
            entry = input.nextEntry
        }
    }
}

private fun findModel(root: File): File? {
    if (!root.exists()) return null
    if (File(root, "conf").isDirectory || File(root, "am").isDirectory) return root
    root.listFiles()?.forEach { child ->
        if (child.isDirectory) findModel(child)?.let { return it }
    }
    return null
}
