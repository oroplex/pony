package app.pony.companion.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Connection { Idle, Pairing, Connected, Reconnecting, Ending, Error }

data class SessionUi(
    val connection: Connection = Connection.Idle,
    val status: String = "Not connected",
    val safetyCode: String? = null,
    val startedAt: Long? = null,
    val endsAt: Long? = null,
    val lastError: String? = null,
    val accessibilityOn: Boolean = false,
    val projectionGranted: Boolean = false,
    val log: List<AuditEntry> = emptyList(),
    val notice: String? = null,
    val clientName: String? = null,
    val relay: String? = null,
    val peerAway: Boolean = false,
    val reconnectAttempt: Int = 0,
    val nextRetryAt: Long? = null,
    val resumable: Boolean = false,
    val endedReason: String? = null,
    val ownerConfirmed: Boolean = false,
) {
    val live: Boolean get() = connection == Connection.Connected || connection == Connection.Reconnecting
    val busy: Boolean get() = connection == Connection.Pairing || connection == Connection.Ending || live
}

object SessionRepository {
    private val _ui = MutableStateFlow(SessionUi())
    val ui: StateFlow<SessionUi> = _ui.asStateFlow()

    fun snapshot(): SessionUi = _ui.value

    fun setAccessibilityEnabled(on: Boolean) {
        _ui.update { it.copy(accessibilityOn = on) }
        Readiness.refreshAccessibility(on)
    }

    fun setProjectionGranted(on: Boolean) {
        _ui.update { it.copy(projectionGranted = on) }
    }

    fun update(transform: (SessionUi) -> SessionUi) {
        _ui.update(transform)
    }

    fun resetConnection() {
        _ui.update {
            it.copy(
                connection = Connection.Idle,
                status = "Not connected",
                safetyCode = null,
                startedAt = null,
                endsAt = null,
                clientName = null,
                notice = null,
                peerAway = false,
                reconnectAttempt = 0,
                nextRetryAt = null,
                ownerConfirmed = false,
            )
        }
    }
}
