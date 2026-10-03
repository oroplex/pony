package app.pony.companion.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExpiryTest {
    @Test
    fun confirmWaitIsLongerThanThePhonePrompt() {
        assertTrue(ActionExpiry.OWNER_CLIENT_WAIT_MS > ActionExpiry.OWNER_PROMPT_MS)
        assertEquals(ActionExpiry.OWNER_CLIENT_WAIT_MS, ActionExpiry.ttlMs("confirm"))
        assertEquals(ActionExpiry.OWNER_CLIENT_WAIT_MS, ActionExpiry.ttlMs("ask_user"))
        assertEquals(ActionExpiry.DEFAULT_TTL_MS, ActionExpiry.ttlMs("tap"))
        assertEquals(0L, ActionExpiry.ttlMs("wait_for_request"))
    }

    @Test
    fun aTapQueuedForMinutesIsExpired() {
        val issued = 1_000L
        assertTrue(ActionExpiry.expired(issued, 15_000, issued + 316_000, 15_000))
        assertFalse(ActionExpiry.expired(issued, 15_000, issued + 14_000, 15_000))
    }

    @Test
    fun aZeroTtlNeverExpiresOnItsOwn() {
        assertFalse(ActionExpiry.expired(1_000, 0, 1_000_000, 0))
        assertFalse(ActionExpiry.expired(null, 15_000, 50_000, 15_000))
    }

    @Test
    fun receiveTimeIsUsedWhenTheClientStampIsMissingAndASkewedStampIsRejected() {
        assertEquals(9_000L, ActionExpiry.effectiveIssuedAt(null, 9_000))
        assertEquals(8_500L, ActionExpiry.effectiveIssuedAt(8_500, 9_000))
        assertTrue(ActionExpiry.resolveIssuedAt(9_000 - 200_000, 9_000) is ActionExpiry.IssuedAt.Skewed)
    }

    @Test
    fun aTapIsRefusedWhenTheNamedScreenIsGone() {
        assertTrue(ActionExpiry.screenChanged("com.google.android.keep", "com.android.settings"))
        assertFalse(ActionExpiry.screenChanged("com.android.settings", "com.android.settings"))
        assertFalse(ActionExpiry.screenChanged(null, "com.android.settings"))
        assertFalse(ActionExpiry.screenChanged("com.android.settings", null))
        assertFalse(ActionExpiry.screenChanged("", "com.android.settings"))
    }
}
