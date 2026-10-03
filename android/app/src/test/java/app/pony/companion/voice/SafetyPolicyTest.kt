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

    @Test
    fun knownMoneyAppsAskBeforeEveryTap() {
        val venmo = SafetyPolicy.forTap(TapTarget("Friends", packageName = "com.venmo", appLabel = "Venmo"))
        assertTrue(venmo is Verdict.Confirm)
        assertEquals("Tap this in Venmo?", (venmo as Verdict.Confirm).prompt)
        assertEquals("payment_app", venmo.reason)

        val chase = SafetyPolicy.forTap(TapTarget("Accounts", packageName = "com.chase.sig.android", appLabel = "Chase"))
        assertTrue(chase is Verdict.Confirm)
        assertEquals("payment_app", (chase as Verdict.Confirm).reason)
    }

    @Test
    fun heuristicCheckoutScreensAskBeforeEveryTap() {
        val chrome = SafetyPolicy.forTap(
            TapTarget(
                "Home",
                packageName = "com.android.chrome",
                appLabel = "Chrome",
                activity = "CheckoutActivity",
                screenText = "Checkout\nPlace order\n$24.00",
            ),
        )
        assertTrue(chrome is Verdict.Confirm)
        assertEquals("money_screen", (chrome as Verdict.Confirm).reason)

        val byText = SafetyPolicy.forTap(
            TapTarget("Continue shopping", packageName = "com.example.shop", screenText = "Confirm payment"),
        )
        assertTrue(byText is Verdict.Confirm)
    }

    @Test
    fun unlabeledCoordinateTapOnAMoneyScreenStillNeedsConfirmation() {
        val icon = SafetyPolicy.forTap(TapTarget("", packageName = "com.venmo", appLabel = "Venmo"))
        assertTrue(icon is Verdict.Confirm)
        assertEquals("Tap this in Venmo?", (icon as Verdict.Confirm).prompt)

        val coords = SafetyPolicy.forTap(
            TapTarget("", packageName = "com.android.chrome", activity = "PaymentActivity"),
        )
        assertTrue(coords is Verdict.Confirm)
        assertEquals("money_screen", (coords as Verdict.Confirm).reason)
    }

    @Test
    fun aNonMoneyScreenAddsNoExtraConfirmation() {
        assertEquals(Verdict.Allow, SafetyPolicy.forTap(TapTarget("Search", packageName = "com.android.settings")))
        assertEquals(Verdict.Allow, SafetyPolicy.forTap(TapTarget("", packageName = "com.android.calculator2")))
        assertEquals(Verdict.Allow, SafetyPolicy.forTap(TapTarget("2", packageName = "com.android.calculator2")))
        assertEquals(Verdict.Allow, SafetyPolicy.forTap(TapTarget("Continue", packageName = "com.android.settings")))
    }

    @Test
    fun existingLabelMatchingStillWorksOnAndOffMoneyScreens() {
        assertEquals("Pay $24.99?", prompt(TapTarget("Pay $24.99")))
        assertEquals("Send this?", prompt(TapTarget("Send")))
        assertEquals("Buy this?", prompt(TapTarget("Place order")))
        assertEquals("Transfer this?", prompt(TapTarget("Transfer")))
        assertEquals(
            "Send this in WhatsApp?",
            prompt(TapTarget("Send", packageName = "com.whatsapp", appLabel = "WhatsApp")),
        )
        // Label rules still win on a known money app, so Pay keeps its amount prompt.
        assertEquals("Pay $8.00?", prompt(TapTarget("Pay $8.00", packageName = "com.squareup.cash", appLabel = "Cash App")))
    }
}
