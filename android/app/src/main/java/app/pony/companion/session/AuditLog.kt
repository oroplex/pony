package app.pony.companion.session

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AuditEntry(
    val id: String = UUID.randomUUID().toString(),
    val at: Long = System.currentTimeMillis(),
    val action: String,
    val detail: String,
    val ok: Boolean,
)

class AuditLog(context: Context) {
    private val file = File(context.filesDir, "audit-log.json")

    @Synchronized
    fun append(entry: AuditEntry): List<AuditEntry> {
        val all = read().toMutableList()
        all.add(0, entry)
        val trimmed = all.take(500)
        write(trimmed)
        return trimmed
    }

    @Synchronized
    fun read(): List<AuditEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        AuditEntry(
                            id = o.getString("id"),
                            at = o.getLong("at"),
                            action = o.getString("action"),
                            detail = o.optString("detail"),
                            ok = o.optBoolean("ok", true),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun write(entries: List<AuditEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("at", e.at)
                    .put("action", e.action)
                    .put("detail", e.detail)
                    .put("ok", e.ok),
            )
        }
        file.writeText(arr.toString())
    }
}
