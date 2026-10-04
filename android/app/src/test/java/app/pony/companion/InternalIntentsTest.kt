package app.pony.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalIntentsTest {
    @Test
    fun micNonceIsSingleUseAndRejectedWhenMissing() {
        assertFalse(InternalIntents.consumeMicNonce(null))
        assertFalse(InternalIntents.consumeMicNonce("forged"))
        val nonce = InternalIntents.issueMicNonce()
        assertTrue(InternalIntents.consumeMicNonce(nonce))
        assertFalse(InternalIntents.consumeMicNonce(nonce))
    }
}
