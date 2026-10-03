package app.pony.companion.session

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class ActionTicket(
    val id: String,
    val op: String?,
    val issuedAt: Long,
    val ttlMs: Long,
    val screenPkg: String? = null,
    val screenActivity: String? = null,
)

/**
 * Live cancel set plus the stamp taken when a frame arrives. The connector
 * sends `cancel` when it gives up; the phone also drops anything past its TTL.
 */
object ActionGate {
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    fun ticket(id: String, op: String?, params: JSONObject?, receivedAt: Long = System.currentTimeMillis()): ActionTicket {
        val clientIssued = if (params != null && params.has("issuedAt")) params.optLong("issuedAt") else null
        val clientTtl = if (params != null && params.has("ttlMs")) params.optLong("ttlMs") else null
        val fallback = ActionExpiry.ttlMs(op)
        return ActionTicket(
            id = id,
            op = op,
            issuedAt = ActionExpiry.effectiveIssuedAt(clientIssued, receivedAt),
            ttlMs = clientTtl ?: fallback,
            screenPkg = params?.optString("screenPkg")?.takeIf { it.isNotBlank() },
            screenActivity = params?.optString("screenActivity")?.takeIf { it.isNotBlank() },
        )
    }

    fun cancel(id: String) {
        if (id.isNotBlank()) cancelled += id
    }

    fun forget(id: String) {
        cancelled.remove(id)
    }

    fun isCancelled(id: String): Boolean = cancelled.contains(id)

    fun expired(ticket: ActionTicket, now: Long = System.currentTimeMillis()): Boolean =
        ActionExpiry.expired(ticket.issuedAt, ticket.ttlMs, now, ticket.ttlMs)

    fun abandoned(ticket: ActionTicket, now: Long = System.currentTimeMillis()): Boolean =
        isCancelled(ticket.id) || expired(ticket, now)
}
