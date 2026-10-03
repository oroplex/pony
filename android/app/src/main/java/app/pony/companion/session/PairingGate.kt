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
}
