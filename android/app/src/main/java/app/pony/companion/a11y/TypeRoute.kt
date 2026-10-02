package app.pony.companion.a11y

/**
 * Order of attempts for `type`. The Pony keyboard is first when it already
 * owns the field. Clipboard paste is included only when the owner opted in.
 * Password fields never reach paste or key events.
 */
object TypeRoute {
    const val IME = "ime"
    const val SET_TEXT = "set_text"
    const val ASK_IME = "ask_ime"
    const val PASTE = "paste"
    const val KEY_EVENTS = "key_events"
    const val PASSWORD = "password"

    fun order(
        password: Boolean,
        imeActive: Boolean,
        imeEnabled: Boolean,
        clipboardOptIn: Boolean,
    ): List<String> {
        if (password) return listOf(PASSWORD)
        val steps = ArrayList<String>(5)
        if (imeActive) steps += IME
        steps += SET_TEXT
        if (imeEnabled && !imeActive) steps += ASK_IME
        if (clipboardOptIn) steps += PASTE
        steps += KEY_EVENTS
        return steps
    }

    fun failure(imeEnabled: Boolean, askedToSwitch: Boolean): String = when {
        !imeEnabled -> "ime_disabled"
        askedToSwitch -> "ime_required"
        else -> "type_unsupported"
    }
}
