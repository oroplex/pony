package app.pony.companion.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyScreensTest {
    @Test
    fun knownPaymentAndBankAppsMatchByPackage() {
        for (pkg in listOf(
            "com.venmo",
            "com.paypal.android.p2pmobile",
            "com.squareup.cash",
            "com.zellepay.zelle",
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.nbu.paisa.user",
            "com.chase.sig.android",
            "com.infonow.bofa",
            "com.wf.wellsfargomobile",
            "com.citi.citimobile",
            "com.konylabs.capitalone",
        )) {
            assertTrue(pkg, MoneyScreens.matches(packageName = pkg))
            assertTrue(pkg, MoneyScreens.isKnownPackage(pkg))
        }
    }

    @Test
    fun activityFilteredAppsNeedTheNamedScreen() {
        assertFalse(MoneyScreens.matches(packageName = "com.amazon.mShop.android.shopping"))
        assertTrue(
            MoneyScreens.matches(
                packageName = "com.amazon.mShop.android.shopping",
                activity = "com.amazon.mShop.checkout.CheckoutActivity",
            ),
        )
        assertTrue(
            MoneyScreens.matches(
                packageName = "com.amazon.mShop.android.shopping",
                windowTitle = "PlaceOrder",
            ),
        )
    }

    @Test
    fun heuristicsCatchCheckoutNamesAndOnScreenPayText() {
        assertTrue(MoneyScreens.looksLikeMoneyName("CheckoutActivity"))
        assertTrue(MoneyScreens.looksLikeMoneyName("com.shop.pay.SendMoneyActivity"))
        assertTrue(MoneyScreens.looksLikeMoneyName("TransferFunds"))
        assertTrue(MoneyScreens.matches(windowTitle = "Confirm payment"))
        assertTrue(MoneyScreens.looksLikeMoneyText("Subtotal $12\nPlace order\nCancel"))
        assertTrue(MoneyScreens.looksLikeMoneyText("Pay"))
        assertTrue(MoneyScreens.looksLikeMoneyText("Transfer"))
        assertTrue(MoneyScreens.looksLikeMoneyText("Send"))
        assertTrue(MoneyScreens.looksLikeMoneyText("Checkout"))
        assertTrue(MoneyScreens.looksLikeMoneyText("Confirm payment"))
        assertTrue(MoneyScreens.matches(activity = "PaymentSheet", packageName = "com.android.chrome"))
    }

    @Test
    fun displayAndSettingsAreNotMoneyScreens() {
        assertFalse(MoneyScreens.looksLikeMoneyName("DisplayActivity"))
        assertFalse(MoneyScreens.looksLikeMoneyName("com.android.settings.DisplaySettings"))
        assertFalse(MoneyScreens.looksLikeMoneyText("Search\nDisplay\nRecall"))
        assertFalse(MoneyScreens.matches(packageName = "com.android.settings", screenText = "Wi‑Fi\nDisplay\nSearch"))
        assertFalse(MoneyScreens.matches(packageName = "com.android.calculator2", activity = "Calculator"))
        assertFalse(MoneyScreens.isKnownPackage("com.android.settings"))
    }

    @Test
    fun ponyOwnUiIsNeverAMoneyScreen() {
        assertFalse(MoneyScreens.matches(packageName = "app.pony.companion", screenText = "Send\nPay"))
        assertFalse(MoneyScreens.matches(packageName = "app.pony.companion.debug", activity = "PayActivity"))
    }

    @Test
    fun tokenizeSplitsCamelCaseSoPayIsAWordAndDisplayIsNot() {
        assertEquals("google pay activity", MoneyScreens.tokenize("GooglePayActivity"))
        assertEquals("display activity", MoneyScreens.tokenize("DisplayActivity"))
        assertEquals("send money", MoneyScreens.tokenize("SendMoney"))
    }
}
