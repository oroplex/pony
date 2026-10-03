package app.pony.companion.voice

/**
 * Screens that move money. Add a [KnownApp] row to cover another payment or
 * bank app — this list is the whole catalog, not a starting point that needs
 * a code change elsewhere.
 *
 * A known app matches on [KnownApp.packageName]. When [KnownApp.activities]
 * is empty, every screen in that package counts. When it is set, only those
 * activity or window-title fragments count, so a shopping app can list just
 * its checkout activities.
 *
 * Generic heuristics then catch checkout and pay screens in apps that are
 * not listed: activity or window names such as checkout, payment, pay,
 * transfer, send money; and on-screen text such as Pay, Transfer, Confirm
 * payment, Send, Checkout, Place order.
 */
object MoneyScreens {
    /**
     * One known money-moving app. Keep [packageName] exact. Leave
     * [activities] empty to treat the whole app as a money screen.
     */
    data class KnownApp(
        val packageName: String,
        val label: String,
        val activities: List<String> = emptyList(),
    )

    /**
     * Venmo, PayPal, Cash App, Zelle, Google Pay / Wallet, and major bank
     * apps. Whole-app match (empty [KnownApp.activities]) unless a row
     * names specific checkout screens.
     */
    val KNOWN_APPS: List<KnownApp> = listOf(
        KnownApp("com.venmo", "Venmo"),
        KnownApp("com.paypal.android.p2pmobile", "PayPal"),
        KnownApp("com.squareup.cash", "Cash App"),
        KnownApp("com.zellepay.zelle", "Zelle"),
        KnownApp("com.google.android.apps.walletnfcrel", "Google Wallet"),
        KnownApp("com.google.android.apps.nbu.paisa.user", "Google Pay"),
        KnownApp("com.samsung.android.spay", "Samsung Pay"),
        KnownApp("com.samsung.android.samsungpay.gear", "Samsung Pay"),
        KnownApp("com.revolut.revolut", "Revolut"),
        KnownApp("com.transferwise.android", "Wise"),
        KnownApp("com.coinbase.android", "Coinbase"),
        KnownApp("com.robinhood.android", "Robinhood"),
        KnownApp("com.onedebit.chime", "Chime"),
        KnownApp("com.sofi.mobile", "SoFi"),
        KnownApp("com.chase.sig.android", "Chase"),
        KnownApp("com.infonow.bofa", "Bank of America"),
        KnownApp("com.wf.wellsfargomobile", "Wells Fargo"),
        KnownApp("com.citi.citimobile", "Citi"),
        KnownApp("com.konylabs.capitalone", "Capital One"),
        KnownApp("com.usbank.mobilebanking", "U.S. Bank"),
        KnownApp("com.pnc.ecommerce.mobile", "PNC"),
        KnownApp("com.td", "TD Bank"),
        KnownApp("com.tdbank", "TD Bank"),
        KnownApp("com.americanexpress.android.acctsvcs.us", "American Express"),
        KnownApp("com.discoverfinancial.mobile", "Discover"),
        KnownApp("com.ally.MobileBanking", "Ally"),
        KnownApp("com.nfcu", "Navy Federal"),
        KnownApp("com.usaa.mobile.android.usaa", "USAA"),
        KnownApp("com.schwab.mobile", "Schwab"),
        KnownApp("com.fidelity.android", "Fidelity"),
        KnownApp("com.regions.mobbanking", "Regions"),
        KnownApp("com.huntington.m", "Huntington"),
        KnownApp("com.citizensbank.androidmobile", "Citizens"),
        KnownApp("com.key.android", "KeyBank"),
        KnownApp("com.sovereign.santander", "Santander"),
        KnownApp("com.bbt.myfi", "Truist"),
        KnownApp("com.mtb.mbanking", "M&T"),
        KnownApp("com.barclays.android.barclaysmobilebanking", "Barclays"),
        KnownApp("uk.co.hsbc.hsbcukmobilebanking", "HSBC"),
        KnownApp("com.rbs.mobile.android.natwest", "NatWest"),
        KnownApp("com.commbank.netbank", "CommBank"),
        KnownApp("org.westpac.bank", "Westpac"),
        KnownApp("com.anz.android.gomoney", "ANZ"),
        // Shopping apps: only the checkout / pay screens, not the catalog.
        KnownApp(
            "com.amazon.mShop.android.shopping",
            "Amazon",
            activities = listOf("Checkout", "PaySelect", "PlaceOrder", "Payment"),
        ),
    )

    /** Activity or window names that mean this screen moves money. */
    val ACTIVITY_HINTS: List<String> = listOf(
        "checkout",
        "payment",
        "pay",
        "transfer",
        "send money",
        "purchase",
        "place order",
        "billing",
        "confirm payment",
    )

    /**
     * On-screen phrases that mark a money screen, matched as whole words so
     * "Display" does not count as "Pay".
     */
    val SCREEN_TEXT_HINTS: List<String> = listOf(
        "confirm payment",
        "place order",
        "send money",
        "checkout",
        "transfer",
        "pay",
        "send",
    )

    private const val PONY_PACKAGE = "app.pony.companion"

    private val knownByPackage: Map<String, KnownApp> = KNOWN_APPS.associateBy { it.packageName }

    val knownPackages: Set<String> = knownByPackage.keys

    fun knownApp(packageName: String?): KnownApp? {
        val pkg = packageName ?: return null
        return knownByPackage[pkg]
    }

    fun isKnownPackage(packageName: String?): Boolean = knownApp(packageName) != null

    fun matches(target: TapTarget): Boolean = matches(
        packageName = target.packageName,
        activity = target.activity,
        windowTitle = target.windowTitle,
        screenText = target.screenText,
    )

    fun matches(
        packageName: String? = null,
        activity: String? = null,
        windowTitle: String? = null,
        screenText: String? = null,
    ): Boolean {
        if (isOwnApp(packageName)) return false
        if (knownAppMatches(packageName, activity, windowTitle)) return true
        if (looksLikeMoneyName(activity) || looksLikeMoneyName(windowTitle)) return true
        if (looksLikeMoneyText(screenText)) return true
        return false
    }

    private fun knownAppMatches(packageName: String?, activity: String?, windowTitle: String?): Boolean {
        val app = knownApp(packageName) ?: return false
        if (app.activities.isEmpty()) return true
        val haystack = listOfNotNull(activity, windowTitle)
        if (haystack.isEmpty()) return false
        return app.activities.any { fragment ->
            haystack.any { name -> name.contains(fragment, ignoreCase = true) }
        }
    }

    fun looksLikeMoneyName(name: String?): Boolean {
        val tokens = tokenize(name) ?: return false
        return ACTIVITY_HINTS.any { hint -> word(hint).containsMatchIn(tokens) }
    }

    fun looksLikeMoneyText(text: String?): Boolean {
        val body = text?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return SCREEN_TEXT_HINTS.any { hint -> word(hint).containsMatchIn(body) }
    }

    /**
     * Splits a class or window name into words so `GooglePayActivity` becomes
     * `google pay activity` and `DisplayActivity` does not contain `pay`.
     */
    fun tokenize(name: String?): String? {
        val raw = name?.substringAfterLast('.')?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return raw
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("[^A-Za-z0-9]+"), " ")
            .lowercase()
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun word(phrase: String) = Regex("\\b${Regex.escape(phrase)}\\b", RegexOption.IGNORE_CASE)

    private fun isOwnApp(packageName: String?): Boolean {
        val pkg = packageName ?: return false
        return pkg == PONY_PACKAGE || pkg.startsWith("$PONY_PACKAGE.")
    }
}
