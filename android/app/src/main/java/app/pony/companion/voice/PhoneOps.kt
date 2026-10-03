package app.pony.companion.voice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import app.pony.companion.a11y.PonyAccessibilityService
import app.pony.companion.a11y.TextEntry
import app.pony.companion.a11y.TypeMode
import app.pony.companion.brain.ScreenView
import app.pony.companion.brain.ToolCall
import app.pony.companion.display.ActionResult
import app.pony.companion.display.BackgroundHost
import app.pony.companion.display.ScreenRouter
import app.pony.companion.display.Watch
import app.pony.companion.memory.Memory
import app.pony.companion.memory.MemoryGuard
import app.pony.companion.overlay.AppVisibility
import app.pony.companion.recap.RecapShotPolicy
import app.pony.companion.recap.RecapShots
import app.pony.companion.recap.UndoLog
import app.pony.companion.recap.UndoPlanner
import app.pony.companion.recap.UndoableAction
import app.pony.companion.schedule.ScheduleAlarms
import app.pony.companion.schedule.ScheduleParser
import app.pony.companion.session.AuditEntry
import app.pony.companion.session.AuditLog
import app.pony.companion.session.SessionRepository
import app.pony.companion.tasks.CarryBuffer
import app.pony.companion.tasks.StepKind
import app.pony.companion.tasks.StopState
import app.pony.companion.tasks.TaskRuntime
import java.io.ByteArrayOutputStream

/** Runs the same actions the relay uses, for a brain on this phone. Confirmation happens before this. */
object PhoneOps {
    /** Tools that don't touch the phone screen, so they run even when accessibility is off. */
    private val LOCAL_OPS = setOf("speak", "remember", "forget", "schedule_task", "copy_text", "recall_text")

    fun screen(context: Context): ScreenView {
        val acted = ScreenRouter.tree(context, null)
        val tree = (acted.fields["tree"] as? String)?.take(8_000) ?: "(accessibility is off)"
        TaskRuntime.step(StepKind.Read, "Read the screen")
        return ScreenView(tree, screenshot(context))
    }

    fun execute(context: Context, call: ToolCall, watch: Watch = Watch.None): String {
        if (StopState.gate.isStopped()) return "stopped"
        if (PonyAccessibilityService.instance == null && call.name !in LOCAL_OPS) return "accessibility_off"
        return when (call.name) {
            "tap" -> {
                val x = call.args["x"]?.toDoubleOrNull() ?: return "missing x"
                val y = call.args["y"]?.toDoubleOrNull() ?: return "missing y"
                val label = ScreenRouter.labelAt(context, x, y, null)
                val acted = ScreenRouter.tapReliably(context, x, y, label, null, watch)
                record(context, "tap", StepKind.Tap, if (label.isNotBlank()) "Tapped “${label.take(60)}”" else "Tapped the screen", acted)
                if (acted.ok) noteCommitIfSending(label)
                narrate(acted.ok, "tapped ${acted.fields["x"]},${acted.fields["y"]}", acted.error ?: "tap_failed", acted)
            }
            "swipe" -> {
                val x1 = call.args["x1"]?.toDoubleOrNull() ?: return "missing x1"
                val y1 = call.args["y1"]?.toDoubleOrNull() ?: return "missing y1"
                val x2 = call.args["x2"]?.toDoubleOrNull() ?: return "missing x2"
                val y2 = call.args["y2"]?.toDoubleOrNull() ?: return "missing y2"
                val dur = call.args["durationMs"]?.toLongOrNull() ?: 250L
                val acted = ScreenRouter.swipe(context, x1, y1, x2, y2, dur, null, watch)
                record(context, "swipe", StepKind.Swipe, swipeLabel(x1, y1, x2, y2), acted)
                narrate(acted.ok, "swiped", acted.error ?: "swipe_failed", acted)
            }
            "long_press" -> {
                val x = call.args["x"]?.toDoubleOrNull() ?: return "missing x"
                val y = call.args["y"]?.toDoubleOrNull() ?: return "missing y"
                val dur = call.args["durationMs"]?.toLongOrNull() ?: 600L
                val label = ScreenRouter.labelAt(context, x, y, null)
                val acted = ScreenRouter.longPress(context, x, y, dur, null, watch)
                record(context, "long_press", StepKind.Tap, if (label.isNotBlank()) "Held “${label.take(60)}”" else "Pressed and held", acted)
                narrate(acted.ok, "held ${acted.fields["x"]},${acted.fields["y"]}", acted.error ?: "gesture_failed", acted)
            }
            "drag" -> {
                val x1 = call.args["x1"]?.toDoubleOrNull() ?: return "missing x1"
                val y1 = call.args["y1"]?.toDoubleOrNull() ?: return "missing y1"
                val x2 = call.args["x2"]?.toDoubleOrNull() ?: return "missing x2"
                val y2 = call.args["y2"]?.toDoubleOrNull() ?: return "missing y2"
                val dur = call.args["durationMs"]?.toLongOrNull() ?: 600L
                val acted = ScreenRouter.drag(context, x1, y1, x2, y2, dur, null, watch)
                record(context, "drag", StepKind.Swipe, "Dragged to ${fmt(x2)},${fmt(y2)}", acted)
                narrate(acted.ok, "dragged", acted.error ?: "gesture_failed", acted)
            }
            "pinch" -> {
                val x = call.args["x"]?.toDoubleOrNull() ?: return "missing x"
                val y = call.args["y"]?.toDoubleOrNull() ?: return "missing y"
                val from = call.args["fromDistance"]?.toDoubleOrNull() ?: return "missing fromDistance"
                val to = call.args["toDistance"]?.toDoubleOrNull() ?: return "missing toDistance"
                val dur = call.args["durationMs"]?.toLongOrNull() ?: 300L
                val acted = ScreenRouter.pinch(context, x, y, from, to, dur, null, watch)
                record(context, "pinch", StepKind.Swipe, if (to >= from) "Pinched to zoom in" else "Pinched to zoom out", acted)
                narrate(acted.ok, if (to >= from) "zoomed in" else "zoomed out", acted.error ?: "gesture_failed", acted)
            }
            "type" -> {
                val text = call.args["text"] ?: return "missing text"
                val mode = TextEntry.parseMode(
                    call.args["mode"],
                    call.args["append"].equals("true", ignoreCase = true),
                )
                val acted = ScreenRouter.type(context, text, mode, null, watch)
                record(context, "type", StepKind.Type, "Typed ${text.length} characters", acted)
                if (acted.ok && mode != TypeMode.APPEND && text.isNotEmpty()) {
                    UndoLog.record(TaskRuntime.current()?.id, UndoableAction.ClearDraft(text.length, foregroundApp()))
                }
                narrate(
                    acted.ok,
                    "typed ${acted.fields["length"]} chars via ${acted.fields["method"]}",
                    acted.error ?: "type_failed",
                    acted,
                )
            }
            "key" -> {
                val key = call.args["key"] ?: return "missing key"
                val acted = ScreenRouter.press(context, key, null, watch)
                record(context, "key", StepKind.Key, "Pressed ${key.replaceFirstChar { it.uppercase() }}", acted)
                narrate(acted.ok, "pressed $key", acted.error ?: "unknown_key", acted)
            }
            "open_app" -> {
                val pkg = call.args["package"] ?: call.args["packageName"] ?: return "missing package"
                val acted = ScreenRouter.open(context, pkg, null, consent = { prompt -> VoiceController.confirmBlocking(context, prompt) }, watch = watch)
                record(context, "open_app", StepKind.Open, "Opened ${BackgroundHost.appLabel(context, pkg)}", acted)
                narrate(acted.ok, "opened $pkg", acted.error ?: "app_not_found", acted)
            }
            "open_settings" -> {
                val name = call.args["name"] ?: call.args["screen"] ?: return "missing name"
                val pkg = call.args["package"] ?: call.args["packageName"]
                val acted = ScreenRouter.openSettings(context, name, pkg, null, consent = { prompt -> VoiceController.confirmBlocking(context, prompt) }, watch = watch)
                record(context, "open_settings", StepKind.Open, "Opened ${settingsLabel(name)}", acted)
                narrate(acted.ok, "opened ${settingsLabel(name)}", acted.error ?: "unknown_settings", acted)
            }
            "wait_idle" -> {
                val timeout = call.args["timeoutMs"]?.toLongOrNull() ?: 4_000L
                val acted = ScreenRouter.waitIdle(context, timeout, null)
                val settled = acted.fields["settled"] == true
                val waited = acted.fields["waitedMs"]
                record(
                    context,
                    "wait_idle",
                    StepKind.Read,
                    if (settled) "Waited for the screen to settle" else "Screen still busy after ${waited}ms",
                    acted,
                )
                narrate(
                    acted.ok,
                    if (settled) "screen settled after ${waited}ms" else "still busy after ${waited}ms",
                    acted.error ?: "wait_failed",
                    acted,
                )
            }
            "speak" -> {
                val text = call.args["text"].orEmpty()
                VoiceController.speakNow(context, text)
                "spoke"
            }
            "remember" -> {
                val key = call.args["key"]?.trim().orEmpty()
                val value = call.args["value"]?.trim().orEmpty()
                if (key.isEmpty() || value.isEmpty()) return "missing key or value"
                val verdict = MemoryGuard.check(key, value)
                if (!verdict.allowed) {
                    TaskRuntime.step(StepKind.Status, "Won't remember “$key” — it looks private", false)
                    return "won't remember that — ${verdict.reason ?: "it looks private"}"
                }
                val entry = Memory.store(context).put(key, value)
                TaskRuntime.step(StepKind.Status, "Remembered “${entry.key.replace('_', ' ')}”")
                "remembered ${entry.key}"
            }
            "forget" -> {
                val key = call.args["key"]?.trim().orEmpty()
                if (key.isEmpty()) return "missing key"
                val removed = Memory.store(context).delete(key)
                TaskRuntime.step(StepKind.Status, if (removed) "Forgot “$key”" else "Nothing to forget for “$key”")
                if (removed) "forgot $key" else "nothing to forget for $key"
            }
            "schedule_task" -> {
                val taskText = call.args["task"]?.trim().orEmpty()
                val whenText = call.args["time"]?.trim().orEmpty()
                if (whenText.isEmpty()) return "missing time"
                val now = System.currentTimeMillis()
                val parsed = ScheduleParser.parse(whenText, now)
                    ?: ScheduleParser.parse("$whenText $taskText", now)
                if (parsed == null) {
                    TaskRuntime.step(StepKind.Status, "Couldn't tell when to run that", false)
                    return "couldn't tell when — say a time like 'every weekday at 8am' or 'tomorrow at 6pm'"
                }
                val text = taskText.ifBlank { parsed.task }
                if (text.isBlank()) return "what should I do at that time?"
                if (parsed.schedule.nextFireAt(now) == null) {
                    TaskRuntime.step(StepKind.Status, "That time's already past", false)
                    return "that time's already past — give me a later time"
                }
                val saved = ScheduleAlarms.add(context, text, parsed.schedule)
                val summary = saved.schedule.describe(now)
                TaskRuntime.step(StepKind.Status, "Scheduled “$text” — $summary")
                "scheduled $text — $summary"
            }
            "copy_text" -> {
                val label = call.args["label"]?.trim().orEmpty()
                val text = call.args["text"].orEmpty()
                if (label.isEmpty()) return "missing label"
                val result = CarryBuffer.shared.stash(label, text)
                if (!result.ok) {
                    TaskRuntime.step(StepKind.Status, "Didn't copy “$label” — ${result.reason}", false)
                    return "didn't copy — ${result.reason}"
                }
                val chars = result.note?.text?.length ?: 0
                TaskRuntime.step(StepKind.Status, "Copied “$label” to carry over")
                "copied $chars characters as $label"
            }
            "recall_text" -> {
                val label = call.args["label"]?.trim().orEmpty()
                if (label.isEmpty()) return "missing label"
                val text = CarryBuffer.shared.recall(label)
                if (text == null) {
                    TaskRuntime.step(StepKind.Status, "Nothing copied under “$label”", false)
                    return "nothing copied under $label"
                }
                TaskRuntime.step(StepKind.Status, "Recalled “$label”")
                text
            }
            else -> "unknown action ${call.name}"
        }
    }

    private fun record(context: Context, action: String, kind: StepKind, label: String, acted: ActionResult) {
        log(context, action, acted.error ?: acted.target.name, acted.ok)
        TaskRuntime.step(kind, if (acted.ok) label else "$label — ${acted.error}", acted.ok)
    }

    /**
     * Once Pony taps a control that sends, pays, posts, or deletes, the task is
     * committed and no undo is offered for it — the owner's rule is to never undo
     * anything already sent or paid. Matching is on the tapped control's own label.
     */
    private fun noteCommitIfSending(label: String) {
        if (label.isBlank()) return
        val verdict = SafetyPolicy.forTap(TapTarget(label))
        if (verdict is Verdict.Confirm && UndoPlanner.committedBy(verdict.reason)) {
            UndoLog.commit(TaskRuntime.current()?.id)
        }
    }

    /** The app Pony is acting in right now, so an undo can reopen the right screen. */
    private fun foregroundApp(): String? =
        PonyAccessibilityService.instance?.foregroundPackage()?.takeIf { it.isNotBlank() }

    private fun settingsLabel(name: String): String = "${name.trim().replace('_', ' ')} settings"

    private fun fmt(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun swipeLabel(x1: Double, y1: Double, x2: Double, y2: Double): String {
        val dx = x2 - x1
        val dy = y2 - y1
        return when {
            kotlin.math.abs(dy) >= kotlin.math.abs(dx) && dy < 0 -> "Swiped up"
            kotlin.math.abs(dy) >= kotlin.math.abs(dx) -> "Swiped down"
            dx < 0 -> "Swiped left"
            else -> "Swiped right"
        }
    }

    private fun narrate(ok: Boolean, success: String, failure: String, acted: ActionResult): String {
        val where = " on ${acted.target.name}"
        val warn = acted.target.warnText?.let { " $it" }.orEmpty()
        return if (ok) success + where + warn else failure + where + warn
    }

    private fun screenshot(context: Context): String? {
        val acted = ScreenRouter.shot(context, null)
        val jpeg = acted.fields["jpeg"] as? ByteArray ?: return null
        log(context, "screenshot", "${jpeg.size} bytes", true)
        TaskRuntime.step(StepKind.Look, "Looked at the screen", jpeg = jpeg, ofOtherApp = true)
        if (RecapShotPolicy.keep(AppVisibility.visible.value)) {
            RecapShots.capture(TaskRuntime.current()?.id, TaskRuntime.thumbnail(jpeg))
        }
        return shrink(jpeg)
    }

    private fun shrink(bytes: ByteArray): String? {
        val bitmap = if (bytes.size <= 120_000) {
            null
        } else {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        val encoded = if (bitmap == null) {
            if (bytes.size > 180_000) return null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } else {
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * 0.45f).toInt().coerceAtLeast(1),
                (bitmap.height * 0.45f).toInt().coerceAtLeast(1),
                true,
            )
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 45, out)
            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()
            val data = out.toByteArray()
            if (data.size > 180_000) null else Base64.encodeToString(data, Base64.NO_WRAP)
        }
        return encoded
    }

    private fun log(context: Context, action: String, detail: String, ok: Boolean) {
        val entries = AuditLog(context).append(AuditEntry(action = action, detail = detail, ok = ok))
        SessionRepository.update { it.copy(log = entries) }
    }
}
