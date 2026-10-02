package app.pony.companion.net

import java.net.URI

/**
 * Plain http/ws is allowed only for private networks: localhost, Tailscale
 * (MagicDNS *.ts.net and 100.64.0.0/10), and RFC 1918 LAN ranges
 * (10/8, 172.16/12, 192.168/16). Everything else must be https/wss.
 * End-to-end encryption still wraps every session, including these.
 */
object CleartextPolicy {
    const val CLOUD_HTTP = "https://relay.pony.karlmagendavid.com"
    const val CLOUD_HOST = "relay.pony.karlmagendavid.com"
    const val TAILSCALE_URL = "https://tailscale.com"

    fun allows(relay: String): Boolean {
        val uri = parse(relay) ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = hostOf(uri) ?: return false
        if (scheme == "https" || scheme == "wss") return true
        if (scheme != "http" && scheme != "ws") return false
        return allowsCleartextHost(host)
    }

    fun allowsCleartextHost(rawHost: String): Boolean {
        val host = rawHost.lowercase().trim().removePrefix("[").removeSuffix("]")
        return isLocal(host) || isTailscaleName(host) || isTailscaleIp(host) || isLanIp(host)
    }

    fun isCloud(relay: String): Boolean = parse(relay)?.host?.equals(CLOUD_HOST, ignoreCase = true) == true

    fun isPrivate(relay: String): Boolean {
        val host = parse(relay)?.let { hostOf(it) } ?: return false
        return allowsCleartextHost(host)
    }

    fun sameHost(saved: String, relay: String): Boolean {
        val a = parse(saved)?.host ?: return false
        val b = parse(relay)?.host ?: return false
        return a.equals(b, ignoreCase = true)
    }

    /** Canonical origin without a trailing /ws, suitable for a pairing payload. */
    fun canonical(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
                trimmed.startsWith("ws://") || trimmed.startsWith("wss://") -> trimmed
            trimmed.contains("://") -> return null
            else -> "http://$trimmed"
        }
        val uri = parse(withScheme.removeSuffix("/").removeSuffix("/ws")) ?: return null
        if (uri.host.isNullOrBlank()) return null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host}$port"
    }

    private fun parse(raw: String): URI? {
        val trimmed = raw.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return null
        return try {
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            URI(withScheme)
        } catch (_: Exception) {
            null
        }
    }

    private fun hostOf(uri: URI): String? =
        uri.host?.lowercase()?.trim()?.removePrefix("[")?.removeSuffix("]")?.takeIf { it.isNotBlank() }

    private fun isLocal(host: String): Boolean =
        host == "localhost" || host == "127.0.0.1" || host == "::1"

    private fun isTailscaleName(host: String): Boolean =
        host == "ts.net" || host.endsWith(".ts.net")

    /** 100.64.0.0/10 */
    fun isTailscaleIp(host: String): Boolean {
        val nums = ipv4(host) ?: return false
        return nums[0] == 100 && nums[1] in 64..127
    }

    /** 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 */
    fun isLanIp(host: String): Boolean {
        val nums = ipv4(host) ?: return false
        return nums[0] == 10 ||
            (nums[0] == 172 && nums[1] in 16..31) ||
            (nums[0] == 192 && nums[1] == 168)
    }

    private fun ipv4(host: String): List<Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val nums = parts.map { part -> part.toIntOrNull()?.takeIf { part.isNotEmpty() && part.length <= 3 } ?: return null }
        if (nums.any { it !in 0..255 }) return null
        return nums
    }
}
