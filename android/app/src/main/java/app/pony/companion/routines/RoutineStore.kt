package app.pony.companion.routines

import app.pony.companion.brain.JsonValue
import java.io.File
import java.util.UUID

/**
 * The owner's saved routines, kept as plain JSON on the phone. A routine is just
 * a name over an instruction the owner would say out loud, so unlike memory it
 * isn't encrypted. Bounded so a runaway loop of "save this" can't fill the disk.
 *
 * Everything is pure (a [File], an injectable clock and id maker), so the store
 * can be exercised on the JVM without a device.
 */
class RoutineStore(
    private val file: File,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString().take(8) },
) {
    /**
     * Saves [text] under [name]. A name already in use is overwritten in place,
     * so saving "my usual lunch" twice keeps one routine rather than two. Blank
     * names or bodies are refused. Returns the saved routine, or null.
     */
    @Synchronized
    fun save(name: String, text: String): Routine? {
        val cleanName = name.trim().take(Routines.MAX_NAME)
        val cleanText = text.trim()
        if (cleanName.isEmpty() || cleanText.isEmpty()) return null
        val all = read()
        val key = Routines.normalize(cleanName)
        val existing = all.firstOrNull { Routines.normalize(it.name) == key }
        val routine = existing?.copy(name = cleanName, text = cleanText)
            ?: Routine(newId(), cleanName, cleanText, createdAt = now())
        val next = all.filter { it.id != existing?.id } + routine
        val bounded = if (next.size > MAX_ENTRIES) next.sortedByDescending { it.createdAt }.take(MAX_ENTRIES) else next
        write(bounded)
        return routine
    }

    @Synchronized
    fun get(id: String): Routine? = read().firstOrNull { it.id == id }

    /** Newest use first, so the routines the owner reaches for sit at the top. */
    @Synchronized
    fun all(): List<Routine> = read().sortedByDescending { it.lastRunAt ?: it.createdAt }

    /** The routine whose name the owner just said, if any — the replay lookup. */
    @Synchronized
    fun match(utterance: String): Routine? {
        val all = read()
        val name = Routines.pick(utterance, all.map { it.name }) ?: return null
        return all.firstOrNull { it.name == name }
    }

    /** Renames a routine, refusing a blank name or one that collides with another. */
    @Synchronized
    fun rename(id: String, name: String): Routine? {
        val clean = name.trim().take(Routines.MAX_NAME)
        if (clean.isEmpty()) return null
        val all = read()
        val target = all.firstOrNull { it.id == id } ?: return null
        val key = Routines.normalize(clean)
        if (all.any { it.id != id && Routines.normalize(it.name) == key }) return null
        val updated = target.copy(name = clean)
        write(all.map { if (it.id == id) updated else it })
        return updated
    }

    /** Records that a routine just ran, for the "ran 3 times" line and recency sort. */
    @Synchronized
    fun markRun(id: String): Routine? {
        val all = read()
        val target = all.firstOrNull { it.id == id } ?: return null
        val updated = target.copy(lastRunAt = now(), runCount = target.runCount + 1)
        write(all.map { if (it.id == id) updated else it })
        return updated
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val all = read()
        val next = all.filter { it.id != id }
        if (next.size == all.size) return false
        write(next)
        return true
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    private fun read(): List<Routine> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        val root = JsonValue.parse(file.readText()) as? JsonValue.Obj ?: return emptyList()
        val items = root.get("routines") as? JsonValue.Arr ?: return emptyList()
        return items.items.mapNotNull { item ->
            val obj = item as? JsonValue.Obj ?: return@mapNotNull null
            val id = (obj.get("id") as? JsonValue.Str)?.value ?: return@mapNotNull null
            val name = (obj.get("name") as? JsonValue.Str)?.value ?: return@mapNotNull null
            val text = (obj.get("text") as? JsonValue.Str)?.value ?: return@mapNotNull null
            val createdAt = (obj.get("createdAt") as? JsonValue.Num)?.value?.toLong() ?: 0L
            val lastRunAt = (obj.get("lastRunAt") as? JsonValue.Num)?.value?.toLong()
            val runCount = (obj.get("runCount") as? JsonValue.Num)?.value?.toInt() ?: 0
            Routine(id, name, text, createdAt, lastRunAt, runCount)
        }
    }

    private fun write(all: List<Routine>) {
        file.parentFile?.mkdirs()
        val body = JsonValue.obj(
            "routines" to JsonValue.arr(
                all.map { routine ->
                    JsonValue.obj(
                        "id" to JsonValue.str(routine.id),
                        "name" to JsonValue.str(routine.name),
                        "text" to JsonValue.str(routine.text),
                        "createdAt" to JsonValue.num(routine.createdAt),
                        "lastRunAt" to (routine.lastRunAt?.let { JsonValue.num(it) } ?: JsonValue.Null),
                        "runCount" to JsonValue.num(routine.runCount),
                    )
                },
            ),
        )
        file.writeText(body.encode())
    }

    companion object {
        const val MAX_ENTRIES = 30
    }
}
