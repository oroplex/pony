package app.pony.companion.session

import app.pony.companion.brain.CipherBlob
import app.pony.companion.brain.JsonValue
import app.pony.companion.brain.SecretBox
import java.io.File

enum class SessionLength(val wire: String, val millis: Long?, val label: String) {
    HALF_HOUR("30m", 30 * 60 * 1000L, "30 minutes"),
    TWO_HOURS("2h", 2 * 60 * 60 * 1000L, "2 hours"),
    EIGHT_HOURS("8h", 8 * 60 * 60 * 1000L, "8 hours"),
    UNTIL_DISCONNECT("open", null, "Until I disconnect");

    companion object {
        fun of(wire: String?): SessionLength = entries.firstOrNull { it.wire == wire } ?: HALF_HOUR
    }
}

/** What the phone needs to rejoin a live session after a drop, a process restart, or a reboot. */
data class SessionSnapshot(
    val relay: String,
    val token: String,
    val botPk: String,
    val phonePrivateKey: String,
    val phonePublicKey: String,
    val clientName: String?,
    val startedAt: Long,
    val endsAt: Long?,
    val safetyCode: String,
    val ownerConfirmed: Boolean = false,
    val protocolVersion: Int = 1,
    val sendSeq: Long = 0,
    val recvSeq: Long = 0,
) {
    fun resumable(now: Long): Boolean = endsAt == null || now < endsAt

    fun encode(): String = JsonValue.Obj(
        buildList {
            add("relay" to JsonValue.str(relay))
            add("token" to JsonValue.str(token))
            add("botPk" to JsonValue.str(botPk))
            add("phonePrivateKey" to JsonValue.str(phonePrivateKey))
            add("phonePublicKey" to JsonValue.str(phonePublicKey))
            clientName?.let { add("clientName" to JsonValue.str(it)) }
            add("startedAt" to JsonValue.num(startedAt))
            endsAt?.let { add("endsAt" to JsonValue.num(it)) }
            add("safetyCode" to JsonValue.str(safetyCode))
            add("ownerConfirmed" to JsonValue.bool(ownerConfirmed))
            add("protocolVersion" to JsonValue.num(protocolVersion.toDouble()))
            add("sendSeq" to JsonValue.num(sendSeq.toDouble()))
            add("recvSeq" to JsonValue.num(recvSeq.toDouble()))
        },
    ).encode()

    companion object {
        fun decode(raw: String): SessionSnapshot? {
            val obj = runCatching { JsonValue.parse(raw) as? JsonValue.Obj }.getOrNull() ?: return null
            fun str(name: String) = (obj.get(name) as? JsonValue.Str)?.value
            fun num(name: String) = (obj.get(name) as? JsonValue.Num)?.value?.toLong()
            return SessionSnapshot(
                relay = str("relay") ?: return null,
                token = str("token") ?: return null,
                botPk = str("botPk") ?: return null,
                phonePrivateKey = str("phonePrivateKey") ?: return null,
                phonePublicKey = str("phonePublicKey") ?: return null,
                clientName = str("clientName"),
                startedAt = num("startedAt") ?: 0L,
                endsAt = num("endsAt"),
                safetyCode = str("safetyCode").orEmpty(),
                ownerConfirmed = (obj.get("ownerConfirmed") as? JsonValue.Bool)?.value == true,
                protocolVersion = num("protocolVersion")?.toInt() ?: 1,
                sendSeq = num("sendSeq") ?: 0L,
                recvSeq = num("recvSeq") ?: 0L,
            )
        }
    }
}

/** The live session on disk, sealed with [box] (Android Keystore on the phone). */
class SessionVault(private val file: File, private val box: SecretBox) {
    @Synchronized
    fun save(snapshot: SessionSnapshot) {
        val blob = box.encrypt(snapshot.encode().toByteArray(Charsets.UTF_8))
        file.parentFile?.mkdirs()
        file.writeText(JsonValue.obj("iv" to JsonValue.str(blob.iv), "ct" to JsonValue.str(blob.ct)).encode())
    }

    @Synchronized
    fun load(): SessionSnapshot? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val obj = JsonValue.parse(file.readText()) as? JsonValue.Obj ?: return null
            val iv = (obj.get("iv") as? JsonValue.Str)?.value ?: return null
            val ct = (obj.get("ct") as? JsonValue.Str)?.value ?: return null
            SessionSnapshot.decode(box.decrypt(CipherBlob(iv, ct)).toString(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    fun raw(): String = if (file.exists()) file.readText() else ""
}
