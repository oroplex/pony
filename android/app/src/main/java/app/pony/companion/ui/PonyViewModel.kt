package app.pony.companion.ui

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import app.pony.companion.BuildConfig
import app.pony.companion.brain.AdapterRequests
import app.pony.companion.brain.BrainLibrary
import app.pony.companion.brain.ChatClient
import app.pony.companion.brain.GrokArrival
import app.pony.companion.brain.GrokBot
import app.pony.companion.brain.KeystoreSecretBox
import app.pony.companion.brain.ProviderDraft
import app.pony.companion.brain.ProviderPreset
import app.pony.companion.brain.ProviderRecord
import app.pony.companion.display.ShizukuBridge
import app.pony.companion.memory.Memory
import app.pony.companion.memory.MemoryEntry
import app.pony.companion.net.CleartextPolicy
import app.pony.companion.routines.Routine
import app.pony.companion.routines.RoutineBook
import app.pony.companion.routines.Routines
import app.pony.companion.schedule.ScheduleAlarms
import app.pony.companion.schedule.ScheduledTask
import app.pony.companion.session.PonySessionService
import app.pony.companion.session.SessionLength
import app.pony.companion.tasks.TaskRuntime
import app.pony.companion.ui.theme.ThemeMode
import app.pony.companion.voice.VoiceController
import app.pony.companion.voice.VoicePrefs
import app.pony.companion.voice.WakeWordService
import java.io.File
import kotlin.concurrent.thread

class PonyViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    val nav = Navigator(if (prefs.getBoolean(KEY_CONSENT, false)) Route.Home else Route.Welcome)
    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE

    var consentChecked by mutableStateOf(false)
    var scanError by mutableStateOf<String?>(null)
    var pendingLink by mutableStateOf<String?>(null)
        private set
    var pendingPayload by mutableStateOf<String?>(null)
        private set
    var captureInFlight by mutableStateOf(false)
        private set
    private var captureStartedAt = 0L
    var clipboardPaste by mutableStateOf(prefs.getBoolean(KEY_CLIPBOARD, false))
        private set
    var themeMode by mutableStateOf(ThemeMode.of(VoicePrefs.themeMode(app)))
        private set
    var voiceEnabled by mutableStateOf(true)
        private set
    var wakeWord by mutableStateOf(false)
        private set
    var chargingOnly by mutableStateOf(true)
        private set
    var builtinBrain by mutableStateOf(false)
        private set
    var speechRate by mutableStateOf(1f)
        private set
    var ttsVoice by mutableStateOf("")
        private set
    var cloudVoice by mutableStateOf(false)
        private set
    var wakeStatus by mutableStateOf("")
    var backgroundMode by mutableStateOf(true)
        private set
    var shizukuEnhanced by mutableStateOf(false)
        private set
    var shizukuGranted by mutableStateOf(false)
    var shizukuInstalled by mutableStateOf(false)
    var shizukuRunning by mutableStateOf(false)
    var shizukuDevOptions by mutableStateOf(false)
    var connectionCloud by mutableStateOf(true)
        private set
    var privateRelay by mutableStateOf("")
        private set
    var knownRelays by mutableStateOf(emptySet<String>())
        private set
    var preferredGrok by mutableStateOf(true)
        private set
    var sessionLength by mutableStateOf(SessionLength.HALF_HOUR)
        private set
    var autoReconnect by mutableStateOf(true)
        private set
    var mainScreenFallback by mutableStateOf(VoicePrefs.FALLBACK_ASK)
        private set
    var keepScreenshots by mutableStateOf(true)
        private set
    var spokenReplies by mutableStateOf(false)
        private set
    var providers by mutableStateOf(listOf<ProviderRecord>())
        private set
    var grokFromTemplate by mutableStateOf(false)
        private set
    var resumable by mutableStateOf(false)
        private set
    var note by mutableStateOf<String?>(null)
    var askTaskId by mutableStateOf<String?>(null)
    var tryTaskId by mutableStateOf<String?>(null)
    var historyLoaded by mutableStateOf(false)
        private set
    var memories by mutableStateOf(listOf<MemoryEntry>())
        private set
    var memoriesLoaded by mutableStateOf(false)
        private set
    var schedules by mutableStateOf(listOf<ScheduledTask>())
        private set
    var schedulesLoaded by mutableStateOf(false)
        private set
    var routines by mutableStateOf(listOf<Routine>())
        private set
    var routinesLoaded by mutableStateOf(false)
        private set
    private var brains: BrainLibrary? = null

    init {
        reload()
    }

    fun hasConsented(): Boolean = prefs.getBoolean(KEY_CONSENT, false)

    fun reload() {
        val app = getApplication<Application>()
        voiceEnabled = VoicePrefs.voiceEnabled(app)
        wakeWord = VoicePrefs.wakeWord(app)
        chargingOnly = VoicePrefs.chargingOnly(app)
        builtinBrain = VoicePrefs.brainMode(app) == VoicePrefs.BUILTIN
        speechRate = VoicePrefs.speechRate(app)
        ttsVoice = VoicePrefs.voiceName(app)
        cloudVoice = VoicePrefs.cloudVoice(app)
        wakeStatus = if (wakeWord) VoicePrefs.status(app) else ""
        backgroundMode = VoicePrefs.backgroundMode(app)
        shizukuEnhanced = VoicePrefs.shizukuEnhanced(app)
        connectionCloud = VoicePrefs.connectionCloud(app)
        privateRelay = VoicePrefs.privateRelay(app)
        knownRelays = VoicePrefs.knownRelays(app)
        preferredGrok = GrokBot.isGrok(VoicePrefs.preferredClient(app))
        sessionLength = VoicePrefs.sessionLength(app)
        autoReconnect = VoicePrefs.autoReconnect(app)
        mainScreenFallback = VoicePrefs.mainScreenFallback(app)
        keepScreenshots = VoicePrefs.keepScreenshots(app)
        spokenReplies = VoicePrefs.spokenReplies(app)
        if (brains == null) {
            brains = runCatching { BrainLibrary(File(app.filesDir, "brains"), KeystoreSecretBox()) }.getOrElse {
                note = it.message ?: "The key store didn't open."
                null
            }
        }
        providers = brains?.list().orEmpty()
        refreshResumable()
        loadMemories()
        loadSchedules()
        loadRoutines()
        thread(name = "pony-history") {
            TaskRuntime.tracker
            main.post { historyLoaded = true }
        }
    }

    /** Reads the remembered facts off disk on a background thread; Keystore work shouldn't block the UI. */
    fun loadMemories() {
        val app = getApplication<Application>()
        thread(name = "pony-memory") {
            val entries = runCatching { Memory.store(app).all() }.getOrDefault(emptyList())
            main.post {
                memories = entries
                memoriesLoaded = true
            }
        }
    }

    fun forgetMemory(key: String) {
        val app = getApplication<Application>()
        runCatching { Memory.store(app).delete(key) }
        loadMemories()
    }

    fun clearMemory() {
        val app = getApplication<Application>()
        runCatching { Memory.store(app).clear() }
        memories = emptyList()
    }

    /** Reads scheduled tasks off disk on a background thread, like memory. */
    fun loadSchedules() {
        val app = getApplication<Application>()
        thread(name = "pony-schedules") {
            val tasks = runCatching { ScheduleAlarms.store(app).all() }.getOrDefault(emptyList())
            main.post {
                schedules = tasks
                schedulesLoaded = true
            }
        }
    }

    fun toggleSchedule(id: String, enabled: Boolean) {
        val app = getApplication<Application>()
        runCatching { ScheduleAlarms.setEnabled(app, id, enabled) }
        schedules = schedules.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    fun deleteSchedule(id: String) {
        val app = getApplication<Application>()
        runCatching { ScheduleAlarms.delete(app, id) }
        schedules = schedules.filterNot { it.id == id }
    }

    fun clearSchedules() {
        val app = getApplication<Application>()
        runCatching { ScheduleAlarms.clearAll(app) }
        schedules = emptyList()
    }

    /** Reads saved routines off disk on a background thread, like memory and schedules. */
    fun loadRoutines() {
        val app = getApplication<Application>()
        thread(name = "pony-routines") {
            val saved = runCatching { RoutineBook.store(app).all() }.getOrDefault(emptyList())
            main.post {
                routines = saved
                routinesLoaded = true
            }
        }
    }

    /** Runs a routine now, the same as saying its name — the brain and every confirm gate still apply. */
    fun runRoutine(id: String) {
        val app = getApplication<Application>()
        val routine = routines.firstOrNull { it.id == id } ?: return
        RoutineBook.markRun(app, id)
        VoiceController.ask(app, routine.text, source = Routines.SOURCE)
        loadRoutines()
    }

    fun renameRoutine(id: String, name: String) {
        val app = getApplication<Application>()
        runCatching { RoutineBook.store(app).rename(id, name) }
        loadRoutines()
    }

    fun deleteRoutine(id: String) {
        val app = getApplication<Application>()
        runCatching { RoutineBook.store(app).delete(id) }
        routines = routines.filterNot { it.id == id }
    }

    fun clearRoutines() {
        val app = getApplication<Application>()
        runCatching { RoutineBook.store(app).clear() }
        routines = emptyList()
    }

    fun refreshResumable() {
        resumable = PonySessionService.resumable(getApplication())
    }

    val activeBrain: ProviderRecord? get() = providers.firstOrNull { it.active }

    val grokName: String get() = if (preferredGrok) GrokBot.NAME else VoicePrefs.preferredClient(getApplication())

    fun agree() {
        prefs.edit().putBoolean(KEY_CONSENT, true).apply()
        nav.advance(Route.Setup)
    }

    fun finishOnboarding() {
        nav.replaceAll(Route.Home)
    }

    fun holdLink(json: String) {
        pendingLink = json
    }

    fun beginCapture(json: String) {
        pendingPayload = json
        captureInFlight = true
        captureStartedAt = System.currentTimeMillis()
        pendingLink = null
    }

    fun finishCapture() {
        captureInFlight = false
        pendingPayload = null
    }

    /**
     * The screen-capture consent prompt can be left without a result — the owner
     * taps away, switches apps, or Android kills the dialog — and then a fresh
     * scan would be ignored as "already in flight". After this long with no
     * answer, treat the in-flight capture as abandoned so pairing works again.
     */
    fun captureStale(): Boolean =
        captureInFlight && System.currentTimeMillis() - captureStartedAt > CAPTURE_STALE_MS

    fun updateTheme(mode: ThemeMode) {
        themeMode = mode
        VoicePrefs.setThemeMode(getApplication(), mode.wire)
    }

    fun updateClipboardPaste(enabled: Boolean) {
        clipboardPaste = enabled
        prefs.edit().putBoolean(KEY_CLIPBOARD, enabled).apply()
    }

    fun updateVoiceEnabled(enabled: Boolean) {
        voiceEnabled = enabled
        VoicePrefs.setVoiceEnabled(getApplication(), enabled)
    }

    fun updateWakeWord(enabled: Boolean) {
        wakeWord = enabled
        val app = getApplication<Application>()
        VoicePrefs.setWakeWord(app, enabled)
        if (!enabled) {
            wakeStatus = ""
            VoicePrefs.setStatus(app, "")
        }
    }

    fun updateChargingOnly(enabled: Boolean) {
        chargingOnly = enabled
        VoicePrefs.setChargingOnly(getApplication(), enabled)
    }

    fun updateSpeechRate(rate: Float) {
        speechRate = rate
        VoicePrefs.setSpeechRate(getApplication(), rate)
    }

    fun updateTtsVoice(name: String) {
        ttsVoice = name
        VoicePrefs.setVoiceName(getApplication(), name)
    }

    fun updateCloudVoice(enabled: Boolean) {
        cloudVoice = enabled
        VoicePrefs.setCloudVoice(getApplication(), enabled)
    }

    fun updateSpokenReplies(enabled: Boolean) {
        spokenReplies = enabled
        VoicePrefs.setSpokenReplies(getApplication(), enabled)
    }

    fun updateBackground(enabled: Boolean) {
        backgroundMode = enabled
        VoicePrefs.setBackgroundMode(getApplication(), enabled)
    }

    fun updateShizuku(enabled: Boolean) {
        shizukuEnhanced = enabled
        VoicePrefs.setShizukuEnhanced(getApplication(), enabled)
    }

    /** Live Shizuku + developer-options state that drives the guided setup and the Advanced card. */
    fun refreshShizuku() {
        val app = getApplication<Application>()
        shizukuInstalled = ShizukuBridge.installed(app)
        shizukuRunning = runCatching { ShizukuBridge.running() }.getOrDefault(false)
        shizukuGranted = runCatching { ShizukuBridge.granted() }.getOrDefault(false)
        shizukuDevOptions = runCatching {
            Settings.Global.getInt(app.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        }.getOrDefault(false)
    }

    fun updateSessionLength(length: SessionLength) {
        sessionLength = length
        VoicePrefs.setSessionLength(getApplication(), length)
    }

    fun updateAutoReconnect(enabled: Boolean) {
        autoReconnect = enabled
        VoicePrefs.setAutoReconnect(getApplication(), enabled)
    }

    fun updateMainScreenFallback(value: String) {
        mainScreenFallback = value
        VoicePrefs.setMainScreenFallback(getApplication(), value)
    }

    fun updateKeepScreenshots(enabled: Boolean) {
        keepScreenshots = enabled
        VoicePrefs.setKeepScreenshots(getApplication(), enabled)
    }

    fun chooseGrok() {
        preferredGrok = true
        builtinBrain = false
        val app = getApplication<Application>()
        VoicePrefs.setPreferredClient(app, GrokBot.NAME)
        VoicePrefs.setBrainMode(app, VoicePrefs.CONNECTED)
    }

    fun choosePhone() {
        builtinBrain = true
        VoicePrefs.setBrainMode(getApplication(), VoicePrefs.BUILTIN)
    }

    fun updateConnectionCloud(cloud: Boolean) {
        connectionCloud = cloud
        VoicePrefs.setConnectionCloud(getApplication(), cloud)
    }

    fun savePrivateRelay(url: String): String? {
        val canonical = CleartextPolicy.canonical(url)
        if (canonical == null || !CleartextPolicy.allows(canonical)) {
            return "Use a Tailscale or home-network address, such as http://100.x.y.z:8787, or an https relay."
        }
        privateRelay = canonical
        val app = getApplication<Application>()
        VoicePrefs.setPrivateRelay(app, canonical)
        VoicePrefs.setConnectionCloud(app, false)
        connectionCloud = false
        knownRelays = VoicePrefs.knownRelays(app)
        return null
    }

    /**
     * Grok Bot template arrival. The relay in the link is remembered next to
     * Pony Cloud; the owner's Connection choice is left alone, so later Pony
     * Cloud links keep working.
     */
    fun applyGrokTemplate(relay: String?) {
        grokFromTemplate = true
        chooseGrok()
        val app = getApplication<Application>()
        if (!GrokArrival.useCloudRelay(relay)) {
            CleartextPolicy.canonical(relay.orEmpty())?.let { VoicePrefs.rememberRelay(app, it) }
            knownRelays = VoicePrefs.knownRelays(app)
        }
        if (!hasConsented()) nav.replaceAll(Route.Welcome) else if (nav.current.onboarding) nav.replaceAll(Route.Home)
    }

    fun saveBrain(draft: ProviderDraft): String? = try {
        val library = brains ?: error("The key store didn't open.")
        library.save(draft)
        providers = library.list()
        null
    } catch (err: Exception) {
        err.message ?: "Couldn't save that brain."
    }

    fun activateBrain(id: String) {
        brains?.activate(id)
        providers = brains?.list().orEmpty()
    }

    fun deleteBrain(id: String) {
        brains?.delete(id)
        providers = brains?.list().orEmpty()
    }

    /**
     * One tap to walk away. Ends any live or resumable session — unpairing Grok
     * Bot or Pony Cloud — wipes every saved brain key from the keystore vault,
     * and stops Pony's background services and their notifications. Pony falls
     * back to Grok Bot, so re-pairing (or re-adding a key) is all it takes to
     * start again.
     */
    fun disconnectEverything() {
        val app = getApplication<Application>()
        runCatching { PonySessionService.stop(app) }
        runCatching { PonySessionService.vaultFor(app).clear() }
        runCatching { WakeWordService.stop(app) }
        if (wakeWord) {
            wakeWord = false
            VoicePrefs.setWakeWord(app, false)
        }
        wakeStatus = ""
        VoicePrefs.setStatus(app, "")
        runCatching { brains?.clearAll() }
        providers = emptyList()
        chooseGrok()
        resumable = false
    }

    /**
     * Connect an API-key brain from the picker in one step: probe the key off
     * the UI thread, and only if it works save it sealed in the keystore vault,
     * make it the active brain, and switch Pony to its own brain. On any failure
     * nothing is saved and [done] carries a sanitized message. An existing brain
     * on the same provider is updated in place, so re-connecting never piles up
     * duplicates.
     */
    fun connectBrain(preset: ProviderPreset, key: String, done: (Boolean, String) -> Unit) {
        val trimmed = key.trim()
        if (trimmed.length < 8) {
            done(false, "Paste an API key of at least 8 characters.")
            return
        }
        thread(name = "pony-connect-brain") {
            val probe = ProviderRecord(
                id = "probe",
                name = preset.title,
                preset = preset,
                model = preset.defaultModel,
                baseUrl = preset.defaultBaseUrl.trimEnd('/'),
                keyLast4 = trimmed.takeLast(4),
                active = false,
            )
            val message = try {
                ChatClient.probe(probe, trimmed)
            } catch (err: Exception) {
                AdapterRequests.sanitize(err.message ?: "Test failed.", trimmed)
            }
            main.post {
                if (message != "Key works.") {
                    done(false, message)
                    return@post
                }
                val library = brains
                if (library == null) {
                    done(false, "The key store didn't open.")
                    return@post
                }
                try {
                    val existing = library.list().firstOrNull { it.preset == preset }
                    val draft = ProviderDraft(
                        id = existing?.id,
                        name = existing?.name?.ifBlank { preset.title } ?: preset.title,
                        preset = preset,
                        model = existing?.model?.ifBlank { preset.defaultModel } ?: preset.defaultModel,
                        baseUrl = existing?.baseUrl?.ifBlank { preset.defaultBaseUrl } ?: preset.defaultBaseUrl,
                        newKey = trimmed,
                    )
                    val saved = library.save(draft)
                    library.activate(saved.id)
                    providers = library.list()
                    choosePhone()
                    done(true, "${preset.title} is ready on this phone.")
                } catch (err: Exception) {
                    done(false, AdapterRequests.sanitize(err.message ?: "Couldn't save that brain.", trimmed))
                }
            }
        }
    }

    fun testBrain(draft: ProviderDraft, pasted: String, done: (Boolean, String) -> Unit) {
        thread(name = "pony-test-brain") {
            val key = pasted.trim().ifEmpty { draft.id?.let { brains?.secret(it) }.orEmpty() }
            val result = if (key.isEmpty()) {
                false to "Paste a key, or save one first."
            } else {
                try {
                    val record = ProviderRecord(
                        id = draft.id ?: "probe",
                        name = draft.name.ifBlank { draft.preset.title },
                        preset = draft.preset,
                        model = draft.model.trim(),
                        baseUrl = draft.baseUrl.trim().trimEnd('/'),
                        keyLast4 = key.takeLast(4),
                        active = false,
                    )
                    val message = ChatClient.probe(record, key)
                    (message == "Key works.") to message
                } catch (err: Exception) {
                    false to AdapterRequests.sanitize(err.message ?: "Test failed.", key)
                }
            }
            main.post { done(result.first, result.second) }
        }
    }

    fun ask(text: String, source: String = "typed"): String? {
        val id = VoiceController.ask(getApplication(), text, source)
        askTaskId = id
        return id
    }

    /** Run the safe one-tap undo the recap offered for the finished task. */
    fun undoLast() {
        VoiceController.undoLast(getApplication())
    }

    fun tryBasics() {
        tryTaskId = VoiceController.ask(getApplication(), TRY_TEXT, "typed", basicsOnly = true, returnToPony = true)
    }

    private companion object {
        const val PREFS = "pony"
        const val KEY_CONSENT = "consent"
        const val KEY_CLIPBOARD = "clipboard_paste"
        const val TRY_TEXT = "Open Calculator and add 2 + 2"
        const val CAPTURE_STALE_MS = 45_000L
    }
}
