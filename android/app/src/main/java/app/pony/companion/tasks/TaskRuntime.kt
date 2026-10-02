package app.pony.companion.tasks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.pony.companion.overlay.AppVisibility
import app.pony.companion.voice.RequestInbox
import app.pony.companion.voice.VoiceBus
import app.pony.companion.voice.VoicePrefs
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/** The phone-wide task tracker, stored in app-private files. */
object TaskRuntime {
    @Volatile
    private var instance: TaskTracker? = null
    @Volatile
    private var app: Context? = null

    val tracker: TaskTracker
        get() = instance ?: synchronized(this) {
            instance ?: TaskTracker(app?.let { TaskStore(File(it.filesDir, "tasks")) }).also { instance = it }
        }

    val live: StateFlow<TaskRecord?> get() = tracker.live
    val history: StateFlow<List<TaskRecord>> get() = tracker.history

    @Volatile
    private var recovered = false

    fun init(context: Context) {
        app = context.applicationContext
        if (recovered) return
        recovered = true
        tracker.recover(RequestInbox.DEFAULT_TTL_MS) { task ->
            VoiceBus.inbox.submit(task.text, task.source, task.id, task.createdAt)
            true
        }
    }

    fun current(): TaskRecord? = tracker.current()

    /**
     * A step with an optional screenshot. A screen capture is shrunk into a
     * history thumbnail only when [ShotPolicy] allows it: never while Pony is in
     * front (its own step list would feed back), and never for another app's
     * screen (that stays private, only in the live reply to the brain).
     */
    fun step(
        kind: StepKind,
        label: String,
        ok: Boolean = true,
        detail: String? = null,
        jpeg: ByteArray? = null,
        id: String? = null,
        ofOtherApp: Boolean = false,
    ): TaskStep? {
        val keep = ShotPolicy.keepThumbnail(
            keepSetting = app?.let { VoicePrefs.keepScreenshots(it) } ?: false,
            ponyForeground = AppVisibility.visible.value,
            ofOtherApp = ofOtherApp,
        )
        val thumb = if (keep && jpeg != null) thumbnail(jpeg) else null
        return tracker.step(kind, label, ok, detail, thumb, id)
    }

    fun shotFile(taskId: String, name: String): File? = tracker.shotFile(taskId, name)

    /** A detached-session step with the same screenshot policy as [step]. */
    fun stepOn(
        id: String,
        kind: StepKind,
        label: String,
        ok: Boolean = true,
        detail: String? = null,
        jpeg: ByteArray? = null,
        ofOtherApp: Boolean = false,
    ): TaskStep? {
        val keep = ShotPolicy.keepThumbnail(
            keepSetting = app?.let { VoicePrefs.keepScreenshots(it) } ?: false,
            ponyForeground = AppVisibility.visible.value,
            ofOtherApp = ofOtherApp,
        )
        val thumb = if (keep && jpeg != null) thumbnail(jpeg) else null
        return tracker.stepOn(id, kind, label, ok, detail, thumb)
    }

    fun thumbnail(jpeg: ByteArray, width: Int = 540): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@runCatching null
        val scaled = if (decoded.width > width) {
            Bitmap.createScaledBitmap(decoded, width, (decoded.height * width.toFloat() / decoded.width).toInt().coerceAtLeast(1), true)
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        out.toByteArray()
    }.getOrNull()
}
