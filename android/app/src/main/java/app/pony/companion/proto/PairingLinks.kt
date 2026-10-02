package app.pony.companion.proto

import java.net.URLDecoder

/**
 * Decodes the link shapes a pairing can arrive as:
 *  - `pony://pair?v=&relay=&token=&pk=` — the app's own deep link.
 *  - `https://<host>/pair#v=&relay=&token=&pk=&exp=` — the tap link the download
 *    page hands out, with the fields in the fragment (or the query).
 * The QR itself stays the JSON payload; this path is for a link the phone can
 * open, or a code the user pastes, when the QR is on the same device.
 */
object PairingLinks {
    data class Fields(
        val v: String,
        val relay: String,
        val token: String,
        val pk: String,
        /** Wall-clock expiry carried by the https tap link; epoch seconds or millis. */
        val exp: Long? = null,
    )

    /** A pairing link whose time is already up. */
    class PairingLinkExpired : IllegalStateException("This pairing link expired — ask for a new one.")

    /** The app's own `pony://pair` deep link. Kept narrow on purpose. */
    fun isPairLink(raw: String): Boolean {
        val head = raw.trim().substringBefore('?').substringBefore('#').trimEnd('/')
        return head.equals("pony://pair", ignoreCase = true)
    }

    /** The https hand-off link, e.g. `https://download.pony…/pair#v=1&…`. */
    fun isHttpPairLink(raw: String): Boolean {
        val trimmed = raw.trim()
        val scheme = trimmed.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return false
        val afterHost = trimmed.substringAfter("://", "").substringBefore('?').substringBefore('#')
        val slash = afterHost.indexOf('/')
        if (slash < 0) return false
        // The tap link is `/pair`; the hand-off page is served as `/pair.html`.
        val route = afterHost.substring(slash).trimEnd('/').removeSuffix(".html").removeSuffix(".htm")
        return route.equals("/pair", ignoreCase = true)
    }

    /** Any link shape Pony pairs from (not the raw QR JSON). */
    fun looksLikePairing(raw: String): Boolean = isPairLink(raw) || isHttpPairLink(raw)

    fun fields(raw: String): Fields {
        val trimmed = raw.trim()
        require(looksLikePairing(trimmed)) { "not a pony pairing link" }
        val map = params(trimmed)
        val v = map["v"].orEmpty()
        val relay = map["relay"].orEmpty()
        val token = map["token"].orEmpty()
        val pk = map["pk"].orEmpty()
        require(v.isNotBlank() && relay.isNotBlank() && token.isNotBlank() && pk.isNotBlank()) {
            "pairing link missing v, relay, token, or pk"
        }
        return Fields(v = v, relay = relay, token = token, pk = pk, exp = map["exp"]?.trim()?.toLongOrNull())
    }

    /**
     * True when [fields] carries an expiry that has already passed. The tap
     * link's `exp` may be epoch seconds or millis; both are understood.
     */
    fun isExpired(fields: Fields, nowMs: Long = System.currentTimeMillis()): Boolean {
        val exp = fields.exp ?: return false
        val expMs = if (exp < 1_000_000_000_000L) exp * 1000 else exp
        return expMs <= nowMs
    }

    /**
     * Optional `client` on a pairing link. It is not part of the QR JSON.
     * `client=grokbot` is the Grok Bot template.
     */
    fun clientParam(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (!looksLikePairing(trimmed)) return null
        return params(trimmed)["client"]?.trim()?.ifEmpty { null }
    }

    /** Merges a link's query and fragment params; the fragment wins on a tie. */
    private fun params(raw: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val qStart = raw.indexOf('?')
        val hStart = raw.indexOf('#')
        val query = when {
            qStart < 0 -> ""
            hStart in 0 until qStart -> ""
            hStart < 0 -> raw.substring(qStart + 1)
            else -> raw.substring(qStart + 1, hStart)
        }
        val fragment = if (hStart >= 0) raw.substring(hStart + 1) else ""
        putPairs(map, query)
        putPairs(map, fragment)
        return map
    }

    private fun putPairs(map: MutableMap<String, String>, blob: String) {
        if (blob.isEmpty()) return
        for (part in blob.split('&')) {
            if (part.isEmpty()) continue
            val eq = part.indexOf('=')
            val key = decode(if (eq < 0) part else part.substring(0, eq))
            val value = decode(if (eq < 0) "" else part.substring(eq + 1))
            if (key.isNotEmpty()) map[key] = value
        }
    }

    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")
}
