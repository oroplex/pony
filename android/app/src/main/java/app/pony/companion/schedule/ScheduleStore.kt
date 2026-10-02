package app.pony.companion.schedule

import app.pony.companion.brain.JsonValue
import java.io.File
import java.time.DayOfWeek
import java.util.UUID

/** One scheduled task: the words Pony should run, when, and whether it's switched on. */
data class ScheduledTask(
    val id: String,
    val text: String,
    val schedule: Schedule,
    val enabled: Boolean,
    val createdAt: Long,
)

/**
 * The owner's scheduled tasks, kept as plain JSON on the phone. Nothing here is
 * a secret — a task is an instruction like "read my calendar", the same words
 * the owner would type — so unlike memory it isn't encrypted. Bounded so a
 * runaway loop of "schedule this" can't fill the disk.
 */
class ScheduleStore(
    private val file: File,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString().take(8) },
) {
    @Synchronized
    fun add(text: String, schedule: Schedule): ScheduledTask {
        val task = ScheduledTask(newId(), text.trim(), schedule, enabled = true, createdAt = now())
        val all = read().toMutableList()
        all += task
        val trimmed = if (all.size > MAX_ENTRIES) all.sortedByDescending { it.createdAt }.take(MAX_ENTRIES) else all
        write(trimmed)
        return task
    }

    @Synchronized
    fun get(id: String): ScheduledTask? = read().firstOrNull { it.id == id }

    @Synchronized
    fun all(): List<ScheduledTask> = read().sortedBy { it.createdAt }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean): ScheduledTask? {
        val all = read()
        val target = all.firstOrNull { it.id == id } ?: return null
        val updated = target.copy(enabled = enabled)
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

    private fun read(): List<ScheduledTask> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        val root = JsonValue.parse(file.readText()) as? JsonValue.Obj ?: return emptyList()
        val items = root.get("tasks") as? JsonValue.Arr ?: return emptyList()
        return items.items.mapNotNull { item ->
            val obj = item as? JsonValue.Obj ?: return@mapNotNull null
            val id = (obj.get("id") as? JsonValue.Str)?.value ?: return@mapNotNull null
            val text = (obj.get("text") as? JsonValue.Str)?.value ?: return@mapNotNull null
            val hour = (obj.get("hour") as? JsonValue.Num)?.value?.toInt() ?: return@mapNotNull null
            val minute = (obj.get("minute") as? JsonValue.Num)?.value?.toInt() ?: 0
            val days = (obj.get("days") as? JsonValue.Arr)?.items.orEmpty().mapNotNull { day ->
                (day as? JsonValue.Num)?.value?.toInt()?.let { iso -> runCatching { DayOfWeek.of(iso) }.getOrNull() }
            }.toSet()
            val onceAt = (obj.get("onceAt") as? JsonValue.Num)?.value?.toLong()
            val enabled = (obj.get("enabled") as? JsonValue.Bool)?.value ?: true
            val createdAt = (obj.get("createdAt") as? JsonValue.Num)?.value?.toLong() ?: 0L
            ScheduledTask(id, text, Schedule(hour, minute, days, onceAt), enabled, createdAt)
        }
    }

    private fun write(all: List<ScheduledTask>) {
        file.parentFile?.mkdirs()
        val body = JsonValue.obj(
            "tasks" to JsonValue.arr(all.map { task ->
                JsonValue.obj(
                    "id" to JsonValue.str(task.id),
                    "text" to JsonValue.str(task.text),
                    "hour" to JsonValue.num(task.schedule.hour),
                    "minute" to JsonValue.num(task.schedule.minute),
                    "days" to JsonValue.arr(task.schedule.days.sorted().map { JsonValue.num(it.value) }),
                    "onceAt" to (task.schedule.onceAt?.let { JsonValue.num(it) } ?: JsonValue.Null),
                    "enabled" to JsonValue.bool(task.enabled),
                    "createdAt" to JsonValue.num(task.createdAt),
                )
            }),
        )
        file.writeText(body.encode())
    }

    companion object {
        const val MAX_ENTRIES = 20
    }
}
