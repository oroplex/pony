package app.pony.companion.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingGateTest {
    @Test
    fun nothingStartsUntilTheOwnerConfirmsTheCode() {
        assertFalse(PairingGate.mayCapture(ownerConfirmed = false))
        assertFalse(PairingGate.mayAct(ownerConfirmed = false))
        assertTrue(PairingGate.mayCapture(ownerConfirmed = true))
        assertTrue(PairingGate.mayAct(ownerConfirmed = true))
    }

    @Test
    fun anExternalPairingLinkAlwaysShowsTheCodeConfirmScreen() {
        assertTrue(PairingGate.mustShowCodeConfirm(fromExternalView = true))
        assertFalse(PairingGate.applyTemplateFromViewIntent())
        assertFalse(PairingGate.honorRequestMic(actionIsView = true))
        assertFalse(PairingGate.honorRequestMic(actionIsView = true, hasValidNonce = true))
        assertFalse(PairingGate.honorRequestMic(actionIsView = false, hasValidNonce = false))
        assertTrue(PairingGate.honorRequestMic(actionIsView = false, hasValidNonce = true))
    }

    @Test
    fun aServiceRestartAfterConfirmResumesTheSamePairing() {
        val saved = SessionSnapshot(
            relay = "wss://relay.example",
            token = "ab".repeat(32),
            botPk = "bot",
            phonePrivateKey = "sk",
            phonePublicKey = "pk",
            clientName = "Grok Bot",
            startedAt = 1_000,
            endsAt = 1_000 + 30 * 60_000,
            safetyCode = "505-817",
            ownerConfirmed = true,
            protocolVersion = 2,
            sendSeq = 1,
            recvSeq = 1,
        )
        assertTrue(PairingGate.shouldResume(saved, saved.token, now = 2_000))
        assertTrue(PairingGate.shouldResume(saved, saved.token.uppercase(), now = 2_000))
        assertTrue(PairingGate.alreadyLive(saved, saved.token, hasKeys = true))
        assertFalse(PairingGate.alreadyLive(saved, saved.token, hasKeys = false))
        assertFalse(PairingGate.shouldResume(saved, "cd".repeat(32), now = 2_000))
        assertFalse(PairingGate.shouldResume(null, saved.token, now = 2_000))
        assertFalse(PairingGate.shouldResume(saved, saved.token, now = saved.endsAt!! + 1))
        assertTrue(PairingGate.shouldResume(saved, pairingToken = null, now = 2_000))
    }
}
