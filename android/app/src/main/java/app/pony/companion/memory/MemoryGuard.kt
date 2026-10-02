package app.pony.companion.memory

/**
 * Keeps secrets out of memory. Pony stores preferences and facts, never
 * passwords, one-time codes, PINs, card or CVV numbers, or API keys — detected
 * by what the key is called and by the shape of the value. Pure, so the rules
 * are unit tested. When in doubt it refuses: a false "won't remember" is safe,
 * a stored secret is not.
 */
object MemoryGuard {
    data class Decision(val allowed: Boolean, val reason: String? = null)

    private val secretKeyHints = listOf(
        "password", "passcode", "passphrase", "pin", "otp", "one-time", "one time",
        "2fa", "mfa", "cvv", "cvc", "security code", "verification code", "login code",
        "card number", "credit card", "debit card", "ssn", "social security",
        "secret", "api key", "apikey", "api_key", "private key", "access token", "auth token",
    )

    private val tokenPrefixes = listOf("sk-", "sk_", "pk-", "rk_", "ghp_", "xoxb-", "xoxp-", "aiza", "bearer ")

    fun check(key: String, value: String): Decision {
        val k = key.trim().lowercase()
        val v = value.trim()
        if (k.isEmpty() || v.isEmpty()) return Decision(false, "nothing to remember")
        if (secretKeyHints.any { k.contains(it) }) return Decision(false, "that looks like a secret")
        if (looksLikeDigits(v)) return Decision(false, "that looks like a code or card number")
        if (looksLikeToken(v)) return Decision(false, "that looks like a key or token")
        return Decision(true)
    }

    /** A bare run of 3–19 digits (CVV, PIN, OTP, card), even with spaces or dashes. */
    private fun looksLikeDigits(value: String): Boolean {
        val bare = value.filter { !it.isWhitespace() && it != '-' }
        return bare.isNotEmpty() && bare.all { it.isDigit() } && bare.length in 3..19
    }

    private fun looksLikeToken(value: String): Boolean {
        val lower = value.lowercase()
        if (tokenPrefixes.any { lower.startsWith(it) }) return true
        return value.none { it.isWhitespace() } &&
            value.length >= 20 &&
            value.any { it.isLetter() } &&
            value.any { it.isDigit() }
    }
}
