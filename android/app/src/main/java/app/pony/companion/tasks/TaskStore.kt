package app.pony.companion.tasks

import app.pony.companion.brain.JsonValue
import java.io.File

/**
 * Task history in app-private storage: what was asked, each step, the
 * outcome, and small screenshots. Nothing here leaves the phone.
 */
class TaskStore(private val root: File, private val keep: Int = KEEP) {
    private val index = File(root, "index.json")

    @Synchronized
    fun list(): List<TaskRecord> {
        if (!index.exists() || index.length() == 0L) return emptyList()
        return try {
            val parsed = JsonValue.parse(index.readText()) as? JsonValue.Obj ?: return emptyList()
            val items = parsed.get("tasks") as? JsonValue.Arr ?: return emptyList()
            items.items.mapNotNull { TaskRecord.fromJson(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun get(id: String): TaskRecord? = list().firstOrNull { it.id == id }

    @Synchronized
    fun upsert(record: TaskRecord): List<TaskRecord> {
        val others = list().filter { it.id != record.id }
        val next = (listOf(record) + others).sortedByDescending { it.createdAt }
        val kept = next.take(keep)
        next.drop(keep).forEach { File(root, it.id).deleteRecursively() }
        write(kept)
        return kept
    }

    @Synchronized
    fun delete(id: String): List<TaskRecord> {
        File(root, id).deleteRecursively()
        val next = list().filter { it.id != id }
        write(next)
        return next
    }

    @Synchronized
    fun clear() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Stores a JPEG for [taskId] and returns the name the step keeps. */
    @Synchronized
    fun saveShot(taskId: String, jpeg: ByteArray): String? {
        if (jpeg.isEmpty()) return null
        val dir = File(root, safe(taskId)).apply { mkdirs() }
        val existing = dir.listFiles { file -> file.name.endsWith(".jpg") }?.size ?: 0
        if (existing >= MAX_SHOTS) return null
        val name = "shot-${existing + 1}.jpg"
        File(dir, name).writeBytes(jpeg)
        return name
    }

    fun shotFile(taskId: String, name: String): File = File(File(root, safe(taskId)), safe(name))

    private fun write(records: List<TaskRecord>) {
        root.mkdirs()
        val body = JsonValue.obj("tasks" to JsonValue.arr(records.map { it.toJson() }))
        val temp = File(root, "index.json.tmp")
        temp.writeText(body.encode())
        if (!temp.renameTo(index)) {
            index.writeText(body.encode())
            temp.delete()
        }
    }

    private fun safe(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        const val KEEP = 100
        const val MAX_SHOTS = 40
    }
}
