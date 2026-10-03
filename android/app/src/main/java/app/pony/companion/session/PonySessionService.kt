package app.pony.companion.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.pony.companion.BuildConfig
import app.pony.companion.MainActivity
import app.pony.companion.R
import app.pony.companion.a11y.PonyAccessibilityService
import app.pony.companion.brain.GrokBot
import app.pony.companion.brain.KeystoreSecretBox
import app.pony.companion.crypto.KeyPairBytes
import app.pony.companion.crypto.SessionCrypto
import app.pony.companion.crypto.SessionKeys
import app.pony.companion.display.ActionResult
import app.pony.companion.display.BackgroundHost
import app.pony.companion.display.DisplayPolicy
import app.pony.companion.display.ScreenGuard
import app.pony.companion.display.ScreenRouter
import app.pony.companion.display.Watch
import app.pony.companion.input.ImeStatus
import app.pony.companion.net.CleartextPolicy
import app.pony.companion.net.RelayClient
import app.pony.companion.proto.AppMessage
import app.pony.companion.proto.PairingPayload
import app.pony.companion.tasks.StepKind
import app.pony.companion.tasks.StopState
import app.pony.companion.tasks.TaskRuntime
import app.pony.companion.tasks.TaskState
import app.pony.companion.voice.LockState
import app.pony.companion.voice.OwnerRequest
import app.pony.companion.voice.SafetyPolicy
import app.pony.companion.voice.StandingHandoff
import app.pony.companion.voice.Verdict
import app.pony.companion.voice.VoiceBus
import app.pony.companion.voice.VoiceController
import app.pony.companion.voice.VoicePrefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The paired session. It survives drops, network switches, process death,
 * and reboots: the relay holds the room, the phone keeps the session sealed
 * with the Keystore, and reconnects back off from 1 s to 30 s.
 */
class PonySessionService : Service(), RelayClient.Listener {
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "pony-session") }
    private val commands = Executors.newSingleThreadExecutor { Thread(it, "pony-commands") }
    private val waits = Executors.newSingleThreadExecutor { Thread(it, "pony-wait") }
    private lateinit var audit: AuditLog
    private lateinit var vault: SessionVault
    @Volatile private var relay: RelayClient? = null
    @Volatile private var keys: SessionKeys? = null
    @Volatile private var phone: KeyPairBytes? = null
    @Volatile private var snapshot: SessionSnapshot? = null
    private var projection: MediaProjection? = null
    private var expiry: ScheduledFuture<*>? = null
    private var retry: ScheduledFuture<*>? = null
    private val backoff = Backoff()
    private val ended = AtomicBoolean(false)
    /** Bumps on every drop and rejoin. An open wait from an older link must not hand out a request. */
    private val linkEpoch = AtomicInteger(0)
    private val handoff = StandingHandoff()
    private var network: ConnectivityManager.NetworkCallback? = null
    @Volatile private var lastNetwork: Network? = null
    @Volatile private var ownerConfirmed = false
    @Volatile private var protocolVersion = 1
    private val sendCounter = DirectionCounter()
    private val recvCounter = DirectionCounter()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audit = AuditLog(this)
        vault = vaultFor(this)
        SessionRepository.update { it.copy(log = audit.read()) }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                worker.execute { end("Disconnected", bye = true) }
                return START_NOT_STICKY
            }
            ACTION_RETRY -> {
                worker.execute { reconnectNow() }
                return START_STICKY
            }
            ACTION_CONFIRM -> {
                worker.execute { confirmOwner() }
                return START_STICKY
            }
            ACTION_ATTACH -> {
                try {
                    attachProjection(intent)
                    startInForeground(true, connectedStatus(snapshot?.clientName))
                } catch (e: Exception) {
                    fail("Could not start screen sharing: ${e.message}")
                }
                return START_STICKY
            }
            ACTION_START -> {
                val payload = intent.getStringExtra(EXTRA_PAIRING) ?: return START_NOT_STICKY
                val hasProjection = intent.getIntExtra(EXTRA_PROJECTION_CODE, 0) != 0
                ended.set(false)
                startInForeground(hasProjection, getString(R.string.notif_title))
                try {
                    attachProjection(intent)
                } catch (e: Exception) {
                    fail("Could not start screen sharing: ${e.message}")
                    return START_NOT_STICKY
                }
                worker.execute { begin(payload) }
            }
            ACTION_RESUME, null -> {
                ended.set(false)
                startInForeground(false, "Reconnecting to your assistant…")
                worker.execute { resume() }
            }
        }
        return START_STICKY
    }

    private fun begin(raw: String) {
        val pairing = try {
            PairingPayload.parse(raw)
        } catch (e: Exception) {
            fail("That pairing code isn't valid: ${e.message}")
            return
        }
        relay?.close()
        relay = null
        val pair = SessionCrypto.generateKeyPair()
        val derived = SessionCrypto.derive(pair.privateKey, SessionCrypto.b64urlDecode(pairing.pk), pairing.token, "phone")
        val now = System.currentTimeMillis()
        val length = VoicePrefs.sessionLength(this)
        val next = SessionSnapshot(
            relay = pairing.relay,
            token = pairing.token,
            botPk = pairing.pk,
            phonePrivateKey = SessionCrypto.b64urlEncode(pair.privateKey),
            phonePublicKey = SessionCrypto.b64urlEncode(pair.publicKey),
            clientName = null,
            startedAt = now,
            endsAt = length.millis?.let { now + it },
            safetyCode = SessionCrypto.formatSafety(derived.safetyCode),
            ownerConfirmed = false,
            protocolVersion = pairing.v,
        )
        ownerConfirmed = false
        protocolVersion = pairing.v
        phone = pair
        keys = derived
        snapshot = next
        vault.save(next)
        CleartextPolicy.canonical(pairing.relay)?.let { VoicePrefs.rememberRelay(this, it) }
        VoiceController.onSessionStarted()
        handoff.finished(null)
        backoff.reset()
        SessionRepository.update {
            it.copy(
                connection = Connection.Pairing,
                status = waitingStatus(),
                safetyCode = next.safetyCode,
                startedAt = next.startedAt,
                endsAt = next.endsAt,
                relay = pairing.relay,
                clientName = null,
                peerAway = false,
                lastError = null,
                ownerConfirmed = false,
                resumable = true,
                endedReason = null,
                reconnectAttempt = 0,
                nextRetryAt = null,
            )
        }
        connect()
        scheduleExpiry()
        watchNetwork()
        append("session", "paired through ${relayName(pairing.relay)}", true)
    }

    private fun resume() {
        val saved = vault.load()
        if (saved == null || !saved.resumable(System.currentTimeMillis())) {
            vault.clear()
            end(if (saved == null) "Not connected" else "Session time limit reached", bye = false)
            return
        }
        val pair = KeyPairBytes(SessionCrypto.b64urlDecode(saved.phonePrivateKey), SessionCrypto.b64urlDecode(saved.phonePublicKey))
        phone = pair
        keys = SessionCrypto.derive(pair.privateKey, SessionCrypto.b64urlDecode(saved.botPk), saved.token, "phone")
        snapshot = saved
        ownerConfirmed = saved.ownerConfirmed
        protocolVersion = saved.protocolVersion
        sendCounter.restore(saved.sendSeq, 0)
        recvCounter.restore(0, saved.recvSeq)
        SessionRepository.update {
            it.copy(
                connection = Connection.Reconnecting,
                status = "Reconnecting to ${saved.clientName ?: preferredName()}…",
                safetyCode = saved.safetyCode,
                startedAt = saved.startedAt,
                endsAt = saved.endsAt,
                relay = saved.relay,
                clientName = saved.clientName,
                resumable = true,
                endedReason = null,
                ownerConfirmed = saved.ownerConfirmed,
            )
        }
        connect()
        scheduleExpiry()
        watchNetwork()
        append("session", "resuming", true)
    }

    private fun connect() {
        val snap = snapshot ?: return
        if (ended.get()) return
        relay?.close()
        val client = RelayClient(snap.relay, snap.token, "phone", this)
        relay = client
        client.connect()
    }

    private fun reconnectNow() {
        if (ended.get()) {
            ended.set(false)
            resume()
            return
        }
        retry?.cancel(false)
        backoff.reset()
        connect()
    }

    private fun scheduleReconnect(why: String) {
        if (ended.get()) return
        relay?.close()
        relay = null
        VoiceBus.inbox.forgetListener()
        if (!VoicePrefs.autoReconnect(this)) {
            SessionRepository.update {
                it.copy(connection = Connection.Error, status = "Connection lost", lastError = why, resumable = true, peerAway = false)
            }
            updateNotification("Connection lost. Open Pony to reconnect.")
            return
        }
        val delay = backoff.next()
        SessionRepository.update {
            it.copy(
                connection = Connection.Reconnecting,
                status = "Reconnecting…",
                lastError = why,
                reconnectAttempt = backoff.attempts,
                nextRetryAt = System.currentTimeMillis() + delay,
                peerAway = false,
            )
        }
        updateNotification("Reconnecting to ${snapshot?.clientName ?: preferredName()}…")
        retry?.cancel(false)
        retry = worker.schedule({ connect() }, delay, TimeUnit.MILLISECONDS)
    }

    private fun watchNetwork() {
        if (network != null) return
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(next: Network) {
                val previous = lastNetwork
                lastNetwork = next
                val state = SessionRepository.snapshot().connection
                val switched = previous != null && previous != next
                if (state == Connection.Reconnecting || state == Connection.Error || (switched && state == Connection.Connected)) {
                    worker.execute { reconnectNow() }
                }
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }.onSuccess { network = callback }
    }

    private fun attachProjection(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_PROJECTION_CODE, 0)
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
        }
        if (resultCode != 0 && data != null) {
            val mgr = getSystemService(MediaProjectionManager::class.java)
            val captured = mgr.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("media projection was not granted")
            captured.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        SessionRepository.setProjectionGranted(false)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            projection = captured
            SessionRepository.setProjectionGranted(true)
        }
    }

    override fun onRelayReady(resumed: Boolean) {
        linkEpoch.incrementAndGet()
        backoff.reset()
        retry?.cancel(false)
        val name = snapshot?.clientName
        SessionRepository.update {
            it.copy(
                connection = Connection.Connected,
                status = connectedStatus(it.clientName ?: name),
                peerAway = false,
                reconnectAttempt = 0,
                nextRetryAt = null,
                lastError = null,
            )
        }
        updateNotification(connectedStatus(name))
        if (resumed) append("session", "reconnected", true)
        if (ownerConfirmed) sendEvent("confirmed", JSONObject().put("confirmed", true).put("v", protocolVersion))
    }

    override fun onClientName(name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        snapshot = snapshot?.copy(clientName = clean)?.also { vault.save(it) }
        SessionRepository.update {
            it.copy(clientName = clean, status = connectedStatus(clean), connection = Connection.Connected)
        }
        updateNotification(connectedStatus(clean))
    }

    override fun onPlainHandshakeNeeded() {
        phone?.let { relay?.sendHandshake(it.publicKey, protocolVersion) }
    }

    private fun confirmOwner() {
        if (ended.get()) return
        ownerConfirmed = true
        snapshot = snapshot?.copy(ownerConfirmed = true)?.also { vault.save(it) }
        SessionRepository.update { it.copy(ownerConfirmed = true) }
        sendEvent("confirmed", JSONObject().put("confirmed", true).put("v", protocolVersion))
        append("session", "owner confirmed the safety code", true)
    }

    override fun onPeerAway() {
        linkEpoch.incrementAndGet()
        VoiceBus.inbox.forgetListener()
        val name = SessionRepository.snapshot().clientName ?: preferredName()
        SessionRepository.update { it.copy(peerAway = true, status = "$name stepped away. Pony is holding the session.") }
        updateNotification("$name stepped away. Waiting for it to come back.")
    }

    override fun onFrame(data: String) {
        val sessionKeys = keys ?: return
        if (data.startsWith("{")) {
            // Plaintext intro / handshake from the relay is unauthenticated. Ignore it.
            return
        }
        val decoded = try {
            SessionCrypto.decryptFrame(sessionKeys.recv, SessionCrypto.b64urlDecode(data), protocolVersion)
        } catch (e: Exception) {
            append("decrypt", e.message ?: "bad frame", false)
            return
        }
        if (protocolVersion >= 2 && !recvCounter.accept(decoded.seq)) {
            append("decrypt", "replayed", false)
            return
        }
        persistCounters()
        val obj = try {
            JSONObject(String(decoded.plaintext))
        } catch (e: Exception) {
            append("decrypt", e.message ?: "bad frame", false)
            return
        }
        if (obj.optString("type") == "intro") {
            onClientName(obj.optString("client"))
            return
        }
        val plain = try {
            AppMessage.fromJson(obj)
        } catch (e: Exception) {
            append("decrypt", e.message ?: "bad frame", false)
            return
        }
        if (plain.kind == "evt" && plain.op == "cancel") {
            val ref = plain.result?.optString("ref") ?: plain.params?.optString("ref")
            if (!ref.isNullOrBlank()) {
                ActionGate.cancel(ref)
                VoiceController.cancelPrompt()
            }
            return
        }
        if (plain.kind != "req" || plain.op == null) return
        val receivedAt = System.currentTimeMillis()
        val lane = if (plain.op == "wait_for_request") waits else commands
        lane.execute {
            val ticket = ActionGate.ticket(plain.id, plain.op, plain.params, receivedAt)
            val result = if (ticket.clockSkew) {
                append(plain.op ?: "action", "clock_skew", false)
                Cmd(false, ActionExpiry.CLOCK_SKEW, JSONObject().put("reason", ActionExpiry.CLOCK_SKEW))
            } else if (ActionGate.alreadyRan(plain.id)) {
                append(plain.op ?: "action", "replayed", false)
                Cmd(false, ActionExpiry.REPLAYED, JSONObject().put("reason", ActionExpiry.REPLAYED).put("id", plain.id))
            } else if (ActionGate.abandoned(ticket)) {
                expiredCmd(ticket)
            } else {
                try {
                    ActionGate.claim(plain.id)
                    handle(plain, ticket)
                } finally {
                    ActionGate.forget(plain.id)
                }
            }
            val sent = send(AppMessage(id = plain.id, kind = "res", ok = result.ok, error = result.error, result = result.result))
            val standing = plain.params?.optBoolean("listen", false) == true
            if (!sent && !standing) result.handedOut?.let { VoiceBus.inbox.putBack(it) }
        }
    }

    private fun expiredCmd(ticket: ActionTicket): Cmd {
        append(ticket.op ?: "action", "expired", false)
        return Cmd(
            false,
            ActionExpiry.EXPIRED,
            JSONObject().put("reason", ActionExpiry.EXPIRED).put("id", ticket.id),
        )
    }

    private fun confirmIfNeeded(
        op: String,
        target: app.pony.companion.voice.TapTarget,
        ticket: ActionTicket,
        watch: Watch,
        key: String? = null,
    ): Cmd? {
        val verdict = SafetyPolicy.forAction(op, target, key)
        if (verdict is Verdict.Block) {
            append(op, verdict.reason, false)
            return Cmd(false, verdict.reason)
        }
        if (verdict !is Verdict.Confirm) return null
        watch.deferred(AWAITING_OWNER, 0)
        val answer = VoiceController.confirmOutcome(this, verdict.prompt, cancelled = { watch.cancelled() })
        if (answer.expired) return expiredCmd(ticket)
        if (!answer.accepted) {
            append(op, "not confirmed", false)
            VoiceController.onRemoteStep(this, StepKind.Confirm, "You declined: ${verdict.prompt}", false)
            return Cmd(false, "not_confirmed", JSONObject().put("reason", verdict.reason))
        }
        return null
    }

    private fun handle(msg: AppMessage): Cmd = handle(msg, ActionGate.ticket(msg.id, msg.op, msg.params))

    private fun handle(msg: AppMessage, ticket: ActionTicket): Cmd {
        val connection = SessionRepository.snapshot().connection
        if (connection != Connection.Connected && connection != Connection.Pairing && connection != Connection.Reconnecting) {
            return Cmd(false, "disconnected")
        }
        val params = msg.params ?: JSONObject()
        val op = msg.op
        val screenOp = op in SCREEN_OPS
        if (op in GATED_OPS && !ownerConfirmed) {
            return Cmd(false, "not_confirmed", JSONObject().put("reason", "safety_code"))
        }
        if (screenOp && StopState.gate.isStopped()) return Cmd(false, "stopped")
        if (screenOp && LockState.isLocked(this)) {
            VoiceController.promptUnlock(this)
            return Cmd(false, "locked")
        }
        if (screenOp && op != "screenshot" && PonyAccessibilityService.instance == null) return Cmd(false, "accessibility_off")
        if (ActionGate.abandoned(ticket)) return expiredCmd(ticket)
        if (op in ACTING_OPS) PonyAccessibilityService.instance?.holdKeyboard()
        val watch = remoteWatch(msg.id, ticket)
        return try {
            when (op) {
                "ping" -> Cmd(true, result = JSONObject().put("pong", true)).also { append("ping", "ok", true) }
                "info" -> Cmd(true, result = info())
                "disconnect" -> {
                    append("disconnect", "requested by assistant", true)
                    worker.execute { end("Your assistant ended the session", bye = true, why = "assistant") }
                    Cmd(true, result = JSONObject().put("disconnected", true))
                }
                "ui_tree" -> {
                    val acted = ScreenRouter.tree(this, params)
                    val tree = acted.fields["tree"] as? String ?: ""
                    append("ui_tree", "${tree.lineSequence().count()} nodes", acted.ok)
                    VoiceController.onRemoteStep(this, StepKind.Read, "Read the screen", acted.ok)
                    cmdOf(acted)
                }
                "wait_idle" -> {
                    val acted = ScreenRouter.waitIdle(this, params.optLong("timeoutMs", 4_000), params)
                    val settled = acted.fields["settled"] == true
                    append("wait_idle", "${if (settled) "settled" else "busy"} ${acted.fields["waitedMs"]}ms", acted.ok)
                    VoiceController.onRemoteStep(
                        this,
                        StepKind.Read,
                        if (settled) "Waited for the screen to settle" else "Screen still busy",
                        acted.ok,
                    )
                    cmdOf(acted)
                }
                "screenshot" -> {
                    val acted = ScreenRouter.shot(this, params)
                    val jpeg = acted.fields["jpeg"] as? ByteArray
                    if (!acted.ok || jpeg == null) {
                        append("screenshot", acted.error ?: "failed", false)
                        VoiceController.onRemoteStep(this, StepKind.Look, "Couldn't capture the screen", false)
                        return cmdOf(acted)
                    }
                    append("screenshot", "${jpeg.size} bytes", true)
                    VoiceController.onRemoteStep(this, StepKind.Look, "Looked at the screen", true, jpeg)
                    val body = acted.json()
                        .put("jpeg_b64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                        .put("source", "accessibility")
                    Cmd(true, result = body)
                }
                "tap" -> {
                    val x = params.getDouble("x")
                    val y = params.getDouble("y")
                    val target = ScreenRouter.tapTarget(this, x, y, params)
                    confirmIfNeeded("tap", target, ticket, watch)?.let { return it }
                    val acted = ScreenRouter.tap(this, x, y, params, watch)
                    append("tap", "${acted.fields["x"]},${acted.fields["y"]} ${acted.error ?: ""}".trim(), acted.ok)
                    val label = if (target.isPassword) "the password field" else target.label.take(60)
                    VoiceController.onRemoteStep(
                        this,
                        StepKind.Tap,
                        stepLabel(if (label.isNotBlank()) "Tapped “$label”" else "Tapped the screen", acted),
                        acted.ok,
                    )
                    finishDeferral(acted)
                    cmdOf(acted)
                }
                "swipe" -> {
                    val target = ScreenRouter.tapTarget(this, params.getDouble("x1"), params.getDouble("y1"), params)
                    confirmIfNeeded("swipe", target, ticket, watch)?.let { return it }
                    val acted = ScreenRouter.swipe(
                        this,
                        params.getDouble("x1"),
                        params.getDouble("y1"),
                        params.getDouble("x2"),
                        params.getDouble("y2"),
                        params.optLong("durationMs", 250),
                        params,
                        watch,
                    )
                    append("swipe", acted.target.name, acted.ok)
                    VoiceController.onRemoteStep(this, StepKind.Swipe, stepLabel("Swiped", acted), acted.ok)
                    finishDeferral(acted)
                    cmdOf(acted)
                }
                "long_press" -> {
                    val x = params.getDouble("x")
                    val y = params.getDouble("y")
                    val target = ScreenRouter.tapTarget(this, x, y, params)
                    confirmIfNeeded("long_press", target, ticket, watch)?.let { return it }
                    val acted = ScreenRouter.longPress(this, x, y, params.optLong("durationMs", 600), params, watch)
                    append("long_press", "${acted.fields["x"]},${acted.fields["y"]} ${acted.error ?: ""}".trim(), acted.ok)
                    val label = if (target.isPassword) "" else target.label.take(60)
                    VoiceController.onRemoteStep(
                        this,
                        StepKind.Tap,
                        stepLabel(if (label.isNotBlank()) "Held “$label”" else "Pressed and held", acted),
                        acted.ok,
                    )
                    finishDeferral(acted)
                    cmdOf(acted)
                }
                "drag" -> {
                    val target = ScreenRouter.tapTarget(this, params.getDouble("x1"), params.getDouble("y1"), params)
                    confirmIfNeeded("drag", target, ticket, watch)?.let { return it }
                    val acted = ScreenRouter.drag(
                        this,
                        params.getDouble("x1"),
                        params.getDouble("y1"),
                        params.getDouble("x2"),
                        params.getDouble("y2"),
                        params.optLong("durationMs", 600),
                        params,
                        watch,
                    )
                    append("drag", acted.target.name, acted.ok)
                    VoiceController.onRemoteStep(this, StepKind.Swipe, stepLabel("Dragged", acted), acted.ok)
                    finishDeferral(acted)
                    cmdOf(acted)
                }
                "pinch" -> {
                    val from = params.getDouble("fromDistance")
                    val to = params.getDouble("toDistance")
                    val target = ScreenRouter.tapTarget(this, params.getDouble("x"), params.getDouble("y"), params)
                    confirmIfNeeded("pinch", target, ticket, watch)?.let { return it }
                    val acted = ScreenRouter.pinch(
                        this,
                        params.getDouble("x"),
                        params.getDouble("y"),
                        from,
                        to,
                        params.optLong("durationMs", 300),
                        params,
                        watch,
                    )
                    append("pinch", acted.target.name, acted.ok)
                    val verb = if (to >= from) "Pinched to zoom in" else "Pinched to zoom out"
                    VoiceController.onRemoteStep(this, StepKind.Swipe, stepLabel(verb, acted), acted.ok)
                    finishDeferral(acted)
                    cmdOf(acted)
                }
                "type" -> {
                    val text = params.optString("text")
                    val mode = app.pony.companion.a11y.TextEntry.parseMode(
                        params.optString("mode").ifBlank { null },
                        params.optBoolean("append", false),
                    )
                    val target = ScreenRouter.tapTarget(this, 0.0, 0.0, params).let {
                        if (it.packageName.isNullOrBlank()) {
                            val pkg = PonyAccessibilityService.instance?.foregroundPackage()
                            it.copy(packageName = pkg, appLabel = pkg?.let { name -> BackgroundHost.appLabel(this, name) })
                        } else it
                    }
                    confirmIfNeeded("type", target, ticket, watch)?.let { return it }
                    if (ActionGate.abandoned(ticket)) return expiredCmd(ticket)
                    val acted = ScreenRouter.type(this, text, mode, params, watch)
                    if (acted.ok) {
                        append("type", "${acted.fields["length"]} chars via ${acted.fields["method"]}", true)
                    } else {
                        append("type", acted.error ?: "failed", false)
                    }
                    VoiceController.onRemoteStep(this, StepKind.Type, stepLabel("Typed ${text.length} characters", acted), acted.ok)
                    finishDeferral(acted)
                    cmdOf(acted)
                }
                "press" -> {
                    val key = params.optString("key")
                    val target = ScreenRouter.tapTarget(this, 0.0, 0.0, params).let {
                        if (it.packageName.isNullOrBlank()) {
                            val pkg = PonyAccessibilityService.instance?.foregroundPackage()
                            it.copy(packageName = pkg, appLabel = pkg?.let { name -> BackgroundHost.appLabel(this, name) })
                        } else it
                    }
                    confirmIfNeeded("press", target, ticket, watch, key)?.let { return it }
                    val acted = ScreenRouter.press(this, key, params, watch)
                    append("press", key, acted.ok)
                    VoiceController.onRemoteStep(this, StepKind.Key, stepLabel("Pressed ${key.replaceFirstChar { it.uppercase() }}", acted), acted.ok)
                    finishDeferral(acted)
                    cmdOf(acted)
                }
                "open_app" -> {
                    val pkg = params.optString("packageName")
                    val label = BackgroundHost.appLabel(this, pkg)
                    confirmIfNeeded("open_app", app.pony.companion.voice.TapTarget(label, packageName = pkg, appLabel = label), ticket, watch)?.let { return it }
                    val consent = { prompt: String ->
                        watch.deferred(AWAITING_OWNER, 0)
                        val answer = VoiceController.confirmOutcome(this, prompt, cancelled = { watch.cancelled() })
                        !answer.expired && answer.accepted
                    }
                    val acted = ScreenRouter.open(this, pkg, params, consent = consent, watch = watch)
                    append("open_app", "$pkg ${acted.target.name}", acted.ok)
                    VoiceController.onRemoteStep(this, StepKind.Open, stepLabel("Opened $label", acted), acted.ok)
                    finishDeferral(acted)
                    cmdOf(acted)
                }
                "open_settings" -> {
                    val name = params.optString("name")
                    val pkg = params.optString("packageName").takeIf { it.isNotBlank() }
                    val consent = { prompt: String ->
                        watch.deferred(AWAITING_OWNER, 0)
                        val answer = VoiceController.confirmOutcome(this, prompt, cancelled = { watch.cancelled() })
                        !answer.expired && answer.accepted
                    }
                    val acted = ScreenRouter.openSettings(this, name, pkg, params, consent = consent, watch = watch)
                    append("open_settings", "$name ${acted.target.name}", acted.ok)
                    val label = "${name.replace('_', ' ')} settings"
                    VoiceController.onRemoteStep(this, StepKind.Open, stepLabel("Opened $label", acted), acted.ok)
                    finishDeferral(acted)
                    cmdOf(acted)
                }
                "wait_for_request" -> {
                    val standing = params.optBoolean("listen", false)
                    if (!standing) VoiceController.onAssistantWaiting()
                    val timeoutMs = params.optLong("timeoutMs", 25_000).coerceIn(1_000, 55_000)
                    if (standing) {
                        val ack = params.optString("ack").takeIf { it.isNotBlank() }
                        val again = handoff.redeliver(ack) { id -> TaskRuntime.tracker.find(id)?.state?.finished != true }
                        if (again != null) {
                            append("wait_for_request", "sent again after a lost reply", true)
                            return requestCmd(again)
                        }
                    }
                    val epoch = linkEpoch.get()
                    val beat = beginWaitHeartbeat(msg.id, timeoutMs, epoch)
                    val request = try {
                        VoiceBus.awaitNext(timeoutMs) { ended.get() || linkEpoch.get() != epoch }
                    } finally {
                        beat.cancel(false)
                    }
                    when {
                        request == null -> Cmd(true, result = JSONObject().put("empty", true))
                        linkEpoch.get() != epoch && !standing -> {
                            VoiceBus.inbox.putBack(request)
                            Cmd(true, result = JSONObject().put("empty", true))
                        }
                        else -> {
                            if (standing) handoff.handedOut(request)
                            append("wait_for_request", "${request.text.length} chars", true)
                            requestCmd(request)
                        }
                    }
                }
                "speak" -> {
                    val text = params.optString("text")
                    val ok = VoiceController.speakNow(this, text)
                    append("speak", "${text.length} chars", ok)
                    Cmd(ok, if (ok) null else "speak_failed", JSONObject().put("spoken", ok))
                }
                "ask_user" -> {
                    val question = params.optString("text")
                    watch.deferred(AWAITING_OWNER, 0)
                    val answer = VoiceController.askOutcome(this, question, cancelled = { watch.cancelled() })
                    when {
                        answer.expired -> expiredCmd(ticket)
                        answer.stopped || StopState.gate.isStopped() -> {
                            append("ask_user", "stopped", false)
                            Cmd(false, "stopped")
                        }
                        else -> {
                            append("ask_user", "${question.length} chars", true)
                            Cmd(true, result = JSONObject().put("text", answer.text))
                        }
                    }
                }
                "confirm" -> {
                    val prompt = params.optString("text")
                    watch.deferred(AWAITING_OWNER, 0)
                    val answer = VoiceController.confirmOutcome(this, prompt, cancelled = { watch.cancelled() })
                    when {
                        answer.expired -> expiredCmd(ticket)
                        answer.stopped || StopState.gate.isStopped() -> {
                            append("confirm", "${prompt.take(80)} → stopped", false)
                            Cmd(false, "stopped", JSONObject().put("accepted", false))
                        }
                        else -> {
                            append("confirm", "${prompt.take(80)} → ${if (answer.accepted) "yes" else "no"}", answer.accepted)
                            Cmd(true, result = JSONObject().put("accepted", answer.accepted))
                        }
                    }
                }
                "done" -> {
                    val text = params.optString("text")
                    val ok = if (params.has("ok")) params.optBoolean("ok", true) else true
                    val ref = params.optString("ref").takeIf { it.isNotBlank() }
                    val finished = VoiceController.onAssistantDone(this, text, ok, ref)
                    handoff.finished(ref)
                    append("done", "${text.length} chars", ok)
                    Cmd(true, result = JSONObject().put("done", finished))
                }
                else -> Cmd(false, "unknown_op:$op").also { append(op ?: "unknown", "rejected", false) }
            }
        } catch (e: Exception) {
            append(op ?: "error", e.message ?: "exception", false)
            Cmd(false, e.message ?: "error")
        }
    }

    /** Calls, pop-ups and Stop reach a waiting command through this. The bot hears about deferrals at once. */
    private fun remoteWatch(requestId: String, ticket: ActionTicket) = object : Watch {
        override fun deferred(reason: String, waitedMs: Long) {
            sendEvent(
                "progress",
                JSONObject()
                    .put("ref", requestId)
                    .put("state", "deferred")
                    .put("reason", reason)
                    .put("waitedMs", waitedMs)
                    .put("limitMs", limitFor(reason)),
            )
            if (reason != AWAITING_OWNER) TaskRuntime.tracker.setState(TaskState.Waiting, VoiceController.waitingLine(reason))
        }

        override fun cancelled(): Boolean =
            StopState.gate.isStopped() || ended.get() || ActionGate.abandoned(ticket)
    }

    /**
     * While a command long-polls for the owner's next request, send a light
     * heartbeat so the client can tell a quiet owner from a dropped link and
     * doesn't give up on the open request mid-wait. It rides the same progress
     * channel the client already extends a request's deadline from.
     */
    private fun beginWaitHeartbeat(requestId: String, limitMs: Long, epoch: Int): ScheduledFuture<*> {
        val start = System.currentTimeMillis()
        return worker.scheduleAtFixedRate(
            {
                if (ended.get() || linkEpoch.get() != epoch) return@scheduleAtFixedRate
                val waited = (System.currentTimeMillis() - start).coerceAtMost(limitMs)
                sendEvent(
                    "progress",
                    JSONObject()
                        .put("ref", requestId)
                        .put("state", "waiting")
                        .put("reason", "awaiting_request")
                        .put("waitedMs", waited)
                        .put("limitMs", limitMs),
                )
            },
            WAIT_HEARTBEAT_MS,
            WAIT_HEARTBEAT_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun limitFor(reason: String): Long = when (reason) {
        DisplayPolicy.CALL_UI_FOREGROUND -> ScreenGuard.callLimitMs
        AWAITING_OWNER -> ActionExpiry.OWNER_PROMPT_MS
        else -> ScreenGuard.COVER_WAIT_MS
    }

    private fun requestCmd(request: OwnerRequest): Cmd = Cmd(
        true,
        result = JSONObject()
            .put("requestId", request.id)
            .put("text", request.text)
            .put("source", request.source),
        handedOut = request,
    )

    /** The session clock pauses while a command waits for a call screen. */
    private fun finishDeferral(acted: ActionResult) {
        val waited = (acted.fields["deferredMs"] as? Long) ?: return
        if (waited <= 0) return
        val current = snapshot ?: return
        val endsAt = current.endsAt ?: return
        snapshot = current.copy(endsAt = endsAt + waited).also { vault.save(it) }
        SessionRepository.update { it.copy(endsAt = snapshot?.endsAt) }
        scheduleExpiry()
    }

    private fun stepLabel(label: String, acted: ActionResult): String = if (acted.ok) {
        if (acted.target.name != "background" && acted.target.warn != null) "$label (${acted.target.name})" else label
    } else {
        "$label — ${friendly(acted.error)}"
    }

    private fun friendly(error: String?): String = when (error) {
        DisplayPolicy.COVERED_BY_POPUP -> "a pop-up was in the way"
        DisplayPolicy.CALL_UI_TIMEOUT -> "the call screen stayed open"
        DisplayPolicy.CALL_UI_FOREGROUND -> "that's the call screen"
        DisplayPolicy.BACKGROUND_REFUSED -> "kept off your screen"
        "password_field" -> "password fields are off limits"
        "stopped" -> "stopped"
        ActionExpiry.EXPIRED -> "that action expired before it ran"
        ActionExpiry.SCREEN_CHANGED -> "the screen changed"
        null -> "failed"
        else -> error.replace('_', ' ')
    }

    private fun info(): JSONObject {
        val ime = ImeStatus.snapshot(this)
        val foreground = PonyAccessibilityService.instance?.foregroundPackage()
        return JSONObject()
            .put("app", "Pony Companion")
            .put("version", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("features", JSONArray(listOf("resume", "tasks", "done", "deferral", "cover_check", "listen", "ack", "action_ttl")))
            .put("sessionEndsAt", snapshot?.endsAt ?: JSONObject.NULL)
            .put("now", System.currentTimeMillis())
            .put("background", VoicePrefs.backgroundMode(this))
            .put("foreground", foreground ?: JSONObject.NULL)
            .put(
                "ime",
                JSONObject()
                    .put("current", ime.currentId ?: JSONObject.NULL)
                    .put("ponyEnabled", ime.ponyEnabled)
                    .put("ponySelected", ime.ponySelected)
                    .put("ponyActive", ime.ponyActive)
                    .put("ponyUsable", ime.ponyUsable),
            )
    }

    private fun cmdOf(acted: ActionResult): Cmd {
        val body = acted.json()
        (acted.fields["coveredBy"] as? String)?.let { body.put("coveredBy", it) }
        return Cmd(acted.ok, acted.error, body)
    }

    private fun send(message: AppMessage): Boolean {
        val sessionKeys = keys ?: return false
        val seq = if (protocolVersion >= 2) sendCounter.nextSend() else null
        persistCounters()
        return runCatching { relay?.sendMessage(sessionKeys.send, message, seq) == true }.getOrDefault(false)
    }

    private fun persistCounters() {
        val current = snapshot ?: return
        val next = current.copy(sendSeq = sendCounter.lastSent(), recvSeq = recvCounter.lastReceived())
        snapshot = next
        vault.save(next)
    }

    private fun sendEvent(op: String, result: JSONObject) {
        send(AppMessage(id = UUID.randomUUID().toString(), kind = "evt", op = op, result = result))
    }

    override fun onClosed(reason: String, fatal: Boolean) {
        if (ended.get()) return
        linkEpoch.incrementAndGet()
        if (fatal) {
            worker.execute { end(if (reason == "peer_left") "Your assistant disconnected" else "The session ended ($reason)", bye = false) }
        } else {
            worker.execute { scheduleReconnect(reason) }
        }
    }

    override fun onError(message: String, fatal: Boolean) {
        if (ended.get()) return
        if (message != "hello_required" && message != "bad_json") linkEpoch.incrementAndGet()
        when {
            message == "peer_missing" || message == "hello_required" || message == "bad_json" -> Unit
            fatal -> worker.execute { end(endReason(message), bye = false) }
            else -> worker.execute { scheduleReconnect(message) }
        }
    }

    private fun endReason(code: String): String = when (code) {
        "unknown_token", "room_closed" -> "The relay closed this session. Pair again to reconnect."
        "expired_token" -> "That pairing code expired. Ask your assistant for a new one."
        else -> code
    }

    private fun fail(message: String) {
        append("error", message, false)
        SessionRepository.update { it.copy(connection = Connection.Error, lastError = message, status = message) }
        worker.execute { end(message, bye = false) }
    }

    /** [why] tells the assistant who ended it: `owner`, `assistant`, or `time_limit`. */
    private fun end(status: String, bye: Boolean, why: String = "owner") {
        if (!ended.compareAndSet(false, true)) return
        if (bye) {
            sendEvent("ended", JSONObject().put("reason", why))
            runCatching { relay?.sendBye() }
        }
        PonyAccessibilityService.instance?.setTypingKeyboardHidden(false)
        expiry?.cancel(false)
        expiry = null
        retry?.cancel(false)
        retry = null
        relay?.close()
        relay = null
        network?.let { callback -> runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) } }
        network = null
        projection?.stop()
        projection = null
        keys = null
        phone = null
        snapshot = null
        ownerConfirmed = false
        protocolVersion = 1
        vault.clear()
        handoff.finished(null)
        VoiceBus.inbox.forgetListener()
        VoiceController.onSessionEnded(status)
        SessionRepository.setProjectionGranted(false)
        SessionRepository.update {
            it.copy(
                connection = Connection.Idle,
                status = status,
                safetyCode = null,
                startedAt = null,
                endsAt = null,
                clientName = null,
                peerAway = false,
                resumable = false,
                reconnectAttempt = 0,
                nextRetryAt = null,
                endedReason = status,
                ownerConfirmed = false,
            )
        }
        BackgroundHost.release(this)
        append("session", status, true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun scheduleExpiry() {
        expiry?.cancel(false)
        val endsAt = snapshot?.endsAt ?: return
        val delay = (endsAt - System.currentTimeMillis()).coerceAtLeast(0)
        expiry = worker.schedule({ end("Session time limit reached", bye = true, why = "time_limit") }, delay, TimeUnit.MILLISECONDS)
    }

    private fun append(action: String, detail: String, ok: Boolean) {
        val entries = audit.append(AuditEntry(action = action, detail = detail, ok = ok))
        SessionRepository.update {
            // A successful action means the link is healthy again: drop any stale
            // "Connection lost" style error still showing on the home badge.
            it.copy(log = entries, lastError = if (ok && it.connection == Connection.Connected) null else it.lastError)
        }
    }

    private fun preferredName(): String = VoicePrefs.preferredClient(this).ifBlank { GrokBot.NAME }

    private fun waitingStatus(): String =
        if (GrokBot.isGrok(preferredName())) "Waiting for Grok Bot…" else "Waiting for your assistant…"

    private fun connectedStatus(name: String?): String {
        if (GrokBot.isGrok(name)) return "Connected to Grok Bot"
        if (name.isNullOrBlank()) return if (GrokBot.isGrok(preferredName())) "Connected to Grok Bot" else "Connected"
        return "Connected to $name"
    }

    private fun relayName(relay: String): String = if (CleartextPolicy.isCloud(relay)) "Pony Cloud" else "a private relay"

    private fun startInForeground(hasProjection: Boolean, text: String) {
        val types = when {
            hasProjection -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notification(text), types)
    }

    private fun updateNotification(text: String) {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text)) }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, PonySessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pony)
            .setContentTitle(text)
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFF6D5CF5.toInt())
            .addAction(0, getString(R.string.notif_disconnect), stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onDestroy() {
        commands.shutdownNow()
        waits.shutdownNow()
        worker.shutdownNow()
        super.onDestroy()
    }

    private data class Cmd(
        val ok: Boolean,
        val error: String? = null,
        val result: JSONObject? = null,
        /** A request this reply hands to the assistant. It goes back in the inbox if the reply can't be sent. */
        val handedOut: OwnerRequest? = null,
    )

    companion object {
        const val ACTION_START = "app.pony.companion.START"
        const val ACTION_STOP = "app.pony.companion.STOP"
        const val ACTION_RESUME = "app.pony.companion.RESUME"
        const val ACTION_RETRY = "app.pony.companion.RETRY"
        const val ACTION_CONFIRM = "app.pony.companion.CONFIRM"
        const val ACTION_ATTACH = "app.pony.companion.ATTACH"
        const val EXTRA_PAIRING = "pairing"
        const val EXTRA_PROJECTION_CODE = "projection_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        private const val CHANNEL_ID = "pony_session"
        private const val NOTIF_ID = 17
        private val SCREEN_OPS = setOf("tap", "swipe", "long_press", "drag", "pinch", "type", "press", "open_app", "open_settings", "screenshot", "ui_tree", "wait_idle")
        private val ACTING_OPS = setOf("tap", "swipe", "long_press", "drag", "pinch", "type", "press", "open_app", "open_settings")
        private val GATED_OPS = SCREEN_OPS + ACTING_OPS + setOf("speak", "ask_user", "confirm")

        /** Progress reason while a command waits for the owner to answer a question on the phone. */
        const val AWAITING_OWNER = "awaiting_owner"
        const val OWNER_WAIT_MS = ActionExpiry.OWNER_PROMPT_MS

        /** How often the phone heartbeats while long-polling for the owner's next request. */
        const val WAIT_HEARTBEAT_MS = 12_000L

        fun vaultFor(context: Context): SessionVault =
            SessionVault(File(context.filesDir, "session.json"), KeystoreSecretBox(KeystoreSecretBox.SESSION_ALIAS))

        fun startInert(context: Context, pairingJson: String) {
            start(context, pairingJson, 0, null)
        }

        fun confirmOwner(context: Context) {
            context.startService(Intent(context, PonySessionService::class.java).setAction(ACTION_CONFIRM))
        }

        fun attachProjection(context: Context, projectionCode: Int, projectionData: Intent?) {
            val intent = Intent(context, PonySessionService::class.java)
                .setAction(ACTION_ATTACH)
                .putExtra(EXTRA_PROJECTION_CODE, projectionCode)
            if (projectionData != null) intent.putExtra(EXTRA_PROJECTION_DATA, projectionData)
            context.startForegroundService(intent)
        }

        fun start(context: Context, pairingJson: String, projectionCode: Int, projectionData: Intent?) {
            val intent = Intent(context, PonySessionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PAIRING, pairingJson)
                .putExtra(EXTRA_PROJECTION_CODE, projectionCode)
            if (projectionData != null) {
                intent.putExtra(EXTRA_PROJECTION_DATA, projectionData)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PonySessionService::class.java).setAction(ACTION_STOP))
        }

        /** Rejoins the saved session, if it is still inside its window. */
        fun resume(context: Context): Boolean {
            val saved = runCatching { vaultFor(context).load() }.getOrNull() ?: return false
            if (!saved.resumable(System.currentTimeMillis())) return false
            context.startForegroundService(Intent(context, PonySessionService::class.java).setAction(ACTION_RESUME))
            return true
        }

        fun retry(context: Context) {
            context.startService(Intent(context, PonySessionService::class.java).setAction(ACTION_RETRY))
        }

        fun resumable(context: Context): Boolean =
            runCatching { vaultFor(context).load()?.resumable(System.currentTimeMillis()) == true }.getOrDefault(false)
    }
}
