package app.pony.companion.session

/**
 * Pairing confirmation rules. Pure so the "It matches" gate and intent
 * policy can be unit-tested without Android.
 */
object PairingGate {
    /** Screen capture and acting commands stay off until the owner taps It matches. */
    fun mayCapture(ownerConfirmed: Boolean): Boolean = ownerConfirmed

    fun mayAct(ownerConfirmed: Boolean): Boolean = ownerConfirmed

    /** A pairing VIEW from a browser or another app always opens the code-confirm screen. */
    fun mustShowCodeConfirm(fromExternalView: Boolean): Boolean = fromExternalView

    /**
     * request_mic is only honored from Pony's own non-VIEW launch with a
     * process-local nonce. A pairing VIEW from another app never prompts the mic.
     */
    fun honorRequestMic(actionIsView: Boolean, hasValidNonce: Boolean = !actionIsView): Boolean =
        !actionIsView && hasValidNonce

    /** A background VIEW must not rewrite brain / relay prefs. */
    fun applyTemplateFromViewIntent(): Boolean = false

    /**
     * After It matches, Android may bounce the session service and redeliver
     * the inert start intent. That must resume the vault (same keys, same
     * confirmation) instead of minting a new keypair and tearing the socket
     * down. A different pairing token still starts fresh.
     */
    fun shouldResume(saved: SessionSnapshot?, pairingToken: String?, now: Long): Boolean {
        if (saved == null || !saved.resumable(now)) return false
        if (pairingToken != null && !saved.token.equals(pairingToken, ignoreCase = true)) return false
        return true
    }

    /** In-memory session for this token: do not call begin() again. */
    fun alreadyLive(snapshot: SessionSnapshot?, pairingToken: String?, hasKeys: Boolean): Boolean {
        if (!hasKeys || snapshot == null) return false
        return pairingToken == null || snapshot.token.equals(pairingToken, ignoreCase = true)
    }
}
