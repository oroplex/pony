package app.pony.companion.voice

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import app.pony.companion.MainActivity
import app.pony.companion.a11y.PonyAccessibilityService
import app.pony.companion.brain.AgentLoop
import app.pony.companion.brain.BrainError
import app.pony.companion.brain.BrainLibrary
import app.pony.companion.brain.BrainRouter
import app.pony.companion.brain.ChatClient
import app.pony.companion.brain.Guard
import app.pony.companion.brain.GrokBot
import app.pony.companion.brain.KeystoreSecretBox
import app.pony.companion.brain.ProviderRecord
import app.pony.companion.brain.StepModel
import app.pony.companion.brain.ToolCall
import app.pony.companion.display.BackgroundHost
import app.pony.companion.display.BackgroundNotifier
import app.pony.companion.display.OwnUi
import app.pony.companion.display.ScreenRouter
import app.pony.companion.display.Watch
import app.pony.companion.memory.Memory
import app.pony.companion.overlay.OverlayHost
import app.pony.companion.recap.RecapShots
import app.pony.companion.recap.UndoLog
import app.pony.companion.recap.UndoableAction
import app.pony.companion.routines.RoutineBook
import app.pony.companion.routines.Routines
import app.pony.companion.session.ActionExpiry
import app.pony.companion.session.AuditEntry
import app.pony.companion.session.AuditLog
import app.pony.companion.session.Connection
import app.pony.companion.session.SessionRepository
import app.pony.companion.session.SessionUi
import app.pony.companion.tasks.Basics
import app.pony.companion.tasks.Brains
import app.pony.companion.tasks.Outcomes
import app.pony.companion.tasks.RemoteStep
import app.pony.companion.tasks.Skill
import app.pony.companion.tasks.SkillRunner
import app.pony.companion.tasks.StepKind
import app.pony.companion.tasks.StopGate
import app.pony.companion.tasks.StopState
import app.pony.companion.tasks.TaskRuntime
import app.pony.companion.tasks.TaskState
import app.pony.companion.text.Prose
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Prompt(val id: Int, val text: String, val confirm: Boolean)

data class PromptAnswer(val accepted: Boolean, val expired: Boolean = false, val stopped: Boolean = false)

data class AskAnswer(val text: String, val expired: Boolean = false, val stopped: Boolean = false)

enum class NoticeAction { ALLOW_MIC, OPEN_PONY, RETRY }

data class Notice(val id: Int, val title: String, val body: String, val action: NoticeAction? = null)

data class VoiceUi(
    val listening: Boolean = false,
    val transcript: String = "",
    val level: Float = 0f,
    val prompt: Prompt? = null,
    val notice: Notice? = null,
    val source: String = "",
)

/**
 * Pony's conductor. Every ask gets a visible status at once and a brain that
 * can actually run it: a listening Grok Bot, a key brain on this phone, or
 * Pony Basics. Stop ends the task that was running, never the session.
 */
object VoiceController {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "pony-task") }
    private val listener = Executors.newSingleThreadExecutor { Thread(it, "pony-listen") }
    private val timers = Executors.newSingleThreadScheduledExecutor { Thread(it, "pony-timers") }
    private val generation = AtomicInteger()
    private val running = AtomicBoolean(false)
    private val listening = AtomicBoolean(false)
    private val choiceMade = AtomicBoolean(false)
    private val choiceYes = AtomicBoolean(false)
    private val promptCancel = AtomicBoolean(false)
    private val serial = AtomicInteger()
    @Volatile private var appContext: Context? = null
    @Volatile private var lastSpoken: String? = null
    @Volatile private var pickedUpAt = 0L
    @Volatile private var lastRemoteAt = 0L
    /** When the assistant last *acted* (not just looked), used to idle out implicit cards. */
    @Volatile private var lastActingAt = 0L
    /** The open detached session for a paired assistant's own actions, and when it last stepped. */
    @Volatile private var sessionId: String? = null
    @Volatile private var sessionStepAt = 0L
    private var lastUnlockAt = 0L
    private var initialized = false
    private val _voice = MutableStateFlow(VoiceUi())
    val voice: StateFlow<VoiceUi> = _voice.asStateFlow()

    const val REMOTE_IDLE_MS = 90_000L
    /** A request the assistant took gets longer: it may think before it touches the phone. */
    const val DELIVERED_IDLE_MS = 150_000L
    /** Fold a paired session's remote steps into one History entry until this long of quiet. */
    const val SESSION_GAP_MS = 4 * 60_000L

    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true
        VoiceBus.inbox.onDelivered { onDelivered(it) }
    }

    fun isRunning(): Boolean = running.get()

    fun isListening(): Boolean = listening.get()

    /** Starts a task from typed or spoken words. Returns the task id, or null if there was nothing to do. */
    fun ask(context: Context, text: String, source: String = "typed", basicsOnly: Boolean = false, returnToPony: Boolean = false): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val app = context.applicationContext
        init(app)
        if (VoiceWords.isStop(trimmed)) {
            stop()
            return null
        }
        if (source != Routines.SOURCE) {
            Routines.parseSave(trimmed)?.let { name -> return saveRoutine(app, trimmed, name, source) }
            RoutineBook.match(app, trimmed)?.let { routine ->
                RoutineBook.markRun(app, routine.id)
                log(app, "routine_run", routine.name, true)
                return ask(app, routine.text, Routines.SOURCE, basicsOnly, returnToPony)
            }
        }
        TaskRuntime.current()?.let { VoiceBus.inbox.cancel(it.id) }
        StopState.gate.release(StopGate.Release.NEW_TASK)
        val gen = generation.incrementAndGet()
        val session = SessionRepository.snapshot()
        val grok = grokName(app, session)
        val connected = session.connection == Connection.Connected && !session.peerAway
        val listeningNow = connected && VoiceBus.listening()
        val key = activeBrain(app)
        val skills = Basics.parse(trimmed)
        val mode = if (VoicePrefs.brainMode(app) == VoicePrefs.BUILTIN) BrainRouter.Mode.PHONE else BrainRouter.Mode.GROK
        val route = if (basicsOnly && skills != null) {
            BrainRouter.Route.Basics(BrainRouter.Why.CHOSEN)
        } else {
            BrainRouter.route(BrainRouter.Input(mode, connected, listeningNow, key?.first?.name, skills != null))
        }
        log(app, "ask", "${trimmed.length} chars, ${routeName(route)}", true)
        val tracker = TaskRuntime.tracker
        val id = when (route) {
            BrainRouter.Route.Deliver -> {
                val task = tracker.begin(trimmed, source, Brains.GROK, grok, TaskState.Sent, headline = "Sent to $grok")
                VoiceBus.inbox.submit(trimmed, source, task.id)
                task.id
            }
            is BrainRouter.Route.Queue -> {
                val task = tracker.begin(trimmed, source, Brains.GROK, grok, TaskState.Queued, headline = "Waiting for $grok to check in…")
                VoiceBus.inbox.submit(trimmed, source, task.id)
                scheduleHandoff(app, task.id, trimmed, source, gen, route.fallback, route.waitMs, grok, skills, returnToPony)
                task.id
            }
            is BrainRouter.Route.Hold -> {
                val headline = if (route.grokConnected) "Waiting for $grok to check in…" else "$grok isn't connected"
                val task = tracker.begin(trimmed, source, Brains.GROK, grok, TaskState.Waiting, headline = headline)
                VoiceBus.inbox.submit(trimmed, source, task.id)
                task.id
            }
            is BrainRouter.Route.Phone -> {
                val brain = key!!
                val headline = when (route.why) {
                    BrainRouter.Why.GROK_OFFLINE -> "$grok isn't connected, so ${brain.first.name} on this phone is doing it"
                    else -> "${brain.first.name} is on it"
                }
                val task = tracker.begin(trimmed, source, Brains.PHONE, brain.first.name, TaskState.Running, headline = headline)
                runPhone(app, task.id, trimmed, source, gen, brain)
                task.id
            }
            is BrainRouter.Route.Basics -> {
                val headline = when (route.why) {
                    BrainRouter.Why.GROK_OFFLINE -> "$grok isn't connected, so Pony is doing this one on the phone"
                    BrainRouter.Why.NO_KEY -> "Pony is doing this one on the phone"
                    else -> "Pony is on it"
                }
                val task = tracker.begin(trimmed, source, Brains.BASICS, Brains.BASICS_LABEL, TaskState.Running, headline = headline)
                runBasics(app, task.id, skills!!, source, gen, returnToPony)
                task.id
            }
            BrainRouter.Route.NeedsKey -> {
                val task = tracker.begin(trimmed, source, Brains.PHONE, "On this phone", TaskState.Running)
                tracker.finish(TaskState.Failed, "Add an API key to run this on the phone, or switch to $grok.", task.id)
                task.id
            }
        }
        showOverlay(app)
        return id
    }

    /**
     * Saves the owner's last finished task as a named routine and puts up a small
     * "Saved …" card. Reads the last task before starting this one, so the save
     * never records itself. The brain is never involved — this is the owner
     * naming something they already did.
     */
    private fun saveRoutine(app: Context, spoken: String, name: String, source: String): String {
        val saved = RoutineBook.saveLast(app, name)
        val task = TaskRuntime.tracker.begin(spoken, Routines.SAVE_SOURCE, Brains.BASICS, Brains.BASICS_LABEL, TaskState.Running, headline = "Saving a routine")
        val said = if (saved != null) {
            "Saved “${saved.name}.” Say “${saved.name}” any time and I'll run it again."
        } else {
            "I don't have a finished task to save yet. Ask me to do something first, then say save it as a routine."
        }
        TaskRuntime.tracker.finish(if (saved != null) TaskState.Done else TaskState.Failed, said, task.id)
        log(app, "routine_save", saved?.name ?: name, saved != null)
        if (source != "typed" || VoicePrefs.spokenReplies(app)) speakOut(app, said)
        showOverlay(app)
        return task.id
    }

    /** Listens once, then asks with what it heard. */
    fun listen(context: Context, source: String) {
        val app = context.applicationContext
        init(app)
        if (!VoicePrefs.voiceEnabled(app)) {
            notice(app, "Voice is off", "Turn on Voice in Pony's settings to talk to Pony.", NoticeAction.OPEN_PONY)
            return
        }
        if (!SpeechInput.micGranted(app)) {
            notice(app, "Pony can't hear you yet", "Allow the microphone so Pony can listen.", NoticeAction.ALLOW_MIC)
            return
        }
        if (!listening.compareAndSet(false, true)) return
        _voice.value = VoiceUi(listening = true, source = source)
        showOverlay(app)
        WakeWordService.pause(app)
        listener.execute {
            try {
                val heard = SpeechInput.listen(
                    app,
                    onPartial = { words -> _voice.update { it.copy(transcript = words) } },
                    onLevel = { level -> _voice.update { it.copy(level = level) } },
                ) { !listening.get() }
                listening.set(false)
                heard.fold(
                    onSuccess = { words ->
                        _voice.update { it.copy(listening = false, level = 0f, transcript = words) }
                        ask(app, words, source)
                        main.postDelayed({ _voice.update { if (!it.listening) it.copy(transcript = "") else it } }, 1_200)
                    },
                    onFailure = { err ->
                        val problem = err as? SpeechProblem
                        _voice.update { it.copy(listening = false, level = 0f) }
                        when (problem?.kind) {
                            SpeechProblem.Kind.STOPPED -> _voice.value = VoiceUi()
                            SpeechProblem.Kind.NO_MIC_PERMISSION ->
                                notice(app, "Pony can't hear you yet", problem.message.orEmpty(), NoticeAction.ALLOW_MIC)
                            SpeechProblem.Kind.NOTHING_HEARD ->
                                notice(app, "Didn't catch that", problem.message.orEmpty(), NoticeAction.RETRY)
                            else -> notice(app, "Pony couldn't listen", err.message ?: "Speech stopped.", NoticeAction.RETRY)
                        }
                    },
                )
            } finally {
                listening.set(false)
                WakeWordService.resume(app)
            }
        }
    }

    fun cancelListening() {
        listening.set(false)
        SpeechInput.cancel()
        _voice.update { it.copy(listening = false, level = 0f) }
    }

    fun retryListening(source: String = "voice") {
        val app = appContext ?: return
        dismissNotice()
        listen(app, source)
    }

    /** Stops the current task. The session and the next task are unaffected. */
    fun stop() {
        val task = TaskRuntime.current()
        StopState.gate.stop(task?.id)
        generation.incrementAndGet()
        task?.let { VoiceBus.inbox.cancel(it.id) }
        choiceYes.set(false)
        choiceMade.set(true)
        listening.set(false)
        SpeechInput.cancel()
        SpeechOutput.stop()
        _voice.value = VoiceUi()
        // The stop sticks until the owner picks it back up. Mark it resumable so
        // Keep going re-asks the very same request — no new ask needed.
        if (task != null) {
            TaskRuntime.tracker.finish(TaskState.Stopped, "Stopped. Tap Keep going whenever you want Pony to pick it back up.", task.id, resumable = true)
        }
        appContext?.let {
            BackgroundHost.release(it)
            log(it, "stop", "task stopped", true)
        }
    }

    fun answer(yes: Boolean) {
        choiceYes.set(yes)
        choiceMade.set(true)
        SpeechInput.cancel()
    }

    fun dismissNotice() {
        _voice.update { it.copy(notice = null) }
    }

    fun openPonyFor(action: NoticeAction?) {
        val app = appContext ?: return
        dismissNotice()
        val intent = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_REQUEST_MIC, action == NoticeAction.ALLOW_MIC)
        runCatching {
            PonyAccessibilityService.instance?.launch(intent, null) ?: app.startActivity(intent)
        }
    }

    fun speakNow(context: Context, text: String): Boolean {
        if (text.isBlank()) return true
        val say = Prose.plain(text)
        lastSpoken = say
        lastRemoteAt = System.currentTimeMillis()
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        TaskRuntime.step(StepKind.Speak, "Said “${say.take(120)}”")
        if (!VoicePrefs.voiceEnabled(context)) return true
        return SpeechOutput.speak(context, say, VoicePrefs.speechRate(context), VoicePrefs.voiceName(context))
    }

    /** Drops the on-phone prompt when the connector gives up, so it cannot later log a stale "declined". */
    fun cancelPrompt() {
        promptCancel.set(true)
        choiceMade.set(true)
        choiceYes.set(false)
        _voice.update { it.copy(prompt = null, transcript = "", level = 0f) }
    }

    fun askBlocking(context: Context, question: String): String = askOutcome(context, question).text

    fun askOutcome(
        context: Context,
        question: String,
        cancelled: () -> Boolean = { false },
        limitMs: Long = ActionExpiry.OWNER_PROMPT_MS,
    ): AskAnswer {
        if (Looper.myLooper() == Looper.getMainLooper()) return AskAnswer("")
        val app = context.applicationContext
        val id = serial.incrementAndGet()
        choiceMade.set(false)
        promptCancel.set(false)
        val deadline = System.currentTimeMillis() + limitMs.coerceAtLeast(1_000L)
        _voice.update { it.copy(prompt = Prompt(id, question, confirm = false)) }
        showOverlay(app)
        TaskRuntime.step(StepKind.Ask, "Asked you: ${question.take(120)}")
        if (VoicePrefs.voiceEnabled(app)) SpeechOutput.speak(app, question, VoicePrefs.speechRate(app), VoicePrefs.voiceName(app))
        val heard = if (SpeechInput.micGranted(app)) {
            SpeechInput.listen(
                app,
                onPartial = { words -> _voice.update { it.copy(transcript = words) } },
                onLevel = { level -> _voice.update { it.copy(level = level) } },
            ) {
                choiceMade.get() || StopState.gate.isStopped() || promptCancel.get() || cancelled() ||
                    System.currentTimeMillis() >= deadline
            }.getOrNull().orEmpty()
        } else {
            ""
        }
        while (heard.isBlank() && !choiceMade.get() && !StopState.gate.isStopped() &&
            !promptCancel.get() && !cancelled() && System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(80)
        }
        _voice.update { if (it.prompt?.id == id) it.copy(prompt = null, transcript = "", level = 0f) else it }
        lastRemoteAt = System.currentTimeMillis()
        val expired = promptCancel.get() || cancelled() || (heard.isBlank() && System.currentTimeMillis() >= deadline && !choiceMade.get())
        if (VoiceWords.isStop(heard)) {
            stop()
            return AskAnswer("", stopped = true)
        }
        if (expired) {
            log(app, "ask_user", "expired", false)
            return AskAnswer("", expired = true)
        }
        log(app, "ask_user", "${question.length} chars", true)
        return AskAnswer(heard, stopped = StopState.gate.isStopped())
    }

    fun confirmBlocking(
        context: Context,
        prompt: String,
        cancelled: () -> Boolean = { false },
        limitMs: Long = ActionExpiry.OWNER_PROMPT_MS,
    ): Boolean {
        val outcome = confirmOutcome(context, prompt, cancelled, limitMs)
        return outcome.accepted && !outcome.expired && !outcome.stopped
    }

    fun confirmOutcome(
        context: Context,
        prompt: String,
        cancelled: () -> Boolean = { false },
        limitMs: Long = ActionExpiry.OWNER_PROMPT_MS,
    ): PromptAnswer {
        if (Looper.myLooper() == Looper.getMainLooper()) return PromptAnswer(false)
        val app = context.applicationContext
        val id = serial.incrementAndGet()
        choiceMade.set(false)
        choiceYes.set(false)
        promptCancel.set(false)
        val deadline = System.currentTimeMillis() + limitMs.coerceAtLeast(1_000L)
        _voice.update { it.copy(prompt = Prompt(id, prompt, confirm = true)) }
        showOverlay(app)
        TaskRuntime.tracker.headline("Waiting for your answer: $prompt")
        if (VoicePrefs.voiceEnabled(app)) SpeechOutput.speak(app, prompt, VoicePrefs.speechRate(app), VoicePrefs.voiceName(app))
        var heard = ""
        if (!choiceMade.get() && VoicePrefs.voiceEnabled(app) && SpeechInput.micGranted(app)) {
            heard = SpeechInput.listen(
                app,
                onPartial = { words -> _voice.update { it.copy(transcript = words) } },
                onLevel = { level -> _voice.update { it.copy(level = level) } },
            ) {
                choiceMade.get() || StopState.gate.isStopped() || promptCancel.get() || cancelled() ||
                    System.currentTimeMillis() >= deadline
            }.getOrNull().orEmpty()
        }
        while (!choiceMade.get() && !VoiceWords.isYes(heard) && !VoiceWords.isNo(heard) &&
            !StopState.gate.isStopped() && !promptCancel.get() && !cancelled() &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(80)
        }
        val expired = promptCancel.get() || cancelled() ||
            (!choiceMade.get() && !VoiceWords.isYes(heard) && !VoiceWords.isNo(heard) &&
                !StopState.gate.isStopped() && System.currentTimeMillis() >= deadline)
        val stopped = StopState.gate.isStopped() || VoiceWords.isStop(heard)
        val accepted = when {
            expired || stopped -> false
            choiceMade.get() -> choiceYes.get()
            else -> VoiceWords.isYes(heard)
        }
        _voice.update { if (it.prompt?.id == id) it.copy(prompt = null, transcript = "", level = 0f) else it }
        lastRemoteAt = System.currentTimeMillis()
        val label = when {
            expired -> "expired"
            accepted -> "yes"
            else -> "no"
        }
        log(app, "confirm", "${prompt.take(80)} → $label", accepted)
        if (!expired) {
            TaskRuntime.step(StepKind.Confirm, if (accepted) "You said yes: $prompt" else "You said no: $prompt", ok = accepted)
        }
        if (VoiceWords.isStop(heard)) stop()
        return PromptAnswer(accepted = accepted, expired = expired, stopped = stopped)
    }

    fun promptUnlock(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastUnlockAt < 15_000) return
        lastUnlockAt = now
        TaskRuntime.tracker.headline(AgentLoop.UNLOCK_LINE)
        notice(context, "Unlock your phone", "Pony will continue after you unlock.", null)
        if (!VoicePrefs.voiceEnabled(context)) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            worker.execute { SpeechOutput.speak(context, AgentLoop.UNLOCK_LINE, VoicePrefs.speechRate(context), VoicePrefs.voiceName(context)) }
        } else {
            SpeechOutput.speak(context, AgentLoop.UNLOCK_LINE, VoicePrefs.speechRate(context), VoicePrefs.voiceName(context))
        }
    }

    /**
     * A command from the paired assistant. When the owner asked for it, the
     * steps belong to that live task. Otherwise the assistant is driving on its
     * own: fold the steps into a detached session entry that stays in History
     * but never becomes the live task — so it can't hide the Ask box, replace
     * the Ask input, bury the last recap, or get absorbed by a brain task.
     */
    fun onRemoteStep(context: Context, kind: StepKind, label: String, ok: Boolean, jpeg: ByteArray? = null) {
        val app = context.applicationContext
        init(app)
        val now = System.currentTimeMillis()
        val acting = RemoteStep.opensWorkingCard(kind)
        val owner = TaskRuntime.current()?.takeIf { it.brain == Brains.GROK && !it.implicit }
        if (owner != null) {
            // The owner asked the assistant to do this; the remote steps are that
            // task's steps, shown on the owner's live card as before.
            if (acting) lastActingAt = now
            lastRemoteAt = now
            TaskRuntime.step(kind, label, ok, jpeg = jpeg, id = owner.id, ofOtherApp = true)
            showOverlay(app)
            return
        }
        val open = sessionId?.let { id -> TaskRuntime.tracker.find(id)?.takeIf { it.running } }
        // A passive look or read with no session open stays invisible: while the
        // assistant is only watching, the owner's Ask box must stay reachable.
        if (open == null && !acting) return
        val id = remoteSession(app, now)
        if (acting) lastActingAt = now
        lastRemoteAt = now
        sessionStepAt = now
        TaskRuntime.stepOn(id, kind, label, ok, jpeg = jpeg, ofOtherApp = true)
        scheduleSessionIdle(id)
    }

    /**
     * The id of the open paired-assistant session, starting a new one when there
     * is none or the last went idle past [SESSION_GAP_MS]. Coalescing by this gap
     * keeps History to one line per connection instead of dozens of tiny tasks.
     */
    private fun remoteSession(app: Context, now: Long): String {
        val open = sessionId?.let { id -> TaskRuntime.tracker.find(id)?.takeIf { it.running } }
        if (open != null && now - sessionStepAt < SESSION_GAP_MS) return open.id
        open?.let { finishSession(it.id) }
        val grok = grokName(app, SessionRepository.snapshot())
        val record = TaskRuntime.tracker.beginDetached(
            "$grok used your phone",
            Brains.GROK,
            grok,
            headline = "$grok is using your phone",
        )
        sessionId = record.id
        sessionStepAt = now
        return record.id
    }

    private fun finishSession(id: String) {
        val record = TaskRuntime.tracker.find(id) ?: return
        if (record.state.finished) {
            if (sessionId == id) sessionId = null
            return
        }
        val outcome = if (record.actionCount > 0) "${record.brainLabel} used your phone." else "${record.brainLabel} looked at your phone."
        TaskRuntime.tracker.finishDetached(id, TaskState.Done, outcome)
        if (sessionId == id) sessionId = null
    }

    /** Closes a detached session once the assistant has gone quiet for the idle gap. */
    private fun scheduleSessionIdle(id: String) {
        timers.schedule({
            if (sessionId != id) return@schedule
            if (System.currentTimeMillis() - sessionStepAt >= SESSION_GAP_MS) finishSession(id)
            else scheduleSessionIdle(id)
        }, SESSION_GAP_MS / 2, TimeUnit.MILLISECONDS)
    }

    /**
     * The assistant is back in `wait_for_request`. That finishes its last
     * running task, but it does NOT lift a Stop: once the owner stops an
     * external session it stays stopped until they pick it back up, so the
     * bot can't quietly carry on the moment it loops back to waiting.
     */
    fun onAssistantWaiting() {
        val task = TaskRuntime.current() ?: return
        if (task.brain != Brains.GROK || task.state != TaskState.Running) return
        if (System.currentTimeMillis() - pickedUpAt < 1_500 && !task.implicit) return
        TaskRuntime.tracker.finish(TaskState.Done, lastSpoken?.takeIf { it.isNotBlank() } ?: "${task.brainLabel} finished.", task.id)
        lastSpoken = null
    }

    /**
     * The assistant finished [ref] (or the current task). Blank [text] finishes
     * quietly with whatever it last said; otherwise the result is shown and,
     * for spoken requests, said out loud. False when that task isn't current.
     */
    fun onAssistantDone(context: Context, text: String, ok: Boolean, ref: String? = null): Boolean {
        val task = TaskRuntime.current() ?: return false
        if (task.brain != Brains.GROK) return false
        if (!RemoteStep.doneClears(task.implicit, task.id, ref)) return false
        val said = text.trim()
        val fallback = lastSpoken?.takeIf { it.isNotBlank() }
            ?: if (ok) "${task.brainLabel} finished." else "${task.brainLabel} couldn't finish."
        TaskRuntime.tracker.finish(if (ok) TaskState.Done else TaskState.Failed, said.ifEmpty { fallback }, task.id)
        lastSpoken = null
        if (said.isNotEmpty() && (task.source != "typed" || VoicePrefs.spokenReplies(context))) {
            worker.execute { speakOut(context.applicationContext, said) }
        }
        return true
    }

    fun onSessionStarted() {
        StopState.gate.release(StopGate.Release.NEW_SESSION)
        BackgroundHost.resetSession()
        // A fresh connection starts a fresh session entry.
        sessionId?.let { finishSession(it) }
    }

    fun onSessionEnded(reason: String) {
        sessionId?.let { finishSession(it) }
        val task = TaskRuntime.current() ?: return
        if (task.brain != Brains.GROK) return
        when (task.state) {
            TaskState.Running, TaskState.Sent -> TaskRuntime.tracker.finish(TaskState.Failed, "The connection to ${task.brainLabel} ended. $reason", task.id)
            else -> TaskRuntime.tracker.setState(TaskState.Waiting, "${task.brainLabel} isn't connected. Pony will hand this over when it's back.", task.id)
        }
    }

    private fun onDelivered(request: OwnerRequest) {
        StopState.gate.release(StopGate.Release.NEW_REQUEST)
        pickedUpAt = System.currentTimeMillis()
        lastRemoteAt = pickedUpAt
        scheduleRemoteIdle(request.id, DELIVERED_IDLE_MS)
        val app = appContext
        val grok = app?.let { grokName(it, SessionRepository.snapshot()) } ?: GrokBot.NAME
        val task = TaskRuntime.current()
        if (task?.id == request.id) {
            TaskRuntime.tracker.setState(TaskState.Running, "$grok picked it up", request.id)
            TaskRuntime.step(StepKind.Status, "$grok picked it up", id = request.id)
        } else {
            TaskRuntime.tracker.begin(request.text, request.source, Brains.GROK, grok, TaskState.Running, id = request.id, headline = "$grok picked it up")
        }
        app?.let { showOverlay(it) }
    }

    private fun scheduleHandoff(
        app: Context,
        taskId: String,
        text: String,
        source: String,
        gen: Int,
        fallback: BrainRouter.Fallback?,
        waitMs: Long,
        grok: String,
        skills: List<Skill>?,
        returnToPony: Boolean,
    ) {
        timers.schedule({
            if (generation.get() != gen || StopState.gate.blocks(taskId)) return@schedule
            if (fallback == null) {
                if (VoiceBus.inbox.isQueued(taskId)) {
                    TaskRuntime.tracker.headline("Still waiting. $grok picks this up the next time it checks in.", taskId)
                }
                return@schedule
            }
            if (VoiceBus.inbox.take(taskId) == null) return@schedule
            when (fallback) {
                BrainRouter.Fallback.PHONE -> {
                    val brain = activeBrain(app) ?: return@schedule
                    TaskRuntime.tracker.reassign(Brains.PHONE, brain.first.name, "$grok didn't pick this up, so ${brain.first.name} on this phone is doing it", taskId)
                    TaskRuntime.step(StepKind.Status, "Handed to ${brain.first.name} on this phone", id = taskId)
                    runPhone(app, taskId, text, source, gen, brain)
                }
                BrainRouter.Fallback.BASICS -> {
                    val plan = skills ?: return@schedule
                    TaskRuntime.tracker.reassign(Brains.BASICS, Brains.BASICS_LABEL, "$grok didn't pick this up, so Pony is doing it on the phone", taskId)
                    TaskRuntime.step(StepKind.Status, "Handed to Pony Basics", id = taskId)
                    runBasics(app, taskId, plan, source, gen, returnToPony)
                }
            }
        }, waitMs, TimeUnit.MILLISECONDS)
    }

    /** Finishes an assistant task once the assistant has gone quiet, so it never sits at "picked it up". */
    private fun scheduleRemoteIdle(taskId: String, idleMs: Long = REMOTE_IDLE_MS) {
        timers.schedule({
            val task = TaskRuntime.current() ?: return@schedule
            if (task.id != taskId || task.brain != Brains.GROK || task.state.finished) return@schedule
            if (task.state == TaskState.Waiting) {
                scheduleRemoteIdle(taskId, idleMs)
                return@schedule
            }
            // An implicit card (no owner ask) clears once the assistant stops acting,
            // even if it keeps reading the screen. A delivered task is kept alive by
            // any remote step, since reading is part of doing the work.
            val since = if (task.implicit) lastActingAt else lastRemoteAt
            if (System.currentTimeMillis() - since >= idleMs) {
                val outcome = lastSpoken?.takeIf { it.isNotBlank() } ?: when {
                    task.actionCount > 0 -> "${task.brainLabel} finished."
                    else -> "${task.brainLabel} got this. It didn't need the phone for it."
                }
                TaskRuntime.tracker.finish(TaskState.Done, outcome, taskId)
                lastSpoken = null
            } else {
                scheduleRemoteIdle(taskId, idleMs)
            }
        }, idleMs / 3, TimeUnit.MILLISECONDS)
    }

    private fun runPhone(app: Context, taskId: String, text: String, source: String, gen: Int, brain: Pair<ProviderRecord, String>) {
        worker.execute {
            val lock = wakeLock(app)
            running.set(true)
            BackgroundNotifier.hold(app)
            WakeWordService.pause(app)
            try {
                lock?.acquire(10 * 60 * 1000L)
                val (record, secret) = brain
                val watch = watchFor(gen, taskId)
                val loop = AgentLoop(
                model = StepModel { messages -> ChatClient.complete(record, secret, messages, tools = true) },
                execute = { call -> if (!isCurrent(gen, taskId)) "stopped" else PhoneOps.execute(app, call, watch) },
                guard = { call -> guardFor(app, call) },
                confirm = { prompt -> confirmBlocking(app, prompt) },
                ask = { question -> askBlocking(app, question) },
                observe = { PhoneOps.screen(app) },
                stopped = { !isCurrent(gen, taskId) },
                locked = { LockState.isLocked(app) },
                waitForUnlock = {
                    promptUnlock(app)
                    LockState.awaitUnlock(app) { !isCurrent(gen, taskId) }
                },
                memory = Memory.summary(app),
            )
            UndoLog.start(taskId)
            RecapShots.start(taskId)
            leaveOwnUi(app)
            val result = loop.run(text)
            if (!isCurrent(gen, taskId)) return@execute
            if (result.status == "capped") {
                val steps = TaskRuntime.tracker.find(taskId)?.steps.orEmpty()
                finish(app, taskId, TaskState.Failed, Outcomes.capped(result.steps, Outcomes.lastAction(steps)), source, resumable = true)
            } else {
                val state = when (result.status) {
                    "done" -> TaskState.Done
                    "stopped" -> TaskState.Stopped
                    else -> TaskState.Failed
                }
                finish(app, taskId, state, result.message, source)
            }
            log(app, "voice_result", "${result.status}: ${result.message.take(180)}", result.status == "done")
            } catch (err: Exception) {
                if (isCurrent(gen, taskId)) finish(app, taskId, TaskState.Failed, BrainError.message(err), source)
            } finally {
                running.set(false)
                if (lock?.isHeld == true) lock.release()
                BackgroundNotifier.releaseHold(app)
                WakeWordService.resume(app)
            }
        }
    }

    private fun runBasics(app: Context, taskId: String, skills: List<Skill>, source: String, gen: Int, returnToPony: Boolean) {
        worker.execute {
            running.set(true)
            BackgroundNotifier.hold(app)
            try {
                val outcome = SkillRunner.run(app, skills) { !isCurrent(gen, taskId) }
                if (!isCurrent(gen, taskId)) return@execute
                finish(app, taskId, if (outcome.ok) TaskState.Done else TaskState.Failed, outcome.message, source)
                if (returnToPony) {
                    Thread.sleep(1_400)
                    openPony(app)
                }
            } finally {
                running.set(false)
                BackgroundNotifier.releaseHold(app)
            }
        }
    }

    private fun finish(app: Context, taskId: String, state: TaskState, message: String, source: String, resumable: Boolean = false) {
        TaskRuntime.tracker.finish(state, message, taskId, resumable)
        if (source != "typed" || VoicePrefs.spokenReplies(app)) speakOut(app, message)
    }

    /**
     * Runs the one safe undo the recap offered for the finished task: clearing a
     * draft Pony typed but never sent. It reopens the app the draft was typed in,
     * waits for it to settle, then clears the focused field — so it acts on that
     * screen, not on Pony's own UI that's in front when the user taps Undo. Never
     * reaches here for anything already sent or paid; [UndoLog] withholds the
     * offer once a task commits.
     */
    fun undoLast(context: Context) {
        val app = context.applicationContext
        val taskId = TaskRuntime.live.value?.id
        val action = UndoLog.offer(taskId) ?: return
        UndoLog.consume(taskId)
        worker.execute {
            val a11y = PonyAccessibilityService.instance
            if (a11y == null) {
                speakOut(app, "Turn on accessibility and I can undo that.")
                return@execute
            }
            val said = when (action) {
                is UndoableAction.ClearDraft -> clearDraft(app, a11y, action)
            }
            log(app, "undo", action.summary, true)
            speakOut(app, said)
        }
    }

    private fun clearDraft(app: Context, a11y: PonyAccessibilityService, action: UndoableAction.ClearDraft): String {
        if (action.app != null && action.app != app.packageName) {
            ScreenRouter.open(app, action.app, null, consent = { true }, watch = Watch.None)
            a11y.waitUntilIdle(android.view.Display.DEFAULT_DISPLAY, 1_800)
        }
        val cleared = ScreenRouter.type(app, "", append = false, null, Watch.None)
        return if (cleared.ok) "Cleared the draft." else "I couldn't find that draft to clear."
    }


    /** Pony's own chat screen is not the task: if it's in front on the main screen, go home before the first look. */
    private fun leaveOwnUi(app: Context) {
        val a11y = PonyAccessibilityService.instance ?: return
        val target = ScreenRouter.targetFor(app, null)
        val onMain = target.displayId == android.view.Display.DEFAULT_DISPLAY
        if (!OwnUi.inFront(onMain, a11y.foregroundPackage(target.displayId), app.packageName)) return
        a11y.press("home")
        a11y.waitUntilIdle(target.displayId, 1_500)
        TaskRuntime.step(StepKind.Status, "Left Pony to look at your screen")
    }

    private fun speakOut(app: Context, message: String) {
        val say = Prose.plain(message)
        if (!VoicePrefs.voiceEnabled(app) || say.isBlank()) return
        SpeechOutput.speak(app, say, VoicePrefs.speechRate(app), VoicePrefs.voiceName(app))
    }

    private fun guardFor(app: Context, call: ToolCall): Guard {
        val verdict = when (call.name) {
            "tap" -> {
                val x = call.args["x"]?.toDoubleOrNull()
                val y = call.args["y"]?.toDoubleOrNull()
                val target = if (x != null && y != null) ScreenRouter.tapTarget(app, x, y, null) else TapTarget(call.args["label"].orEmpty())
                SafetyPolicy.forTap(target.copy(label = target.label.ifBlank { call.args["label"].orEmpty() }))
            }
            "type" -> {
                val display = ScreenRouter.targetFor(app, null).displayId
                SafetyPolicy.forType(PonyAccessibilityService.instance?.focusedIsPassword(display) == true)
            }
            "open_app" -> SafetyPolicy.forOpenApp(call.args["package"] ?: call.args["packageName"].orEmpty())
            else -> Verdict.Allow
        }
        return when (verdict) {
            Verdict.Allow -> Guard.Allow
            is Verdict.Confirm -> Guard.Confirm(verdict.prompt)
            is Verdict.Block -> Guard.Refuse(verdict.reason)
        }
    }

    private fun watchFor(gen: Int, taskId: String) = object : Watch {
        override fun deferred(reason: String, waitedMs: Long) {
            TaskRuntime.tracker.setState(TaskState.Waiting, waitingLine(reason), taskId)
        }

        override fun cancelled(): Boolean = !isCurrent(gen, taskId)
    }

    fun waitingLine(reason: String): String = when (reason) {
        app.pony.companion.display.DisplayPolicy.CALL_UI_FOREGROUND -> "Waiting for the call screen to close. Pony won't touch it."
        app.pony.companion.display.DisplayPolicy.COVERED_BY_POPUP -> "Waiting for a pop-up to move out of the way…"
        else -> "Waiting…"
    }

    private fun isCurrent(gen: Int, taskId: String): Boolean = generation.get() == gen && !StopState.gate.blocks(taskId)

    private fun activeBrain(app: Context): Pair<ProviderRecord, String>? = runCatching {
        val library = BrainLibrary(File(app.filesDir, "brains"), KeystoreSecretBox())
        val record = library.active() ?: return null
        val secret = library.secret(record.id)?.takeIf { it.isNotBlank() } ?: return null
        record to secret
    }.getOrNull()

    fun grokName(app: Context, session: SessionUi): String =
        session.clientName?.takeIf { it.isNotBlank() } ?: VoicePrefs.preferredClient(app).ifBlank { GrokBot.NAME }

    private fun routeName(route: BrainRouter.Route): String = when (route) {
        BrainRouter.Route.Deliver -> "grok_listening"
        is BrainRouter.Route.Queue -> "grok_queue"
        is BrainRouter.Route.Hold -> "grok_hold"
        is BrainRouter.Route.Phone -> "phone_brain"
        is BrainRouter.Route.Basics -> "basics"
        BrainRouter.Route.NeedsKey -> "needs_key"
    }

    private fun notice(context: Context, title: String, body: String, action: NoticeAction?) {
        val id = serial.incrementAndGet()
        _voice.update { it.copy(notice = Notice(id, title, body, action), listening = false) }
        showOverlay(context.applicationContext)
        main.postDelayed({ _voice.update { if (it.notice?.id == id) it.copy(notice = null) else it } }, if (action == NoticeAction.ALLOW_MIC) 9_000 else 5_000)
    }

    private fun showOverlay(context: Context) {
        if (PonyAccessibilityService.instance != null) OverlayHost.show()
        appContext = context.applicationContext
    }

    private fun openPony(app: Context) {
        val intent = Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { PonyAccessibilityService.instance?.launch(intent, null) ?: app.startActivity(intent) }
    }

    private fun log(context: Context, action: String, detail: String, ok: Boolean) {
        val entries = AuditLog(context).append(AuditEntry(action = action, detail = detail, ok = ok))
        SessionRepository.update { it.copy(log = entries) }
    }

    private fun wakeLock(context: Context): PowerManager.WakeLock? {
        val pm = context.getSystemService(PowerManager::class.java) ?: return null
        return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pony:task").apply { setReferenceCounted(false) }
    }
}
