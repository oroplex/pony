package app.pony.companion.voice

import java.util.regex.Pattern

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
    /** Accessibility content description, when it is not already the label. */
    val contentDescription: String? = null,
    /** Android class name of the node, for icon-only ImageButton heuristics. */
    val className: String? = null,
) {
    fun haystack(): String = listOfNotNull(label, contentDescription, viewId).joinToString(" ")
}

sealed class Verdict {
    data object Allow : Verdict()

    /** Ask the owner first, with an explicit yes. */
    data class Confirm(val prompt: String, val reason: String) : Verdict()

    data class Block(val reason: String) : Verdict()
}

/**
 * Acting verbs that must be spoken back and accepted with an explicit yes:
 * password fields, payments, sending, buying, deleting, calling, posting, and
 * security changes. Label matching uses the control's label, content
 * description, or view id, in English and the common locales. On a
 * [MoneyScreens] hit every acting verb asks first, including a bare icon.
 */
object SafetyPolicy {
    private data class Rule(val pattern: Regex, val cjk: List<String>, val prompt: String, val reason: String)

    private fun words(vararg alternatives: String): Regex =
        Pattern.compile(
            "\\b(${alternatives.joinToString("|")})\\b",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.UNICODE_CHARACTER_CLASS,
        ).toRegex()

    /**
     * English plus es, de, fr, pt, he, hi, ar, zh, ja (and ko as a backstop).
     * Latin / Hebrew / Arabic / Devanagari use word boundaries; CJK is a
     * substring list because `\b` does not split those scripts.
     */
    private val rules = listOf(
        Rule(
            words(
                "send money", "request money", "send payment", "pay now", "pay", "payment", "checkout", "check out",
                "confirm payment", "slide to pay", "tap to pay", "complete purchase", "authori[sz]e payment",
                "pagar", "pago", "pagar agora", "paiement", "payer", "bezahlen", "zahlung", "zahlen",
                "שלם", "תשלום", "भुगतान", "चुकाना", "دفع", "سداد",
            ),
            listOf("支付", "付款", "结账", "結帳", "支払", "支払い", "決済", "결제", "지불"),
            "Pay?",
            "payment",
        ),
        Rule(
            words(
                "buy", "buy now", "purchase", "place order", "order now", "subscribe", "start subscription",
                "start trial", "confirm purchase", "rent",
                "comprar", "acheter", "kaufen", "bestellen",
                "קנה", "खरीदें", "شراء",
            ),
            listOf("购买", "購買", "下单", "下單", "購入", "買う", "구매"),
            "Buy this?",
            "purchase",
        ),
        Rule(
            words(
                "send", "send message", "send now", "send email", "send sms", "reply all",
                "enviar", "envío", "envio", "envoyer", "senden", "absenden",
                "שלח", "שליחה", "भेजें", "भेजो", "إرسال", "ارسل",
            ),
            listOf("发送", "傳送", "发送", "送信", "送る", "보내기", "전송"),
            "Send this?",
            "send",
        ),
        Rule(
            words(
                "delete", "erase", "trash", "delete account", "remove account", "wipe", "empty trash",
                "eliminar", "borrar", "excluir", "apagar", "supprimer", "löschen", "entfernen",
                "מחק", "हटाएं", "मिटाएँ", "حذف",
            ),
            listOf("删除", "刪除", "削除", "消す", "삭제"),
            "Delete this?",
            "delete",
        ),
        Rule(
            words(
                "call", "dial", "video call", "voice call", "call back",
                "llamar", "llamada", "ligar", "appeler", "appel", "anrufen", "anruf",
                "התקשר", "שיחה", "कॉल", "कॉल करें", "اتصال", "مكالمة",
            ),
            listOf("呼叫", "拨打", "撥打", "通話", "発信", "전화"),
            "Place this call?",
            "call",
        ),
        Rule(
            words("transfer", "wire", "withdraw", "transferir", "überweisen", "virer", "העבר", "स्थानांतरण", "تحويل"),
            listOf("转账", "轉帳", "振込", "이체"),
            "Transfer this?",
            "transfer",
        ),
        Rule(
            words(
                "post", "publish", "tweet", "share publicly", "go live",
                "publicar", "publier", "veröffentlichen", "פרסם", "पोस्ट", "نشر",
            ),
            listOf("发布", "發佈", "投稿", "게시"),
            "Post this?",
            "post",
        ),
        Rule(
            Regex(
                "\\b(factory\\s+reset|change\\s+password|set\\s+password|reset\\s+password|screen\\s+lock|encryption|disable\\s+security|remove\\s+lock|turn\\s+off\\s+find\\s+my|restablecer\\s+de\\s+fábrica|ändern\\s+des\\s+passworts|changer\\s+le\\s+mot\\s+de\\s+passe)\\b",
                RegexOption.IGNORE_CASE,
            ),
            listOf("恢复出厂", "恢復出廠", "工場出荷", "비밀번호 변경", "更改密码", "變更密碼"),
            "Change security settings?",
            "security",
        ),
    )

    private val confirmWords = words(
        "confirm", "continue", "next", "submit", "approve", "authori[sz]e", "ok", "done", "agree", "accept",
        "confirmar", "continuar", "siguiente", "aceptar",
        "bestätigen", "weiter", "akzeptieren",
        "confirmer", "continuer", "suivant", "accepter",
        "confirmar", "continuar", "próximo", "aceitar",
        "אישור", "המשך", "אישור",
        "पुष्टि", "जारी",
        "تأكيد", "متابعة",
    )
    private val confirmCjk = listOf("确认", "確認", "继续", "繼續", "下一步", "提交", "同意", "確定", "次へ", "확인")

    private val amount = Regex("([$€£¥₹]\\s?\\d)|(\\d[\\d,.]*\\s?(usd|eur|gbp|dollars?|euros?))", RegexOption.IGNORE_CASE)

    private val submitKeys = setOf("send", "enter", "go", "search")

    private val iconId = Regex(
        "(^|[_./])(send|pay|delete|call|dial|post|publish|buy|purchase|trash|compose_send|ic_send|btn_send)([_.]|$)",
        RegexOption.IGNORE_CASE,
    )

    private val iconClass = Regex("ImageButton|ImageView|AppCompatImageButton", RegexOption.IGNORE_CASE)

    private val messaging = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.instagram.android",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.aweme",
        "com.slack",
        "com.discord",
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.yahoo.mobile.client.android.mail",
        "com.google.android.talk",
        "com.google.android.apps.dynamite",
        "com.viber.voip",
        "jp.naver.line.android",
        "com.kakao.talk",
        "com.tencent.mm",
        "org.thoughtcrime.securesms",
    )

    /** Pony's own package, so its own UI can be told apart from the apps it drives. */
    private const val PONY_PACKAGE = "app.pony.companion"

    fun isOwnApp(packageName: String?): Boolean {
        val pkg = packageName ?: return false
        return pkg == PONY_PACKAGE || pkg.startsWith("$PONY_PACKAGE.")
    }

    fun isMessagingApp(packageName: String?): Boolean {
        val pkg = packageName ?: return false
        return messaging.any { pkg == it || pkg.startsWith("$it.") }
    }

    fun forAction(op: String, target: TapTarget, key: String? = null): Verdict {
        return when (op) {
            "tap", "long_press" -> forTap(target)
            "swipe", "drag", "pinch" -> forGesture(target, op)
            "press" -> forPress(key, target)
            "open_app" -> forOpenApp(target.packageName ?: "", target.appLabel ?: target.packageName ?: "")
            "type" -> {
                if (target.isPassword) forType(true)
                else if (MoneyScreens.matches(target)) moneyTap(target, verb = "Type")
                else Verdict.Allow
            }
            else -> Verdict.Allow
        }
    }

    fun forTap(target: TapTarget): Verdict {
        if (target.isPassword) {
            return Verdict.Confirm("Tap the password field? Pony still won't read or type into it.", "password_field")
        }
        val money = MoneyScreens.matches(target)
        val matched = matchRules(target.haystack())
        if (matched != null) {
            if (matched.reason == "send" && isOwnApp(target.packageName)) return Verdict.Allow
            return confirmRule(matched, target)
        }
        if (unlabeledRisky(target)) {
            return if (money) moneyTap(target) else Verdict.Confirm(where("Send this in", target) ?: "Send this?", "send")
        }
        val label = target.label.trim()
        if (label.isEmpty() && target.contentDescription.isNullOrBlank()) {
            return if (money) moneyTap(target) else Verdict.Allow
        }
        if (money && (matchesConfirm(label) || amount.containsMatchIn(label))) {
            return Verdict.Confirm(where("Confirm this in", target) ?: "Confirm this payment?", "payment_app")
        }
        if (amount.containsMatchIn(label) && matchesConfirm(label)) {
            return Verdict.Confirm("Confirm ${amountIn(label) ?: "this amount"}?", "payment")
        }
        if (money) return moneyTap(target)
        return Verdict.Allow
    }

    fun forGesture(target: TapTarget, op: String): Verdict {
        if (MoneyScreens.matches(target)) return moneyTap(target, verb = gestureVerb(op))
        val matched = matchRules(target.haystack() + " " + (target.screenText ?: ""))
        if (matched != null && matched.reason in setOf("payment", "purchase", "send", "delete", "call", "transfer", "post")) {
            return confirmRule(matched, target)
        }
        return Verdict.Allow
    }

    fun forPress(key: String?, target: TapTarget): Verdict {
        val money = MoneyScreens.matches(target)
        val normalized = key?.trim()?.lowercase().orEmpty()
        if (money) {
            return Verdict.Confirm(where("Do this in", target) ?: "Continue in a payment app?", "payment_app")
        }
        // Risky labels prompt on every acting verb, including press / IME keys.
        val matched = matchRules(listOf(target.haystack(), key).filterNotNull().joinToString(" "))
        if (matched != null) {
            if (matched.reason == "send" && isOwnApp(target.packageName)) return Verdict.Allow
            return confirmRule(matched, target)
        }
        if (normalized in submitKeys) {
            if (isOwnApp(target.packageName)) return Verdict.Allow
            if (isMessagingApp(target.packageName) || matchRules(target.haystack())?.reason == "send") {
                return Verdict.Confirm(where("Send this in", target) ?: "Send this?", "send")
            }
            if (normalized == "send") {
                return Verdict.Confirm(where("Send this in", target) ?: "Send this?", "send")
            }
        }
        return Verdict.Allow
    }

    /** Every control on a money screen, including an unlabeled icon or a coordinate tap. */
    private fun moneyTap(target: TapTarget, verb: String = "Tap"): Verdict {
        val known = MoneyScreens.isKnownPackage(target.packageName)
        val prompt = where("$verb this in", target)
            ?: if (known) "$verb this in a payment app?" else "$verb this on a payment screen?"
        return Verdict.Confirm(prompt, if (known) "payment_app" else "money_screen")
    }

    fun forOpenApp(packageName: String, label: String = packageName): Verdict {
        if (isOwnApp(packageName)) return Verdict.Block("own_app")
        if (MoneyScreens.isKnownPackage(packageName)) {
            val name = MoneyScreens.knownApp(packageName)?.label ?: label
            return Verdict.Confirm("Open $name? This is a payment app.", "payment_app")
        }
        val rule = rules.firstOrNull { it.reason == "security" && matchesRule(it, label) }
        return if (rule != null) Verdict.Confirm(rule.prompt, rule.reason) else Verdict.Allow
    }

    fun forType(passwordFocused: Boolean): Verdict =
        if (passwordFocused) Verdict.Block("password_field") else Verdict.Allow

    private fun unlabeledRisky(target: TapTarget): Boolean {
        if (isOwnApp(target.packageName)) return false
        val empty = target.label.isBlank()
        if (!empty) return false
        val id = target.viewId.orEmpty()
        if (iconId.containsMatchIn(id)) return true
        val desc = target.contentDescription.orEmpty()
        if (desc.isNotBlank() && matchRules(desc) != null) return true
        if (isMessagingApp(target.packageName) && iconClass.containsMatchIn(target.className.orEmpty())) return true
        return false
    }

    private fun matchRules(text: String): Rule? {
        val body = text.trim()
        if (body.isEmpty()) return null
        return rules.firstOrNull { matchesRule(it, body) }
    }

    private fun matchesRule(rule: Rule, text: String): Boolean {
        if (rule.pattern.containsMatchIn(text)) return true
        return rule.cjk.any { text.contains(it) }
    }

    private fun matchesConfirm(text: String): Boolean =
        confirmWords.containsMatchIn(text) || confirmCjk.any { text.contains(it) }

    private fun confirmRule(rule: Rule, target: TapTarget): Verdict {
        val prompt = when (rule.reason) {
            "payment" -> amountIn(target.haystack())?.let { "Pay $it?" } ?: rule.prompt
            "send" -> where("Send this in", target) ?: rule.prompt
            else -> rule.prompt
        }
        return Verdict.Confirm(prompt, rule.reason)
    }

    private fun gestureVerb(op: String): String = when (op) {
        "drag" -> "Drag"
        "pinch" -> "Pinch"
        else -> "Swipe"
    }

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
        if (action !in setOf("tap", "open_app", "swipe", "drag", "long_press", "press", "key")) return null
        val text = label.trim()
        val verdict = when (action) {
            "open_app" -> SafetyPolicy.forOpenApp(text, text)
            "swipe", "drag" -> SafetyPolicy.forGesture(TapTarget(text), action)
            "press", "key" -> SafetyPolicy.forPress(text, TapTarget(text))
            else -> SafetyPolicy.forTap(TapTarget(text))
        }
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
