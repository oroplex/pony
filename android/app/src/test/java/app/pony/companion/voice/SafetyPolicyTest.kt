package app.pony.companion.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPolicyTest {
    private fun prompt(target: TapTarget): String? = (SafetyPolicy.forTap(target) as? Verdict.Confirm)?.prompt

    @Test
    fun passwordFieldTapsNeedAYesAndTypingIsBlocked() {
        val verdict = SafetyPolicy.forTap(TapTarget("", isPassword = true, packageName = "com.chase.sig.android"))
        assertTrue(verdict is Verdict.Confirm)
        assertEquals("password_field", (verdict as Verdict.Confirm).reason)
        assertEquals(Verdict.Block("password_field"), SafetyPolicy.forType(passwordFocused = true))
        assertEquals(Verdict.Allow, SafetyPolicy.forType(passwordFocused = false))
    }

    @Test
    fun paymentsNameTheAmount() {
        assertEquals("Pay $24.99?", prompt(TapTarget("Pay $24.99")))
        assertEquals("Pay?", prompt(TapTarget("Checkout")))
        assertEquals("Pay?", prompt(TapTarget("Send money")))
        assertEquals("Buy this?", prompt(TapTarget("Subscribe")))
        assertEquals("Confirm $120?", prompt(TapTarget("Confirm $120")))
    }

    @Test
    fun sendNamesTheAppWhenKnown() {
        assertEquals("Send this in WhatsApp?", prompt(TapTarget("Send", packageName = "com.whatsapp", appLabel = "WhatsApp")))
        assertEquals("Send this?", prompt(TapTarget("Send")))
        assertEquals("Post this?", prompt(TapTarget("Post")))
    }

    @Test
    fun ponysOwnSendArrowIsNotGatedButOtherAppsStillAsk() {
        // Pony's own Ask "Send" arrow, on the release and the debug build.
        assertEquals(Verdict.Allow, SafetyPolicy.forTap(TapTarget("Send", packageName = "app.pony.companion", appLabel = "Pony")))
        assertEquals(Verdict.Allow, SafetyPolicy.forTap(TapTarget("Send", packageName = "app.pony.companion.debug", appLabel = "Pony")))
        // The exemption is only the Send rule, and only for Pony's own package.
        assertEquals("Send this in Messages?", prompt(TapTarget("Send", packageName = "com.google.android.apps.messaging", appLabel = "Messages")))
        assertEquals("Send this?", prompt(TapTarget("Send")))
    }

    @Test
    fun paymentAppsConfirmTheirContinueButtons() {
        assertEquals(
            "Confirm this in Cash App?",
            prompt(TapTarget("Continue", packageName = "com.squareup.cash", appLabel = "Cash App")),
        )
        assertEquals(Verdict.Allow, SafetyPolicy.forTap(TapTarget("Continue", packageName = "com.android.settings")))
    }

    @Test
    fun ordinaryControlsPass() {
        for (label in listOf("2", "+", "=", "Recall", "Display", "Search", "Back", "Settings", "What are you looking for?")) {
            assertEquals(label, Verdict.Allow, SafetyPolicy.forTap(TapTarget(label)))
        }
    }
}
