package app.pony.companion

/**
 * Process-local tokens for intents that must not be honored from another app.
 * Pairing VIEW stays exported (App Links). Mic / template extras do not.
 */
object InternalIntents {
    const val ACTION_REQUEST_MIC = "app.pony.companion.REQUEST_MIC"
    const val EXTRA_NONCE = "pony_internal_nonce"

    @Volatile
    private var micNonce: String? = null

    fun issueMicNonce(): String {
        val n = java.util.UUID.randomUUID().toString()
        micNonce = n
        return n
    }

    fun consumeMicNonce(value: String?): Boolean {
        val ok = !value.isNullOrBlank() && value == micNonce
        micNonce = null
        return ok
    }
}
