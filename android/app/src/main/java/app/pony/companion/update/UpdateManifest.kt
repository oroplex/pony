package app.pony.companion.update

import app.pony.companion.brain.JsonValue
import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

/**
 * `latest.json` on the Pony download server:
 * ```
 * { "versionCode": 6, "versionName": "0.5.1", "apk": "pony-0.5.1.apk",
 *   "sha256": "<64 hex>", "size": 12345678, "publishedAt": "2026-10-02",
 *   "minSdk": 29, "changelog": ["Line one", "Line two"] }
 * ```
 * `apk` may be a file name or an https URL on the same host.
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long?,
    val changelog: List<String>,
    val publishedAt: String?,
    val minSdk: Int?,
)

object UpdateManifests {
    const val BASE = "https://download.pony.karlmagendavid.com"
    const val MANIFEST_URL = "$BASE/latest.json"
    private const val HOST = "download.pony.karlmagendavid.com"

    fun parse(raw: String, base: String = BASE): UpdateManifest {
        val root = JsonValue.parse(raw) as? JsonValue.Obj ?: error("The update file isn't valid.")
        val code = (root.get("versionCode") as? JsonValue.Num)?.value?.toInt() ?: error("The update file has no version.")
        val name = (root.get("versionName") as? JsonValue.Str)?.value?.trim().orEmpty()
        if (name.isEmpty()) error("The update file has no version name.")
        val apk = ((root.get("apk") ?: root.get("url")) as? JsonValue.Str)?.value?.trim().orEmpty()
        if (apk.isEmpty()) error("The update file has no download.")
        val sha = (root.get("sha256") as? JsonValue.Str)?.value?.trim()?.lowercase().orEmpty()
        if (!sha.matches(Regex("[0-9a-f]{64}"))) error("The update file has no valid checksum.")
        val url = resolve(apk, base)
        val changelog = when (val notes = root.get("changelog")) {
            is JsonValue.Arr -> notes.items.mapNotNull { (it as? JsonValue.Str)?.value?.trim()?.takeIf(String::isNotEmpty) }
            is JsonValue.Str -> notes.value.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
        return UpdateManifest(
            versionCode = code,
            versionName = name,
            apkUrl = url,
            sha256 = sha,
            size = (root.get("size") as? JsonValue.Num)?.value?.toLong(),
            changelog = changelog,
            publishedAt = (root.get("publishedAt") as? JsonValue.Str)?.value,
            minSdk = (root.get("minSdk") as? JsonValue.Num)?.value?.toInt(),
        )
    }

    fun isNewer(manifest: UpdateManifest, installedCode: Int): Boolean = manifest.versionCode > installedCode

    fun supports(manifest: UpdateManifest, sdk: Int): Boolean = manifest.minSdk == null || sdk >= manifest.minSdk

    /** Downloads come only from the Pony download host, over https. */
    fun resolve(apk: String, base: String = BASE): String {
        val uri = if (apk.startsWith("https://") || apk.startsWith("http://")) URI(apk) else URI("$base/").resolve(apk)
        require(uri.scheme == "https") { "Updates must download over https." }
        require(uri.host.equals(HOST, ignoreCase = true) || uri.host.equals(URI(base).host, ignoreCase = true)) {
            "Updates must come from $HOST."
        }
        return uri.toString()
    }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(file: File, expected: String): Boolean =
        file.inputStream().use { sha256(it) }.equals(expected.trim(), ignoreCase = true)
}
