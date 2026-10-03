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
    val clockSkew: Boolean = false,
)

/**
 * Live cancel set plus the stamp taken when a frame arrives. The connector
 * sends `cancel` when it gives up; the phone also drops anything past its TTL.
 */
object ActionGate {
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val executed = ExecutedIds()

    fun ticket(id: String, op: String?, params: JSONObject?, receivedAt: Long = System.currentTimeMillis()): ActionTicket {
        val clientIssued = if (params != null && params.has("issuedAt")) params.optLong("issuedAt") else null
        val clientTtl = if (params != null && params.has("ttlMs")) params.optLong("ttlMs") else null
        val fallback = ActionExpiry.ttlMs(op)
        val issued = ActionExpiry.resolveIssuedAt(clientIssued, receivedAt)
        return ActionTicket(
            id = id,
            op = op,
            issuedAt = when (issued) {
                is ActionExpiry.IssuedAt.Ok -> issued.at
                ActionExpiry.IssuedAt.Skewed -> receivedAt
            },
            ttlMs = clientTtl ?: fallback,
            screenPkg = params?.optString("screenPkg")?.takeIf { it.isNotBlank() },
            screenActivity = params?.optString("screenActivity")?.takeIf { it.isNotBlank() },
            clockSkew = issued is ActionExpiry.IssuedAt.Skewed,
        )
    }

    fun claim(id: String): Boolean = executed.remember(id)

    fun alreadyRan(id: String): Boolean = executed.has(id)

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
