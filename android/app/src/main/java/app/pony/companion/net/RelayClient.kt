package app.pony.companion.net

import app.pony.companion.crypto.SessionCrypto
import app.pony.companion.proto.AppMessage
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RelayClient(
    private val wsUrl: String,
    private val token: String,
    private val role: String = "phone",
    private val listener: Listener,
) {
    interface Listener {
        /** Both peers are in the room. [resumed] is true when this rejoined a live session. */
        fun onRelayReady(resumed: Boolean)
        fun onClientName(name: String)
        fun onFrame(data: String)
        fun onPlainHandshakeNeeded()

        /** The assistant dropped but the relay is holding the session for it. */
        fun onPeerAway() = Unit

        /** [fatal] means the session is over. Otherwise the phone should reconnect. */
        fun onClosed(reason: String, fatal: Boolean)
        fun onError(message: String, fatal: Boolean)
    }

    private var http: OkHttpClient? = null
    private var socket: WebSocket? = null
    private val finished = AtomicBoolean(false)

    fun connect() {
        if (!CleartextPolicy.allows(wsUrl)) {
            listener.onError("That relay address isn't allowed. Use Pony Cloud, Tailscale, or a private network.", fatal = true)
            return
        }
        val httpClient = httpClientFor(wsUrl)
        http = httpClient
        val request = Request.Builder().url(dialUrl(wsUrl)).build()
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = JSONObject()
                    .put("type", "hello")
                    .put("role", role)
                    .put("token", token)
                    .put("resume", true)
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (obj.optString("type")) {
                    "waiting" -> Unit
                    "ready" -> {
                        listener.onRelayReady(obj.optBoolean("resumed", false))
                        val client = obj.optString("client")
                        if (client.isNotBlank()) listener.onClientName(client)
                        listener.onPlainHandshakeNeeded()
                    }
                    "fwd" -> listener.onFrame(obj.optString("data"))
                    "peer_away" -> listener.onPeerAway()
                    "peer_left" -> finish { listener.onClosed("peer_left", fatal = true) }
                    "error" -> {
                        val reason = obj.optString("reason", "relay_error")
                        val fatal = reason in FATAL_REASONS
                        if (fatal) finish { listener.onError(reason, fatal = true) } else listener.onError(reason, fatal = false)
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                val fatal = code == CLOSE_PEER_LEFT || code == CLOSE_ROOM_GONE || reason in FATAL_REASONS
                finish { listener.onClosed(reason.ifBlank { "closed" }, fatal) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish { listener.onError(t.message ?: "socket_failed", fatal = false) }
            }
        })
    }

    fun sendHandshake(publicKey: ByteArray) {
        val inner = JSONObject()
            .put("type", "hs")
            .put("pk", SessionCrypto.b64urlEncode(publicKey))
        sendRaw(JSONObject().put("type", "fwd").put("data", inner.toString()).toString())
    }

    fun sendEncrypted(payloadB64: String): Boolean =
        sendRaw(JSONObject().put("type", "fwd").put("data", payloadB64).toString())

    /** False when the socket is already gone, so the frame never left the phone. */
    fun sendMessage(keysSend: ByteArray, message: AppMessage): Boolean {
        val cipher = SessionCrypto.encrypt(keysSend, message.toJson().toString().toByteArray())
        return sendEncrypted(SessionCrypto.b64urlEncode(cipher))
    }

    /** Ends the session for both sides. The relay drops the room. */
    fun sendBye() {
        sendRaw(JSONObject().put("type", "bye").toString())
    }

    fun close() {
        finished.set(true)
        socket?.close(1000, "disconnect")
        socket = null
        http?.dispatcher?.executorService?.shutdown()
        http = null
    }

    private fun finish(report: () -> Unit) {
        if (finished.compareAndSet(false, true)) report()
    }

    private fun sendRaw(text: String): Boolean = socket?.send(text) ?: false

    companion object {
        /** Hostnames in the network security config. Raw IP ranges are not domain suffixes. */
        const val TAILSCALE_DIAL_HOST = "pony-tailscale.invalid"
        const val LAN_DIAL_HOST = "pony-lan.invalid"
        const val CLOSE_PEER_LEFT = 4001
        const val CLOSE_ROOM_GONE = 4000

        val FATAL_REASONS = setOf("unknown_token", "expired_token", "room_closed", "bad_token", "peer_left", "session_ended")

        fun normalizeWs(relay: String): String {
            val trimmed = relay.trim().removeSuffix("/")
            val asWs = when {
                trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
                trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
                else -> trimmed
            }
            return if (asWs.endsWith("/ws")) asWs else "$asWs/ws"
        }

        /**
         * OkHttp checks cleartext against the URL host. Tailscale and LAN
         * addresses dial an alias the network security config allows, and a
         * custom DNS returns the real address.
         */
        fun dialUrl(relay: String): String {
            val normalized = normalizeWs(relay)
            val host = hostOf(normalized) ?: return normalized
            val alias = aliasFor(host) ?: return normalized
            val uri = httpUri(normalized) ?: return normalized
            val scheme = if (normalized.startsWith("wss://")) "wss" else "ws"
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val path = uri.rawPath?.ifBlank { "/ws" } ?: "/ws"
            return "$scheme://$alias$port$path"
        }

        fun aliasFor(host: String): String? = when {
            CleartextPolicy.isTailscaleIp(host) -> TAILSCALE_DIAL_HOST
            CleartextPolicy.isLanIp(host) -> LAN_DIAL_HOST
            else -> null
        }

        private fun httpClientFor(relay: String): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(12, TimeUnit.SECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
            val host = hostOf(normalizeWs(relay))
            val alias = host?.let { aliasFor(it) }
            if (host != null && alias != null) {
                builder.dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        if (hostname.equals(alias, ignoreCase = true)) {
                            return listOf(InetAddress.getByName(host))
                        }
                        return Dns.SYSTEM.lookup(hostname)
                    }
                })
            }
            return builder.build()
        }

        private fun hostOf(ws: String): String? = httpUri(ws)?.host

        private fun httpUri(ws: String): URI? = try {
            URI(ws.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://"))
        } catch (_: Exception) {
            null
        }
    }
}
