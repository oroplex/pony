package app.pony.companion.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.activity.BackEventCompat
import app.pony.companion.recap.RecapBuilder
import app.pony.companion.recap.RecapShots
import app.pony.companion.recap.UndoLog
import app.pony.companion.session.Connection
import app.pony.companion.session.Readiness
import app.pony.companion.session.SessionRepository
import app.pony.companion.tasks.TaskRuntime
import app.pony.companion.ui.design.ListRow
import app.pony.companion.ui.design.LocalToaster
import app.pony.companion.ui.design.PonyButton
import app.pony.companion.ui.design.ButtonTone
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.design.PonySheet
import app.pony.companion.ui.design.Toaster
import app.pony.companion.ui.design.Tone
import app.pony.companion.ui.design.Trailing
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
import app.pony.companion.ui.screens.PairScreen
import app.pony.companion.ui.screens.PairState
import app.pony.companion.ui.screens.PrivacyScreen
import app.pony.companion.ui.screens.SchedulesScreen
import app.pony.companion.ui.screens.SchedulesState
import app.pony.companion.ui.screens.RoutinesScreen
import app.pony.companion.ui.screens.RoutinesState
import app.pony.companion.ui.screens.SettingsScreen
import app.pony.companion.ui.screens.SettingsState
import app.pony.companion.ui.screens.SetupScreen
import app.pony.companion.ui.screens.ShizukuSetupScreen
import app.pony.companion.ui.screens.TaskDetailScreen
import app.pony.companion.ui.screens.TryItScreen
import app.pony.companion.ui.screens.UpdatesScreen
import app.pony.companion.ui.screens.VoiceScreen
import app.pony.companion.ui.screens.VoiceState
import app.pony.companion.ui.screens.WelcomeScreen
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.SquircleShape
import app.pony.companion.update.UpdateState
import app.pony.companion.update.Updater
import app.pony.companion.voice.RequestInbox
import app.pony.companion.voice.SpeechOutput
import app.pony.companion.voice.VoiceBus
import app.pony.companion.voice.VoiceController
import app.pony.companion.voice.VoicePrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PonyRoot(vm: PonyViewModel, actions: PonyActions, scanner: @Composable (onQr: (String) -> Unit) -> Unit) {
    val nav = vm.nav
    val toaster = remember { Toaster() }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    PredictiveBackHandler(enabled = nav.canPop) { events ->
        try {
            events.collect { event ->
                backProgress = event.progress
                backEdge = event.swipeEdge
            }
            nav.pop()
        } catch (_: CancellationException) {
        } finally {
            backProgress = 0f
        }
    }
    val route = nav.current
    BackHandler(enabled = !nav.canPop && route.onboarding && route != Route.Welcome) {
        when (route) {
            Route.Privacy -> nav.retreat(Route.Welcome)
            Route.Setup -> nav.retreat(Route.Privacy)
            Route.TryIt -> nav.retreat(Route.Setup)
            else -> Unit
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalToaster provides toaster) {
        Box(Modifier.fillMaxSize().background(Pony.colors.canvas)) {
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = nav.current,
                    transitionSpec = { transitionFor(nav.action) },
                    contentKey = { it.key },
                    label = "nav",
                ) { target ->
                    CompositionLocalProvider(LocalShared provides this@SharedTransitionLayout, LocalVisibility provides this) {
                        val progress = if (target == nav.current) backProgress else 0f
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (progress > 0f) {
                                        val scale = 1f - 0.1f * progress
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = (if (backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f) * with(density) { 28.dp.toPx() } * progress
                                        shape = SquircleShape(34.dp * progress)
                                        clip = true
                                    }
                                },
                        ) {
                            Screen(target, vm, actions, scanner)
                        }
                    }
                }
            }
            toaster.Host(Modifier.align(Alignment.BottomCenter))
        }
    }
}

private fun AnimatedContentTransitionScope<Route>.transitionFor(action: NavAction): ContentTransform {
    val from = initialState
    val to = targetState
    val morph = (from is Route.Home && to is Route.Ask) || (from is Route.Ask && to is Route.Home)
    val spec = when {
        morph -> fadeIn(tween(300, 40)) togetherWith fadeOut(tween(180))
        action == NavAction.REPLACE -> (fadeIn(tween(360)) + scaleIn(tween(420), initialScale = 0.96f)) togetherWith fadeOut(tween(200))
        action == NavAction.POP ->
            (slideInHorizontally(app.pony.companion.ui.theme.Motion.slide) { -it / 6 } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(app.pony.companion.ui.theme.Motion.slide) { it / 3 } + fadeOut(tween(200)))
        else ->
            (slideInHorizontally(app.pony.companion.ui.theme.Motion.slide) { it / 3 } + fadeIn(tween(260))) togetherWith
                (slideOutHorizontally(app.pony.companion.ui.theme.Motion.slide) { -it / 6 } + fadeOut(tween(200)))
    }
    spec.targetContentZIndex = if (action == NavAction.POP) -1f else 1f
    return spec using SizeTransform(clip = false)
}

@Composable
private fun rememberNow(periodMs: Long = 1_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs)
        }
    }
    return now
}

@Composable
private fun Screen(route: Route, vm: PonyViewModel, actions: PonyActions, scanner: @Composable (onQr: (String) -> Unit) -> Unit) {
    val context = LocalContext.current
    val nav = vm.nav
    val toaster = LocalToaster.current
    val session by SessionRepository.ui.collectAsState()
    val readiness by Readiness.state.collectAsState()
    val inbox by VoiceBus.state.collectAsState()
    val live by TaskRuntime.live.collectAsState()
    val history by TaskRuntime.history.collectAsState()
    val voice by VoiceController.voice.collectAsState()
    val update by Updater.state.collectAsState()
    val now = rememberNow()
    val grok = session.clientName ?: vm.grokName
    val listening = session.connection == Connection.Connected && !session.peerAway &&
        (inbox.waiters > 0 || (inbox.lastWaitEndedAt > 0 && now - inbox.lastWaitEndedAt < RequestInbox.LISTEN_GRACE_MS))
    val shotFile = { taskId: String, name: String -> TaskRuntime.shotFile(taskId, name) }
    val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    when (route) {
        Route.Welcome -> WelcomeScreen(pendingLink = vm.pendingLink != null, onStart = { nav.advance(Route.Privacy) })
        Route.Privacy -> PrivacyScreen(
            checked = vm.consentChecked,
            pendingLink = vm.pendingLink != null,
            onChecked = { vm.consentChecked = it },
            onAgree = vm::agree,
            onBack = { nav.retreat(Route.Welcome) },
        )
        Route.PrivacyReview -> PrivacyScreen(checked = true, pendingLink = false, onChecked = {}, onAgree = {}, onBack = { nav.pop() }, review = true)
        Route.Setup, Route.Permissions -> SetupScreen(
            items = setupItems(readiness, samsung),
            readiness = readiness,
            restrictedHint = Build.VERSION.SDK_INT >= 33 && !readiness.accessibility,
            samsung = samsung,
            onboarding = route == Route.Setup,
            onAction = { key ->
                when (key) {
                    "control" -> actions.openAccessibility()
                    "notifications" -> actions.requestNotifications()
                    "battery" -> actions.requestBattery()
                    "microphone" -> actions.requestMic(false)
                    else -> actions.openKeyboardSettings()
                }
            },
            onRestricted = actions.openAppDetails,
            onSamsungBattery = actions.openSamsungBattery,
            onContinue = { nav.advance(Route.TryIt) },
            onBack = { if (route == Route.Setup) nav.retreat(Route.Privacy) else nav.pop() },
        )
        Route.TryIt -> {
            val task = vm.tryTaskId?.let { id -> live?.takeIf { it.id == id } ?: history.firstOrNull { it.id == id } }
            TryItScreen(
                task = task,
                controlOn = readiness.accessibility,
                onRun = vm::tryBasics,
                onStop = VoiceController::stop,
                onTurnOn = actions.openAccessibility,
                onFinish = {
                    VoicePrefs.setTriedBasics(context)
                    vm.finishOnboarding()
                },
                onBack = { nav.retreat(Route.Setup) },
                shotFile = shotFile,
            )
        }
        Route.Home -> {
            val running = live?.takeIf { it.running }
            HomeScreen(
                state = HomeState(
                    greeting = greeting(now),
                    subline = when {
                        running != null -> "Pony is working on your ask."
                        session.connection == Connection.Connected && listening -> "$grok is listening. What should we do?"
                        session.connection == Connection.Connected -> "Connected to $grok. What should we do?"
                        vm.activeBrain != null -> "${vm.activeBrain?.name} is ready on this phone."
                        else -> "What should we do?"
                    },
                    badge = connectionBadge(session, listening, grok, vm.resumable, now, keyBrain = vm.activeBrain?.name),
                    assistant = assistantLink(session, vm.activeBrain?.name, vm.resumable, grok),
                    mood = homeMood(session, listening, running),
                    live = running,
                    recent = history.filter { !it.running || it.id != running?.id },
                    suggestions = rememberSuggestions(),
                    needsSetup = !readiness.accessibility,
                    loading = !vm.historyLoaded,
                    version = vm.versionName,
                    now = now,
                    updateReady = update is UpdateState.Available,
                ),
                onAsk = { nav.push(Route.Ask()) },
                onListen = {
                    if (actions.micGranted()) {
                        nav.push(Route.Ask(listen = true))
                    } else {
                        actions.requestMic(true)
                    }
                },
                onSuggestion = { nav.push(Route.Ask(prefill = it.prompt, send = true)) },
                onBadge = { action ->
                    when (action) {
                        BadgeAction.RECONNECT -> actions.reconnect()
                        BadgeAction.RETRY -> actions.retry()
                        BadgeAction.CONNECT -> nav.push(Route.Brains)
                        BadgeAction.DISCONNECT -> actions.disconnect()
                        BadgeAction.NONE -> Unit
                    }
                },
                onStop = VoiceController::stop,
                onOpenTask = { id -> if (running?.id == id) nav.push(Route.Ask()) else nav.push(Route.Task(id)) },
                onHistory = { nav.push(Route.History) },
                onSettings = { nav.push(Route.Settings) },
                onSetup = { nav.push(Route.Permissions) },
            )
        }
        is Route.Ask -> {
            var draft by rememberSaveable { mutableStateOf(if (route.send) "" else route.prefill.orEmpty()) }
            var sent by rememberSaveable { mutableStateOf(false) }
            var brainSheet by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (route.send && !sent && !route.prefill.isNullOrBlank()) {
                    sent = true
                    vm.ask(route.prefill)
                }
                if (route.listen && actions.micGranted() && !VoiceController.isListening()) actions.listen()
            }
            val task = live?.takeIf { it.id == vm.askTaskId } ?: live?.takeIf { it.running }
            val key = vm.activeBrain?.name
            val recap = task?.takeIf { it.state.finished }?.let { RecapBuilder.build(it, UndoLog.offer(it.id)) }
            val recapShots = RecapShots.of(task?.id)
            Box(Modifier.fillMaxSize()) {
                AskScreen(
                    state = AskState(
                        status = askStatus(vm.builtinBrain, session, listening, key, grok),
                        brainLabel = if (vm.builtinBrain) key ?: "On this phone" else grok,
                        brainTone = if (listening || (vm.builtinBrain && key != null)) Tone.Success else Tone.Iris,
                        brainLive = listening || (vm.builtinBrain && key != null),
                        task = task,
                        voice = voice,
                        suggestions = rememberSuggestions(),
                        now = now,
                        autoFocus = !route.listen && !route.send,
                    ),
                    draft = draft,
                    onDraft = { draft = it },
                    onSend = { text ->
                        draft = ""
                        vm.ask(text)
                    },
                    onMic = { if (actions.micGranted()) actions.listen() else actions.requestMic(true) },
                    onStopListening = VoiceController::cancelListening,
                    onStop = VoiceController::stop,
                    onBack = { nav.pop() },
                    onFix = { fix ->
                        when (fix) {
                            AskFix.PAIR -> nav.push(Route.Pair)
                            AskFix.ADD_KEY -> nav.push(Route.Brains)
                            AskFix.USE_GROK -> vm.chooseGrok()
                            AskFix.LISTEN_HELP -> nav.push(Route.ListenHelp)
                        }
                    },
                    onBrain = { brainSheet = true },
                    onSuggestion = { vm.ask(it.prompt) },
                    onOpenTask = { nav.push(Route.Task(it)) },
                    onNewAsk = { vm.askTaskId = null },
                    shotFile = shotFile,
                    recap = recap,
                    recapShots = recapShots,
                    onUndo = { vm.undoLast() },
                )
                PonySheet(brainSheet, { brainSheet = false }, "Who should handle your asks?", subtitle = "Grok Bot needs no key. A brain on this phone uses your API key.") {
                    ListRow(
                        grok,
                        subtitle = when {
                            listening -> "Listening now"
                            session.connection == Connection.Connected -> "Connected"
                            else -> "Not connected"
                        },
                        icon = PonyIcons.Bot,
                        trailing = if (!vm.builtinBrain) Trailing.Badge("In use", Tone.Iris) else Trailing.None,
                        onClick = {
                            vm.chooseGrok()
                            brainSheet = false
                        },
                    )
                    vm.providers.forEach { record ->
                        ListRow(
                            record.name,
                            subtitle = "${record.preset.title} · on this phone",
                            icon = PonyIcons.Key,
                            trailing = if (vm.builtinBrain && record.active) Trailing.Badge("In use", Tone.Iris) else Trailing.None,
                            onClick = {
                                vm.activateBrain(record.id)
                                vm.choosePhone()
                                brainSheet = false
                            },
                        )
                    }
                    PonyButton("Manage brains", { brainSheet = false; nav.push(Route.Brains) }, tone = ButtonTone.Secondary, icon = PonyIcons.Brain)
                }
            }
        }
        Route.History -> HistoryScreen(
            tasks = history,
            loading = !vm.historyLoaded,
            now = now,
            onOpen = { nav.push(Route.Task(it)) },
            onClear = {
                TaskRuntime.tracker.clear()
                toaster.show("History cleared")
            },
            onBack = { nav.pop() },
            onAsk = { nav.push(Route.Ask()) },
        )
        is Route.Task -> {
            val task = live?.takeIf { it.id == route.id } ?: history.firstOrNull { it.id == route.id }
            val recap = task?.takeIf { it.state.finished }?.let { RecapBuilder.build(it, UndoLog.offer(it.id)) }
            TaskDetailScreen(
                task = task,
                now = now,
                shotFile = shotFile,
                onBack = { nav.pop() },
                onAskAgain = { text -> nav.push(Route.Ask(prefill = text, send = true)) },
                onDelete = { id ->
                    TaskRuntime.tracker.delete(id)
                    nav.pop()
                    toaster.show("Task deleted")
                },
                onStop = VoiceController::stop,
                recap = recap,
                recapShots = RecapShots.of(task?.id),
                onUndo = { vm.undoLast() },
            )
        }
        Route.Pair -> PairScreen(
            state = PairState(
                cameraGranted = readiness.camera,
                error = vm.scanError,
                pending = vm.pendingLink != null,
                pairing = session.connection == Connection.Pairing,
                safetyCode = session.safetyCode?.takeIf { session.connection == Connection.Pairing || session.connection == Connection.Connected || session.connection == Connection.Reconnecting },
                connectedName = session.clientName ?: grok.takeIf { session.connection == Connection.Connected },
                grokName = grok,
                relayHost = session.relay?.let { hostOf(it) },
                ownerConfirmed = session.ownerConfirmed,
            ),
            onRequestCamera = actions.requestCamera,
            onPair = actions.pair,
            onUsePending = actions.usePending,
            onTemplate = actions.openTemplate,
            onListenHelp = { nav.push(Route.ListenHelp) },
            onDone = { nav.pop() },
            onConfirm = actions.confirmPair,
            onBack = { nav.pop() },
            scanner = { scanner(actions.pair) },
        )
        Route.ListenHelp -> ListenHelpScreen(
            relay = if (vm.connectionCloud) "" else vm.privateRelay,
            onCopy = { text ->
                actions.copy(text)
                toaster.show("Copied")
            },
            onBack = { nav.pop() },
        )
        Route.Settings -> SettingsScreen(
            state = SettingsState(
                version = vm.versionName,
                versionCode = vm.versionCode,
                brainLabel = if (vm.builtinBrain) vm.activeBrain?.name ?: "On this phone" else grok,
                connectionLabel = if (vm.connectionCloud) "Pony Cloud" else "Private network",
                sessionLength = vm.sessionLength,
                autoReconnect = vm.autoReconnect,
                connected = session.connection == Connection.Connected || session.connection == Connection.Reconnecting,
                assistantName = grok,
                voiceEnabled = vm.voiceEnabled,
                wakeWord = vm.wakeWord,
                spokenReplies = vm.spokenReplies,
                background = vm.backgroundMode,
                mainScreen = vm.mainScreenFallback,
                keepScreenshots = vm.keepScreenshots,
                memoryCount = vm.memories.size,
                scheduleCount = vm.schedules.size,
                routineCount = vm.routines.size,
                theme = vm.themeMode,
                update = update,
                setupDone = setupItems(readiness, samsung).count { it.done },
                setupTotal = setupItems(readiness, samsung).size,
            ),
            onBack = { nav.pop() },
            onBrains = { nav.push(Route.Brains) },
            onConnection = { nav.push(Route.Connection) },
            onSessionLength = vm::updateSessionLength,
            onAutoReconnect = vm::updateAutoReconnect,
            onVoice = { nav.push(Route.Voice) },
            onSpokenReplies = vm::updateSpokenReplies,
            onBackground = vm::updateBackground,
            onMainScreen = vm::updateMainScreenFallback,
            onKeepScreenshots = vm::updateKeepScreenshots,
            onTheme = vm::updateTheme,
            onUpdates = {
                if (update == UpdateState.Idle) Updater.check(context)
                nav.push(Route.Updates)
            },
            onSetup = { nav.push(Route.Permissions) },
            onMemory = { nav.push(Route.Memory) },
            onSchedules = { nav.push(Route.Schedules) },
            onRoutines = { nav.push(Route.Routines) },
            onPrivacy = { nav.push(Route.PrivacyReview) },
            onClearHistory = {
                TaskRuntime.tracker.clear()
                toaster.show("History cleared")
            },
            onAdvanced = { nav.push(Route.Advanced) },
            onDisconnect = actions.disconnect,
            onDisconnectEverything = {
                vm.disconnectEverything()
                toaster.show("Disconnected. Every saved key cleared.")
            },
        )
        Route.Memory -> {
            LaunchedEffect(Unit) { vm.loadMemories() }
            MemoryScreen(
                state = MemoryState(entries = vm.memories, loading = !vm.memoriesLoaded),
                onForget = { key ->
                    vm.forgetMemory(key)
                    toaster.show("Forgot “${key.replace('_', ' ')}”")
                },
                onClearAll = {
                    vm.clearMemory()
                    toaster.show("Memory cleared")
                },
                onBack = { nav.pop() },
            )
        }
        Route.Schedules -> {
            LaunchedEffect(Unit) { vm.loadSchedules() }
            SchedulesScreen(
                state = SchedulesState(
                    tasks = vm.schedules,
                    loading = !vm.schedulesLoaded,
                    now = System.currentTimeMillis(),
                ),
                onToggle = { id, enabled ->
                    vm.toggleSchedule(id, enabled)
                    toaster.show(if (enabled) "Task on" else "Task paused")
                },
                onDelete = { id ->
                    vm.deleteSchedule(id)
                    toaster.show("Task deleted")
                },
                onClearAll = {
                    vm.clearSchedules()
                    toaster.show("Tasks cleared")
                },
                onBack = { nav.pop() },
            )
        }
        Route.Routines -> {
            LaunchedEffect(Unit) { vm.loadRoutines() }
            RoutinesScreen(
                state = RoutinesState(
                    routines = vm.routines,
                    loading = !vm.routinesLoaded,
                ),
                onRun = { id ->
                    val name = vm.routines.firstOrNull { it.id == id }?.name
                    vm.runRoutine(id)
                    toaster.show(if (name != null) "Running “$name”" else "Running routine")
                },
                onRename = { id, name ->
                    vm.renameRoutine(id, name)
                    toaster.show("Renamed to “$name”")
                },
                onDelete = { id ->
                    vm.deleteRoutine(id)
                    toaster.show("Routine deleted")
                },
                onClearAll = {
                    vm.clearRoutines()
                    toaster.show("Routines cleared")
                },
                onBack = { nav.pop() },
            )
        }
        Route.Voice -> {
            var voices by remember { mutableStateOf(emptyList<app.pony.companion.voice.VoiceCatalog.Option>()) }
            var loading by remember { mutableStateOf(true) }
            LaunchedEffect(vm.cloudVoice) {
                loading = true
                SpeechOutput.loadVoices(context) {
                    voices = it
                    loading = false
                }
            }
            VoiceScreen(
                state = VoiceState(
                    voiceEnabled = vm.voiceEnabled,
                    wakeWord = vm.wakeWord,
                    chargingOnly = vm.chargingOnly,
                    micGranted = readiness.microphone,
                    assistantHeld = readiness.assistant,
                    speechRate = vm.speechRate,
                    voices = voices,
                    voicesLoading = loading,
                    selectedVoice = vm.ttsVoice,
                    cloudVoice = vm.cloudVoice,
                    wakeStatus = vm.wakeStatus,
                    fullScreenNeeded = readiness.fullScreenNeeded,
                ),
                onVoiceEnabled = actions.setVoiceEnabled,
                onWakeWord = actions.setWakeWord,
                onChargingOnly = vm::updateChargingOnly,
                onRequestMic = { actions.requestMic(false) },
                onRate = vm::updateSpeechRate,
                onCloudVoice = vm::updateCloudVoice,
                onVoice = vm::updateTtsVoice,
                onPreview = actions.previewVoice,
                onTest = actions.testVoice,
                onAssistant = actions.openAssistantSettings,
                onFullScreen = actions.openFullScreenSettings,
                onBack = { nav.pop() },
            )
        }
        Route.Brains, Route.AddBrain -> BrainsScreen(
            state = BrainsState(
                grokName = grok,
                grokSelected = !vm.builtinBrain,
                grokConnected = session.connection == Connection.Connected,
                grokListening = listening,
                providers = vm.providers,
            ),
            onUseGrok = vm::chooseGrok,
            onUsePhone = { id ->
                id?.let(vm::activateBrain)
                vm.choosePhone()
            },
            onSave = { draft ->
                vm.saveBrain(draft).also { if (it == null) toaster.show("Saved. The key stays on this phone.") }
            },
            onDelete = { id ->
                vm.deleteBrain(id)
                toaster.show("Brain deleted")
            },
            onTest = vm::testBrain,
            onConnect = { preset, key, done ->
                vm.connectBrain(preset, key) { ok, message ->
                    if (ok) toaster.show(message)
                    done(ok, message)
                }
            },
            onGetKey = actions.openUrl,
            onPair = { nav.push(Route.Pair) },
            onDisconnectGrok = {
                actions.disconnect()
                toaster.show("$grok disconnected")
            },
            onTemplate = actions.openTemplate,
            onListenHelp = { nav.push(Route.ListenHelp) },
            onBack = { nav.pop() },
            startEditing = route == Route.AddBrain,
        )
        Route.Connection -> {
            var error by remember { mutableStateOf<String?>(null) }
            ConnectionScreen(
                state = ConnectionState(
                    cloud = vm.connectionCloud,
                    privateRelay = vm.privateRelay,
                    knownRelays = vm.knownRelays.sorted(),
                    connected = session.connection == Connection.Connected,
                    statusLine = session.status,
                    relay = session.relay?.let { if (app.pony.companion.net.CleartextPolicy.isCloud(it)) "Pony Cloud" else it },
                    timeLeft = if (session.endsAt == null && session.connection == Connection.Connected) "Until you disconnect" else remainingText(session.endsAt, now),
                ),
                error = error,
                onCloud = {
                    vm.updateConnectionCloud(true)
                    error = null
                },
                onSavePrivate = { url ->
                    error = vm.savePrivateRelay(url)
                    if (error == null) toaster.show("Private relay saved")
                },
                onDisconnect = actions.disconnect,
                onTailscale = actions.openTailscale,
                onBack = { nav.pop() },
            )
        }
        Route.Advanced -> AdvancedScreen(
            state = AdvancedState(
                clipboard = vm.clipboardPaste,
                keyboardOn = readiness.keyboard,
                shizuku = vm.shizukuEnhanced,
                shizukuGranted = vm.shizukuGranted,
                shizukuInstalled = vm.shizukuInstalled,
                shizukuRunning = vm.shizukuRunning,
                note = vm.note,
            ),
            onClipboard = vm::updateClipboardPaste,
            onKeyboard = actions.openKeyboardSettings,
            onPicker = actions.showKeyboardPicker,
            onShizuku = vm::updateShizuku,
            onSetupShizuku = { nav.push(Route.ShizukuSetup) },
            onRestartShizuku = actions.openShizuku,
            onBack = { nav.pop() },
        )
        Route.ShizukuSetup -> ShizukuSetupScreen(
            state = app.pony.companion.ui.screens.ShizukuSetupState(
                installed = vm.shizukuInstalled,
                devOptions = vm.shizukuDevOptions,
                running = vm.shizukuRunning,
                granted = vm.shizukuGranted,
            ),
            onGetShizuku = actions.getShizuku,
            onBuildNumber = actions.openBuildNumber,
            onWirelessDebugging = actions.openWirelessDebugging,
            onOpenShizuku = actions.openShizuku,
            onGrant = actions.grantShizuku,
            onGranted = { vm.updateShizuku(true) },
            onRefresh = { vm.refreshShizuku() },
            onDone = { nav.pop() },
            onBack = { nav.pop() },
        )
        Route.Updates -> UpdatesScreen(
            state = update,
            version = vm.versionName,
            onCheck = { Updater.check(context) },
            onDownload = {
                val manifest = (update as? UpdateState.Available)?.manifest ?: (update as? UpdateState.NeedsPermission)?.manifest
                manifest?.let { Updater.download(context, it) }
            },
            onAllowInstalls = actions.openInstallPermission,
            onBack = { nav.pop() },
        )
    }
}

private fun hostOf(relay: String): String {
    val trimmed = relay.trim().removePrefix("wss://").removePrefix("ws://").removePrefix("https://").removePrefix("http://")
    return trimmed.substringBefore("/").substringBefore("?").ifBlank { relay }
}
