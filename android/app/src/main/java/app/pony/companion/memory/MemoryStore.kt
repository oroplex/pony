package app.pony.companion.memory

import app.pony.companion.brain.CipherBlob
import app.pony.companion.brain.JsonValue
import app.pony.companion.brain.SecretBox
import java.io.File

/** One thing Pony keeps for the owner: a short key, its value, where it came from, and when. */
data class MemoryEntry(val key: String, val value: String, val source: String, val createdAt: Long)

/**
 * A small set of things the owner asked Pony to remember — their name, home
 * city, a usual order. The value is encrypted by the same Keystore box the brain
 * keys use, so it never touches the disk in the clear; the key and timestamp
 * stay plain so the list and the prompt summary are cheap. Newest write for a
 * key wins, and the whole thing is bounded so it can't grow without limit.
 */
class MemoryStore(
    private val file: File,
    private val box: SecretBox,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    @Synchronized
    fun put(key: String, value: String, source: String = "owner"): MemoryEntry {
        val entry = MemoryEntry(normalize(key), value.trim(), source, now())
        val all = read().filter { it.key != entry.key }.toMutableList()
        all += entry
        val trimmed = if (all.size > MAX_ENTRIES) all.sortedByDescending { it.createdAt }.take(MAX_ENTRIES) else all
        write(trimmed)
        return entry
    }

    @Synchronized
    fun get(key: String): MemoryEntry? = read().firstOrNull { it.key == normalize(key) }

    @Synchronized
    fun all(): List<MemoryEntry> = read().sortedBy { it.key }

    @Synchronized
    fun delete(key: String): Boolean {
        val target = normalize(key)
        val all = read()
        val next = all.filter { it.key != target }
        if (next.size == all.size) return false
        write(next)
        return true
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    /** Raw file text, for a test that checks values are not stored in the clear. */
    @Synchronized
    fun raw(): String = if (file.exists()) file.readText() else ""

    private fun read(): List<MemoryEntry> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        val root = JsonValue.parse(file.readText()) as? JsonValue.Obj ?: return emptyList()
        val items = root.get("memories") as? JsonValue.Arr ?: return emptyList()
        return items.items.mapNotNull { item ->
            val obj = item as? JsonValue.Obj ?: return@mapNotNull null
            val key = (obj.get("key") as? JsonValue.Str)?.value ?: return@mapNotNull null
            val iv = (obj.get("iv") as? JsonValue.Str)?.value ?: return@mapNotNull null
            val ct = (obj.get("ct") as? JsonValue.Str)?.value ?: return@mapNotNull null
            val value = runCatching { box.decrypt(CipherBlob(iv, ct)).toString(Charsets.UTF_8) }.getOrNull()
                ?: return@mapNotNull null
            MemoryEntry(
                key = key,
                value = value,
                source = (obj.get("source") as? JsonValue.Str)?.value ?: "owner",
                createdAt = (obj.get("createdAt") as? JsonValue.Num)?.value?.toLong() ?: 0L,
            )
        }
    }

    private fun write(all: List<MemoryEntry>) {
        file.parentFile?.mkdirs()
        val body = JsonValue.obj(
            "memories" to JsonValue.arr(all.map { entry ->
                val blob = box.encrypt(entry.value.toByteArray(Charsets.UTF_8))
                JsonValue.obj(
                    "key" to JsonValue.str(entry.key),
                    "iv" to JsonValue.str(blob.iv),
                    "ct" to JsonValue.str(blob.ct),
                    "source" to JsonValue.str(entry.source),
                    "createdAt" to JsonValue.num(entry.createdAt),
                )
            }),
        )
        file.writeText(body.encode())
    }

    private fun normalize(key: String): String =
        key.trim().lowercase().replace(Regex("\\s+"), "_").replace(Regex("[^a-z0-9_]"), "").trim('_')

    companion object {
        const val MAX_ENTRIES = 50
    }
}
