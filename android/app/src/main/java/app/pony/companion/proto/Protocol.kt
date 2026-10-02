package app.pony.companion.proto

import org.json.JSONObject

const val PROTOCOL_VERSION = 1
const val PAIRING_TTL_MS = 15 * 60 * 1000L
const val SESSION_TTL_MS = 30 * 60 * 1000L
const val HKDF_INFO = "pony-companion-v1"
const val AEAD_AAD = "pony-v1"
const val SAFETY_INFO = "pony-safety"

data class PairingPayload(
    val relay: String,
    val token: String,
    val pk: String,
) {
    fun toJson(): String =
        JSONObject()
            .put("v", PROTOCOL_VERSION)
            .put("relay", relay)
            .put("token", token)
            .put("pk", pk)
            .toString()

    companion object {
        fun parse(raw: String): PairingPayload {
            val obj = JSONObject(raw.trim())
            require(obj.optInt("v") == PROTOCOL_VERSION) { "unsupported pairing version" }
            val relay = obj.getString("relay")
            val token = obj.getString("token").lowercase()
            val pk = obj.getString("pk")
            require(token.matches(Regex("[0-9a-f]{64}"))) { "pairing token must be 32 bytes hex" }
            require(relay.isNotBlank() && pk.isNotBlank()) { "pairing payload incomplete" }
            return PairingPayload(relay = relay, token = token, pk = pk)
        }
    }
}

data class AppMessage(
    val id: String,
    val kind: String,
    val op: String? = null,
    val params: JSONObject? = null,
    val ok: Boolean? = null,
    val error: String? = null,
    val result: JSONObject? = null,
) {
    fun toJson(): JSONObject {
        val obj = JSONObject().put("id", id).put("kind", kind)
        if (op != null) obj.put("op", op)
        if (params != null) obj.put("params", params)
        if (ok != null) obj.put("ok", ok)
        if (error != null) obj.put("error", error)
        if (result != null) obj.put("result", result)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): AppMessage =
            AppMessage(
                id = obj.getString("id"),
                kind = obj.getString("kind"),
                op = obj.optString("op").ifBlank { null },
                params = obj.optJSONObject("params"),
                ok = if (obj.has("ok")) obj.getBoolean("ok") else null,
                error = obj.optString("error").ifBlank { null },
                result = obj.optJSONObject("result"),
            )
    }
}
