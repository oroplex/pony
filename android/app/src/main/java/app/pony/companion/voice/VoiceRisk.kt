package app.pony.companion.voice

/** What Pony learned about the control under a tap. */
data class TapTarget(
    val label: String,
    val isPassword: Boolean = false,
    val packageName: String? = null,
    val appLabel: String? = null,
    val viewId: String? = null,
    /** Foreground activity or window class, when accessibility knows it. */
    val activity: String? = null,
    /** Focused window title, when the system exposes one. */
    val windowTitle: String? = null,
    /** Visible labels on the current screen, used to spot checkout / pay UI. */
    val screenText: String? = null,
)

sealed class Verdict {
    data object Allow : Verdict()

    /** Ask the owner first, with an explicit yes. */
    data class Confirm(val prompt: String, val reason: String) : Verdict()

    data class Block(val reason: String) : Verdict()
}

/**
 * Taps that must be spoken back and accepted with an explicit yes: password
 * fields, payments, sending, buying, deleting, calling, posting, and security
 * changes. Label matching still uses the control's own label or accessibility
 * text. On a [MoneyScreens] hit — a known payment app or a checkout heuristic
 * — every tap asks first, including a bare icon or a tap by coordinates.
 */
object SafetyPolicy {
    private data class Rule(val pattern: Regex, val prompt: String, val reason: String)

    private fun words(vararg alternatives: String) =
        Regex("\\b(${alternatives.joinToString("|")})\\b", RegexOption.IGNORE_CASE)

    private val rules = listOf(
        Rule(words("send money", "request money", "send payment", "pay now", "pay", "payment", "checkout", "check out", "confirm payment", "slide to pay", "tap to pay", "complete purchase", "authori[sz]e payment"), "Pay?", "payment"),
        Rule(words("buy", "buy now", "purchase", "place order", "order now", "subscribe", "start subscription", "start trial", "confirm purchase", "rent"), "Buy this?", "purchase"),
        Rule(words("send", "send message", "send now", "send email", "send sms", "reply all"), "Send this?", "send"),
        Rule(words("delete", "erase", "trash", "delete account", "remove account", "wipe", "empty trash"), "Delete this?", "delete"),
        Rule(words("call", "dial", "video call", "voice call", "call back"), "Place this call?", "call"),
        Rule(words("transfer", "wire", "withdraw"), "Transfer this?", "transfer"),
        Rule(words("post", "publish", "tweet", "share publicly", "go live"), "Post this?", "post"),
        Rule(
            Regex(
                "\\b(factory\\s+reset|change\\s+password|set\\s+password|reset\\s+password|screen\\s+lock|encryption|disable\\s+security|remove\\s+lock|turn\\s+off\\s+find\\s+my)\\b",
                RegexOption.IGNORE_CASE,
            ),
            "Change security settings?",
            "security",
        ),
    )

    private val confirmWords = words("confirm", "continue", "next", "submit", "approve", "authori[sz]e", "ok", "done", "agree", "accept")

    private val amount = Regex("([$€£¥₹]\\s?\\d)|(\\d[\\d,.]*\\s?(usd|eur|gbp|dollars?|euros?))", RegexOption.IGNORE_CASE)

    /** Pony's own package, so its own UI can be told apart from the apps it drives. */
    private const val PONY_PACKAGE = "app.pony.companion"

    private fun isOwnApp(packageName: String?): Boolean {
        val pkg = packageName ?: return false
        return pkg == PONY_PACKAGE || pkg.startsWith("$PONY_PACKAGE.")
    }

    fun forTap(target: TapTarget): Verdict {
        if (target.isPassword) {
            return Verdict.Confirm("Tap the password field? Pony still won't read or type into it.", "password_field")
        }
        val label = target.label.trim()
        val money = MoneyScreens.matches(target)
        if (label.isEmpty()) {
            return if (money) moneyTap(target) else Verdict.Allow
        }
        val rule = rules.firstOrNull { it.pattern.containsMatchIn(label) }
        if (rule != null) {
            // Pony's own Ask "Send" arrow isn't a message leaving the phone, so it
            // doesn't get the Send gate. Every other app's Send still asks.
            if (rule.reason == "send" && isOwnApp(target.packageName)) return Verdict.Allow
            val prompt = when (rule.reason) {
                "payment" -> amountIn(label)?.let { "Pay $it?" } ?: rule.prompt
                "send" -> where("Send this in", target) ?: rule.prompt
                else -> rule.prompt
            }
            return Verdict.Confirm(prompt, rule.reason)
        }
        if (money && (confirmWords.containsMatchIn(label) || amount.containsMatchIn(label))) {
            return Verdict.Confirm(where("Confirm this in", target) ?: "Confirm this payment?", "payment_app")
        }
        if (amount.containsMatchIn(label) && confirmWords.containsMatchIn(label)) {
            return Verdict.Confirm("Confirm ${amountIn(label) ?: "this amount"}?", "payment")
        }
        if (money) return moneyTap(target)
        return Verdict.Allow
    }

    /** Every control on a money screen, including an unlabeled icon or a coordinate tap. */
    private fun moneyTap(target: TapTarget): Verdict {
        val known = MoneyScreens.isKnownPackage(target.packageName)
        val prompt = where("Tap this in", target)
            ?: if (known) "Tap this in a payment app?" else "Tap this on a payment screen?"
        return Verdict.Confirm(prompt, if (known) "payment_app" else "money_screen")
    }

    fun forOpenApp(packageName: String, label: String = packageName): Verdict {
        val rule = rules.firstOrNull { it.reason == "security" && it.pattern.containsMatchIn(label) }
        return if (rule != null) Verdict.Confirm(rule.prompt, rule.reason) else Verdict.Allow
    }

    fun forType(passwordFocused: Boolean): Verdict =
        if (passwordFocused) Verdict.Block("password_field") else Verdict.Allow

    private fun where(prefix: String, target: TapTarget): String? =
        target.appLabel?.takeIf { it.isNotBlank() }?.let { "$prefix $it?" }

    private fun amountIn(label: String): String? {
        val match = Regex("[$€£¥₹]\\s?\\d[\\d,]*(\\.\\d{1,2})?").find(label) ?: return null
        return match.value.replace(" ", "")
    }
}

/** Existing entry point for label-only checks. */
object VoiceRisk {
    fun promptFor(action: String, label: String): String? {
        if (action != "tap" && action != "open_app") return null
        val text = label.trim()
        if (text.isEmpty()) return null
        val verdict = if (action == "tap") SafetyPolicy.forTap(TapTarget(text)) else SafetyPolicy.forOpenApp(text, text)
        return (verdict as? Verdict.Confirm)?.prompt
    }
}

object VoiceWords {
    fun isStop(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (normalized.isEmpty()) return false
        return normalized == "stop" ||
            normalized == "stop pony" ||
            normalized == "cancel" ||
            normalized == "never mind" ||
            normalized.startsWith("stop ")
    }

    fun isYes(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized in YES || YES.any { normalized.startsWith("$it ") }
    }

    fun isNo(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized in NO || NO.any { normalized.startsWith("$it ") }
    }

    private val YES = setOf("yes", "yeah", "yep", "ok", "okay", "do it", "confirm", "sure", "go ahead")
    private val NO = setOf("no", "nope", "don't", "do not", "nah", "cancel", "stop")
}
