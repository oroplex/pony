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
}
