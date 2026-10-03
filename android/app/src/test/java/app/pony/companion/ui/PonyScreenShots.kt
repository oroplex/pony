package app.pony.companion.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pony.companion.brain.ProviderPreset
import app.pony.companion.brain.ProviderRecord
import app.pony.companion.memory.MemoryEntry
import app.pony.companion.overlay.AssistantPanel
import app.pony.companion.overlay.OverlayPill
import app.pony.companion.overlay.PillState
import app.pony.companion.recap.Recap
import app.pony.companion.recap.RecapShots
import app.pony.companion.recap.UndoableAction
import app.pony.companion.schedule.Schedule
import app.pony.companion.schedule.ScheduledTask
import app.pony.companion.session.Connection
import app.pony.companion.session.ReadinessState
import app.pony.companion.session.SessionLength
import app.pony.companion.session.SessionUi
import app.pony.companion.tasks.Brains
import app.pony.companion.tasks.StepKind
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskState
import app.pony.companion.tasks.TaskStep
import app.pony.companion.ui.design.OrbMood
import app.pony.companion.ui.design.Tone
import app.pony.companion.ui.screens.AdvancedScreen
import app.pony.companion.ui.screens.AdvancedState
import app.pony.companion.ui.screens.AskScreen
import app.pony.companion.ui.screens.AskState
import app.pony.companion.ui.screens.BrainsScreen
import app.pony.companion.ui.screens.BrainsState
import app.pony.companion.ui.screens.ConnectionScreen
import app.pony.companion.ui.screens.ConnectionState
import app.pony.companion.ui.screens.HistoryScreen
import app.pony.companion.ui.screens.HomeScreen
import app.pony.companion.ui.screens.HomeState
import app.pony.companion.ui.screens.ListenHelpScreen
import app.pony.companion.ui.screens.MemoryScreen
import app.pony.companion.ui.screens.MemoryState
import app.pony.companion.ui.screens.SchedulesScreen
import app.pony.companion.ui.screens.SchedulesState
import app.pony.companion.ui.screens.PairScreen
import app.pony.companion.ui.screens.PairState
import app.pony.companion.ui.screens.PrivacyScreen
import app.pony.companion.ui.screens.SettingsScreen
import app.pony.companion.ui.screens.SettingsState
import app.pony.companion.ui.screens.SetupScreen
import app.pony.companion.ui.screens.ShizukuSetupScreen
import app.pony.companion.ui.screens.ShizukuSetupState
import app.pony.companion.ui.screens.TaskDetailScreen
import app.pony.companion.ui.screens.TryItScreen
import app.pony.companion.ui.screens.UpdatesScreen
import app.pony.companion.ui.screens.VoiceScreen
import app.pony.companion.ui.screens.VoiceState
import app.pony.companion.ui.screens.WelcomeScreen
import app.pony.companion.ui.theme.ThemeMode
import app.pony.companion.update.UpdateManifest
import app.pony.companion.update.UpdateState
import app.pony.companion.voice.Notice
import app.pony.companion.voice.NoticeAction
import app.pony.companion.voice.Prompt
import app.pony.companion.voice.VoiceCatalog
import app.pony.companion.voice.VoicePrefs
import app.pony.companion.voice.VoiceUi
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Every screen, in dark and light, at a Galaxy-sized window. Stills named
 * flow-NN-* read in order as the first minute with Pony.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class PonyScreenShots {
    private val now = 1_790_000_000_000L
    private val readinessPartial = ReadinessState(accessibility = true, notifications = true, microphone = false, battery = false, keyboard = false, camera = false)
    private val readinessNone = ReadinessState()
    private val readinessAll = ReadinessState(accessibility = true, notifications = true, microphone = true, battery = true, keyboard = true, assistant = true, camera = true)

    @Test fun welcome() = both("welcome") { WelcomeScreen(pendingLink = false, onStart = {}) }

    @Test fun privacy() = both("privacy") { PrivacyScreen(checked = true, pendingLink = false, onChecked = {}, onAgree = {}, onBack = {}) }

    @Test fun setup() = both("setup") { Setup(readinessPartial, onboarding = true) }

    @Test fun setupNotStarted() = shot("setup-control-off") { Setup(readinessNone, onboarding = true, restricted = true) }

    @Test fun tryIdle() = shot("try-it") { TryIt(null) }

    @Test fun tryRunning() = shot("try-it-running") { TryIt(calcTask(TaskState.Running, steps = 4)) }

    @Test fun tryDone() = both("try-it-done") { TryIt(calcTask(TaskState.Done, steps = 6, outcome = "2 + 2 = 4")) }

    @Test fun homeNotConnected() = both("home-not-connected") { Home(home(offline(), recent = emptyList())) }

    @Test fun homeReadyBrain() = both("home-ready-brain") { Home(home(offline(), recent = recentTasks(), keyBrain = "Anthropic Claude")) }

    @Test fun homeListening() = both("home-listening") { Home(home(listening(), recent = recentTasks())) }

    @Test fun homeWorking() = shot("home-working") {
        Home(home(listening(), recent = recentTasks(), live = calcTask(TaskState.Running, steps = 3, headline = "Tapped “+”")))
    }

    @Test fun homeReconnecting() = shot("home-reconnecting") {
        Home(home(SessionUi(connection = Connection.Reconnecting, nextRetryAt = now + 4_000, reconnectAttempt = 3), recent = recentTasks()))
    }

    @Test fun homeResumable() = shot("home-reconnect") { Home(home(offline(), recent = recentTasks(), resumable = true)) }

    @Test fun homeLoading() = shot("home-loading") { Home(home(offline(), recent = emptyList()).copy(loading = true)) }

    @Test fun homeSetupNeeded() = shot("home-setup-needed") { Home(home(offline(), recent = emptyList()).copy(needsSetup = true)) }

    @Test fun homeLargeText() = shot("home-large-text", fontScale = 1.3f) { Home(home(listening(), recent = recentTasks())) }

    @Test fun askOffline() = both("ask-grok-offline") { Ask(askState(offline(), key = null)) }

    @Test fun askNotListening() = shot("ask-grok-not-listening") { Ask(askState(SessionUi(connection = Connection.Connected, clientName = "Grok Bot"), key = null)) }

    @Test fun askBackup() = shot("ask-grok-with-backup") { Ask(askState(SessionUi(connection = Connection.Connected, clientName = "Grok Bot"), key = "Claude")) }

    @Test fun askReady() = shot("ask-grok-listening") { Ask(askState(listening(), key = null, listeningNow = true)) }

    @Test fun askListening() = both("ask-voice-listening") {
        Ask(askState(listening(), key = null, listeningNow = true, voice = VoiceUi(listening = true, transcript = "open the calculator and add", level = 0.6f)))
    }

    @Test fun askMicBlocked() = shot("ask-mic-blocked") {
        Ask(askState(listening(), key = null, listeningNow = true, voice = VoiceUi(notice = Notice(1, "Pony can't hear you yet", "Allow the microphone so Pony can listen.", NoticeAction.ALLOW_MIC))))
    }

    @Test fun askQueued() = shot("ask-queued") {
        Ask(askState(SessionUi(connection = Connection.Connected, clientName = "Grok Bot"), key = null, task = calcTask(TaskState.Queued, steps = 0, headline = "Waiting for Grok Bot to check in…", brain = Brains.GROK)))
    }

    @Test fun askRunning() = both("ask-thread-running") {
        Ask(askState(listening(), key = null, listeningNow = true, task = calcTask(TaskState.Running, steps = 4, brain = Brains.GROK, headline = "Tapped “2”")))
    }

    @Test fun askDone() = both("ask-thread-done") {
        Ask(askState(listening(), key = null, listeningNow = true, task = calcTask(TaskState.Done, steps = 7, brain = Brains.GROK, outcome = "2 + 2 = 4")))
    }

    @Test fun askLargeText() = shot("ask-large-text", fontScale = 1.3f) { Ask(askState(offline(), key = null)) }

    @Test fun history() = both("history") { HistoryScreen(recentTasks(), loading = false, now = now, onOpen = {}, onClear = {}, onBack = {}, onAsk = {}) }

    @Test fun historyEmpty() = shot("history-empty") { HistoryScreen(emptyList(), loading = false, now = now, onOpen = {}, onClear = {}, onBack = {}, onAsk = {}) }

    @Test fun taskDetail() = both("task-detail") {
        val task = calcTask(TaskState.Done, steps = 7, brain = Brains.GROK, outcome = "2 + 2 = 4", shots = true)
        TaskDetailScreen(task, now, shotFile = { _, _ -> fakeShot }, onBack = {}, onAskAgain = {}, onDelete = {}, onStop = {})
    }

    @Test fun taskDetailRecap() = both("task-detail-recap") {
        val steps = listOf(
            TaskStep(now + 400, StepKind.Status, "Grok Bot picked it up"),
            TaskStep(now + 1_200, StepKind.Open, "Opened WhatsApp"),
            TaskStep(now + 2_000, StepKind.Tap, "Opened the chat with Maya"),
            TaskStep(now + 2_600, StepKind.Type, "Typed “Running ten late”"),
        )
        val task = TaskRecord(
            id = "draft-recap",
            text = "Tell Maya I'm running ten minutes late",
            source = "voice",
            brain = Brains.GROK,
            brainLabel = "Grok Bot",
            createdAt = now,
            state = TaskState.Done,
            steps = steps,
            outcome = "Typed the message and left it ready for you to send.",
            endedAt = now + 3_000,
        )
        val recap = Recap("Typed the message.", emptyList(), UndoableAction.ClearDraft(chars = 19, app = "com.whatsapp"))
        TaskDetailScreen(
            task, now, shotFile = { _, _ -> fakeShot }, onBack = {}, onAskAgain = {}, onDelete = {}, onStop = {},
            recap = recap,
            recapShots = RecapShots.Shots(shotBytes(0xFF1E252D.toInt(), 0xFF3D7BF7.toInt()), shotBytes(0xFF14331F.toInt(), 0xFF2FA968.toInt())),
            onUndo = {},
        )
    }

    @Test fun pairScan() = both("pair") { Pair(PairState(false, null, false, false, null, null, "Grok Bot")) }

    @Test fun pairError() = shot("pair-error") {
        Pair(PairState(true, "That relay address isn't allowed. Use Pony Cloud, Tailscale, or your home network.", true, false, null, null, "Grok Bot"))
    }

    @Test fun pairSafety() = both("pair-safety-code") { Pair(PairState(true, null, false, false, "482-193", "Grok Bot", "Grok Bot")) }

    @Test fun listenHelp() = shot("listen-help") { ListenHelpScreen("", onCopy = {}, onBack = {}) }

    @Test fun settings() = both("settings") { Settings() }

    @Test fun settingsLargeText() = shot("settings-large-text", fontScale = 1.3f) { Settings() }

    @Test fun settingsConnected() = both("settings-connected") { Settings(connected = true) }

    @Test fun memory() = both("memory") {
        MemoryScreen(
            MemoryState(
                entries = listOf(
                    MemoryEntry("name", "Alex", "owner", now),
                    MemoryEntry("home_city", "Portland", "owner", now),
                    MemoryEntry("coffee_order", "oat flat white, extra hot", "owner", now),
                ),
                loading = false,
            ),
            onForget = {}, onClearAll = {}, onBack = {},
        )
    }

    @Test fun memoryEmpty() = shot("memory-empty") {
        MemoryScreen(MemoryState(entries = emptyList(), loading = false), onForget = {}, onClearAll = {}, onBack = {})
    }

    @Test fun schedules() = both("schedules") {
        SchedulesScreen(
            SchedulesState(
                tasks = listOf(
                    ScheduledTask("a1", "read me my calendar", Schedule(7, 0), enabled = true, createdAt = now - 4000),
                    ScheduledTask("a2", "water the plants", Schedule(8, 30, Schedule.WEEKDAYS), enabled = true, createdAt = now - 3000),
                    ScheduledTask("a3", "wind down and stretch", Schedule(21, 0), enabled = false, createdAt = now - 2000),
                    ScheduledTask("a4", "pick up the dry cleaning", Schedule(18, 0, onceAt = now + 30 * 60 * 60 * 1000L), enabled = true, createdAt = now - 1000),
                ),
                loading = false,
                now = now,
            ),
            onToggle = { _, _ -> }, onDelete = {}, onClearAll = {}, onBack = {},
        )
    }

    @Test fun schedulesEmpty() = shot("schedules-empty") {
        SchedulesScreen(SchedulesState(tasks = emptyList(), loading = false, now = now), onToggle = { _, _ -> }, onDelete = {}, onClearAll = {}, onBack = {})
    }

    @Test fun voice() = both("voice") {
        VoiceScreen(
            VoiceState(
                voiceEnabled = true,
                wakeWord = false,
                chargingOnly = true,
                micGranted = false,
                assistantHeld = false,
                speechRate = 1.0f,
                voices = listOf(
                    VoiceCatalog.Option("en-us-neural", "en-US", VoiceCatalog.QUALITY_VERY_HIGH),
                    VoiceCatalog.Option("en-gb-enhanced", "en-GB", VoiceCatalog.QUALITY_HIGH),
                    VoiceCatalog.Option("en-au-standard", "en-AU", VoiceCatalog.QUALITY_NORMAL),
                ),
                voicesLoading = false,
                selectedVoice = "en-us-neural",
                cloudVoice = false,
                wakeStatus = "",
                fullScreenNeeded = false,
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun voiceCloud() = shot("voice-cloud") {
        VoiceScreen(
            VoiceState(
                voiceEnabled = true,
                wakeWord = false,
                chargingOnly = true,
                micGranted = true,
                assistantHeld = false,
                speechRate = 1.1f,
                voices = listOf(
                    VoiceCatalog.Option("en-us-cloud", "en-US", VoiceCatalog.QUALITY_VERY_HIGH, networkRequired = true),
                    VoiceCatalog.Option("en-us-neural", "en-US", VoiceCatalog.QUALITY_VERY_HIGH),
                    VoiceCatalog.Option("he-il-standard", "he-IL", VoiceCatalog.QUALITY_NORMAL),
                ),
                voicesLoading = false,
                selectedVoice = "",
                cloudVoice = true,
                wakeStatus = "",
                fullScreenNeeded = false,
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun connection() = both("connection") {
        ConnectionScreen(
            ConnectionState(true, "http://100.64.1.10:8787", listOf("http://100.64.1.10:8787"), true, "Connected to Grok Bot", "Pony Cloud", "1 hr 42 min left"),
            null, {}, {}, {}, {}, {},
        )
    }

    @Test fun advanced() = both("advanced") { AdvancedScreen(AdvancedState(false, true, false, false, false, false, null), {}, {}, {}, {}, {}, {}, {}) }

    @Test fun shizukuSetupStart() = both("shizuku-setup-start") {
        ShizukuSetupScreen(ShizukuSetupState(installed = false, devOptions = false, running = false, granted = false), {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun shizukuSetupConnecting() = both("shizuku-setup-connecting") {
        ShizukuSetupScreen(ShizukuSetupState(installed = true, devOptions = true, running = false, granted = false), {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun shizukuSetupReady() = both("shizuku-setup-ready") {
        ShizukuSetupScreen(ShizukuSetupState(installed = true, devOptions = true, running = true, granted = true), {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun updatesAvailable() = both("updates-available") { UpdatesScreen(UpdateState.Available(manifest), "0.5.0", {}, {}, {}, {}) }

    @Test fun updatesDownloading() = shot("updates-downloading") { UpdatesScreen(UpdateState.Downloading(manifest, 0.62f), "0.5.0", {}, {}, {}, {}) }

    @Test fun updatesCurrent() = shot("updates-up-to-date") { UpdatesScreen(UpdateState.UpToDate("0.5.0"), "0.5.0", {}, {}, {}, {}) }

    @Test fun brains() = both("brains") { Brains(listOf(claude)) }

    @Test fun brainsEmpty() = shot("brains-empty") { Brains(emptyList()) }

    @Test fun brainsPhone() = both("brains-picker") { Brains(listOf(claude), grokSelected = false) }

    @Test fun brainSetup() = both("brains-setup") {
        BrainsScreen(
            BrainsState("Grok Bot", grokSelected = false, grokConnected = false, grokListening = false, providers = listOf(claude)),
            {}, {}, { null }, {}, { _, _, _ -> }, { _, _, _ -> }, {}, {}, {}, {}, {}, {},
            startSetup = ProviderPreset.GEMINI,
        )
    }

    @Test fun brainsDisconnect() = both("brains-disconnect") {
        BrainsScreen(
            BrainsState("Grok Bot", grokSelected = false, grokConnected = false, grokListening = false, providers = listOf(claude)),
            {}, {}, { null }, {}, { _, _, _ -> }, { _, _, _ -> }, {}, {}, {}, {}, {}, {},
            startSetup = ProviderPreset.ANTHROPIC,
        )
    }

    @Test fun brainEditor() = shot("brains-add") {
        BrainsScreen(
            BrainsState("Grok Bot", true, false, false, emptyList()),
            {}, {}, { null }, {}, { _, _, _ -> }, { _, _, _ -> }, {}, {}, {}, {}, {}, {},
            startEditing = true,
        )
    }

    @Test fun overlayWorking() = both("overlay-working") {
        FakeApp(dark = true) { OverlayPill(PillState.Working(calcTask(TaskState.Running, steps = 3, brain = Brains.GROK, headline = "Tapped “+”")), minimized = false) }
    }

    @Test fun overlayConfirm() = shot("overlay-confirm") { FakeApp(dark = true) { OverlayPill(PillState.Confirm("Send this in WhatsApp?"), minimized = false) } }

    @Test fun overlayListening() = shot("overlay-listening") { FakeApp(dark = true) { OverlayPill(PillState.Listening("set a timer for five", 0.7f), minimized = false) } }

    @Test fun overlayDone() = shot("overlay-done") { FakeApp(dark = true) { OverlayPill(PillState.Finished(calcTask(TaskState.Done, 6, outcome = "2 + 2 = 4")), minimized = false) } }

    @Test fun overlayInCall() = shot("overlay-in-call") {
        FakeApp(dark = true) { OverlayPill(PillState.Working(calcTask(TaskState.Waiting, 3, headline = "Waiting for the call screen to close. Pony won't touch it.")), minimized = true) }
    }

    @Test fun overlayMic() = shot("overlay-allow-mic") {
        FakeApp(dark = true) { OverlayPill(PillState.Note("Pony can't hear you yet", "Allow the microphone so Pony can listen.", NoticeAction.ALLOW_MIC), minimized = false) }
    }

    @Test fun assistantPanel() = shot("assistant-panel") { FakeApp(dark = true) { Box(Modifier.fillMaxSize()) { AssistantPanel(onClose = {}) } } }

    @Test fun flow01() = shot("flow-01-welcome") { WelcomeScreen(pendingLink = false, onStart = {}) }

    @Test fun flow02() = shot("flow-02-privacy") { PrivacyScreen(checked = true, pendingLink = false, onChecked = {}, onAgree = {}, onBack = {}) }

    @Test fun flow03() = shot("flow-03-setup") { Setup(readinessAll, onboarding = true) }

    @Test fun flow04() = shot("flow-04-try-it") { TryIt(null) }

    @Test fun flow05() = shot("flow-05-calculator-overlay") {
        FakeApp(dark = true) { OverlayPill(PillState.Working(calcTask(TaskState.Running, steps = 4, brain = Brains.BASICS, headline = "Tapped “2”")), minimized = false) }
    }

    @Test fun flow06() = shot("flow-06-it-worked") { TryIt(calcTask(TaskState.Done, steps = 6, outcome = "2 + 2 = 4")) }

    @Test fun flow07() = shot("flow-07-home") { Home(home(listening(), recent = recentTasks())) }

    @Test fun flow08() = shot("flow-08-ask") { Ask(askState(listening(), key = null, listeningNow = true)) }

    @Test fun flow09() = shot("flow-09-ask-running") {
        Ask(askState(listening(), key = null, listeningNow = true, task = calcTask(TaskState.Running, steps = 4, brain = Brains.GROK, headline = "Tapped “+”")))
    }

    @Test fun flow10() = shot("flow-10-result") {
        Ask(askState(listening(), key = null, listeningNow = true, task = calcTask(TaskState.Done, steps = 7, brain = Brains.GROK, outcome = "2 + 2 = 4")))
    }

    private fun both(name: String, content: @Composable () -> Unit) {
        shot(name, dark = true, content = content)
        shot("$name-light", dark = false, content = content)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(name: String, dark: Boolean = true, fontScale: Float = 1f, content: @Composable () -> Unit) {
        // build.gradle.kts sets roborazzi.output.dir (PONY_SCREENSHOTS, or build/outputs/roborazzi).
        val dir = File(System.getProperty("roborazzi.output.dir") ?: "build/outputs/roborazzi").apply { mkdirs() }
        captureRoboImage(
            file = File(dir, "$name.png"),
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        ) {
            DeviceFrame(dark = dark, fontScale = fontScale) { content() }
        }
    }

    @Composable
    private fun Setup(readiness: ReadinessState, onboarding: Boolean, restricted: Boolean = false) = SetupScreen(
        items = setupItems(readiness, samsung = true),
        readiness = readiness,
        restrictedHint = restricted,
        samsung = true,
        onboarding = onboarding,
        onAction = {},
        onRestricted = {},
        onSamsungBattery = {},
        onContinue = {},
        onBack = {},
    )

    @Composable
    private fun TryIt(task: TaskRecord?) = TryItScreen(task, controlOn = true, onRun = {}, onStop = {}, onTurnOn = {}, onFinish = {}, onBack = {})

    @Composable
    private fun Home(state: HomeState) = HomeScreen(state, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Composable
    private fun Ask(state: AskState) = AskScreen(state, "", {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, shotFile = { _, _ -> fakeShot })

    @Composable
    private fun Pair(state: PairState) = PairScreen(state, {}, {}, {}, {}, {}, {}, onBack = {}, scanner = {})

    @Composable
    private fun Brains(providers: List<ProviderRecord>, grokSelected: Boolean = true) = BrainsScreen(
        BrainsState("Grok Bot", grokSelected = grokSelected, grokConnected = true, grokListening = true, providers = providers),
        {}, {}, { null }, {}, { _, _, _ -> }, { _, _, _ -> }, {}, {}, {}, {}, {}, {},
    )

    @Composable
    private fun Settings(connected: Boolean = false) = SettingsScreen(
        SettingsState(
            version = "0.6.3",
            versionCode = 10,
            brainLabel = "Grok Bot",
            connectionLabel = "Pony Cloud",
            sessionLength = SessionLength.TWO_HOURS,
            autoReconnect = true,
            connected = connected,
            assistantName = "Grok Bot",
            voiceEnabled = true,
            wakeWord = false,
            spokenReplies = false,
            background = true,
            mainScreen = VoicePrefs.FALLBACK_ASK,
            keepScreenshots = true,
            memoryCount = 3,
            scheduleCount = 2,
            routineCount = 1,
            theme = ThemeMode.SYSTEM,
            update = UpdateState.UpToDate("0.6.0"),
            setupDone = 4,
            setupTotal = 5,
        ),
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
    )

    private fun offline() = SessionUi(connection = Connection.Idle)

    private fun listening() = SessionUi(connection = Connection.Connected, clientName = "Grok Bot", safetyCode = "482-193", endsAt = now + 102 * 60_000)

    private fun home(session: SessionUi, recent: List<TaskRecord>, live: TaskRecord? = null, resumable: Boolean = false, keyBrain: String? = null): HomeState {
        val listening = session.connection == Connection.Connected
        return HomeState(
            greeting = "Good evening",
            subline = when {
                live != null -> "Pony is working on your ask."
                listening -> "Grok Bot is listening. What should we do?"
                keyBrain != null -> "$keyBrain is ready on this phone."
                else -> "What should we do?"
            },
            badge = connectionBadge(session, listening, "Grok Bot", resumable, now, keyBrain),
            assistant = assistantLink(session, keyBrain, resumable, "Grok Bot"),
            mood = homeMood(session, listening, live),
            live = live,
            recent = recent,
            suggestions = SUGGESTIONS.take(4),
            needsSetup = false,
            loading = false,
            version = "0.5.0",
            now = now,
        )
    }

    private fun askState(
        session: SessionUi,
        key: String?,
        listeningNow: Boolean = false,
        task: TaskRecord? = null,
        voice: VoiceUi = VoiceUi(),
    ) = AskState(
        status = askStatus(false, session, listeningNow, key, "Grok Bot"),
        brainLabel = "Grok Bot",
        brainTone = if (listeningNow) Tone.Success else Tone.Iris,
        brainLive = listeningNow,
        task = task,
        voice = voice,
        suggestions = SUGGESTIONS.take(4),
        now = now,
        autoFocus = false,
    )

    private fun calcTask(
        state: TaskState,
        steps: Int,
        brain: String = Brains.BASICS,
        outcome: String? = null,
        headline: String? = null,
        shots: Boolean = false,
    ): TaskRecord {
        val all = listOf(
            TaskStep(now + 400, StepKind.Status, "Grok Bot picked it up"),
            TaskStep(now + 1_200, StepKind.Open, "Opened Calculator"),
            TaskStep(now + 2_000, StepKind.Look, "Looked at the screen", shot = if (shots) "shot-1.jpg" else null),
            TaskStep(now + 2_600, StepKind.Tap, "Tapped “2”"),
            TaskStep(now + 2_900, StepKind.Tap, "Tapped “+”"),
            TaskStep(now + 3_200, StepKind.Tap, "Tapped “2”"),
            TaskStep(now + 3_700, StepKind.Tap, "Tapped “=”"),
            TaskStep(now + 4_300, StepKind.Read, "Read the result: 4"),
        )
        val picked = if (brain == Brains.GROK) all else all.drop(1)
        return TaskRecord(
            id = "calc-$state-$steps",
            text = "Open the calculator and add 2 plus 2",
            source = "typed",
            brain = brain,
            brainLabel = if (brain == Brains.GROK) "Grok Bot" else Brains.BASICS_LABEL,
            createdAt = now,
            state = state,
            steps = picked.take(steps),
            outcome = outcome,
            headline = headline ?: outcome,
            endedAt = if (state.finished) now + 4_600 else null,
        )
    }

    private fun recentTasks(): List<TaskRecord> = listOf(
        calcTask(TaskState.Done, 7, brain = Brains.GROK, outcome = "2 + 2 = 4").copy(id = "r1", createdAt = now - 4 * 60_000),
        TaskRecord("r2", "Set a timer for 5 minutes", "voice", Brains.BASICS, Brains.BASICS_LABEL, now - 52 * 60_000, TaskState.Done,
            listOf(TaskStep(now - 52 * 60_000, StepKind.Open, "Set a 5-minute timer in Clock")), "Your 5-minute timer is running."),
        TaskRecord("r3", "Reply to Maya that I'm running ten minutes late", "typed", Brains.GROK, "Grok Bot", now - 3 * 3_600_000, TaskState.Stopped,
            listOf(TaskStep(now, StepKind.Open, "Opened Messages"), TaskStep(now, StepKind.Confirm, "You said no: Send this in Messages?", ok = false)), "Stopped. Pony won't continue this task."),
        TaskRecord("r4", "Turn on dark mode", "assistant", Brains.PHONE, "Claude", now - 26 * 3_600_000, TaskState.Failed,
            listOf(TaskStep(now, StepKind.Open, "Opened Settings")), "Settings didn't show a dark mode switch Pony could reach."),
    )

    private val claude = ProviderRecord("p1", "Claude", ProviderPreset.ANTHROPIC, "claude-opus-5-5", ProviderPreset.ANTHROPIC.defaultBaseUrl, "7f2c", active = true)

    private val manifest = UpdateManifest(
        versionCode = 6,
        versionName = "0.5.1",
        apkUrl = "https://download.pony.karlmagendavid.com/pony-0.5.1.apk",
        sha256 = "a".repeat(64),
        size = 14_000_000,
        changelog = listOf("Reconnects twice as fast after a network switch", "Calculator math works in more calculator apps", "Quieter haptics while Pony types"),
        publishedAt = "2026-10-14",
        minSdk = 29,
    )

    private val fakeShot: File by lazy {
        val file = File.createTempFile("pony-shot", ".jpg")
        val bitmap = Bitmap.createBitmap(270, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, 520f, 0xFF1E252D.toInt(), 0xFF3D7BF7.toInt(), Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, 270f, 520f, paint)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        file
    }

    private fun shotBytes(top: Int, bottom: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(270, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, 520f, top, bottom, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, 270f, 520f, paint)
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it); it.toByteArray() }
    }
}
