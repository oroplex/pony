package app.pony.companion.a11y

/**
 * How `type` writes into a focused field. The tool replaces the field's contents:
 * the service selects the whole field first, then commits through the Pony keyboard
 * ([METHOD_IME]), sets the full value with ACTION_SET_TEXT ([METHOD_SET_TEXT]),
 * pastes over the selection ([METHOD_PASTE], opt-in), or overwrites with key events
 * ([METHOD_KEY_EVENTS]) as a last resort.
 *
 * [fieldValue] and [compose] remain the cursor-aware helpers for reading a field's
 * real text (dropping a hint or placeholder) and inserting at a known selection.
 */
object TextEntry {
    const val METHOD_IME = "ime"
    const val METHOD_SET_TEXT = "set_text"
    const val METHOD_PASTE = "paste"
    const val METHOD_KEY_EVENTS = "key_events"
    const val KEY_EVENT_WARNING =
        "Typed with key events because the field rejected the Pony keyboard and set-text. Check the phone before trusting this text."

    /** Drop placeholder text so an empty field does not become "hint" + insert. */
    fun fieldValue(text: String?, hint: String?, showingHint: Boolean): String? {
        if (showingHint) return null
        if (!hint.isNullOrEmpty() && text == hint) return null
        return text
    }

    fun compose(existing: String?, selectionStart: Int, selectionEnd: Int, insert: String): String {
        val base = existing ?: ""
        if (selectionStart < 0 || selectionEnd < 0 ||
            selectionStart > base.length || selectionEnd > base.length
        ) {
            return base + insert
        }
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        return base.substring(0, start) + insert + base.substring(end)
    }

    fun isPasswordField(isPassword: Boolean, inputType: Int): Boolean {
        if (isPassword) return true
        val klass = inputType and TYPE_MASK_CLASS
        val variation = inputType and TYPE_MASK_VARIATION
        if (klass == TYPE_CLASS_TEXT && variation in TEXT_PASSWORD_VARIATIONS) return true
        if (klass == TYPE_CLASS_NUMBER && variation == TYPE_NUMBER_VARIATION_PASSWORD) return true
        return false
    }

    private const val TYPE_MASK_CLASS = 0x0000000f
    private const val TYPE_MASK_VARIATION = 0x00000ff0
    private const val TYPE_CLASS_TEXT = 0x00000001
    private const val TYPE_CLASS_NUMBER = 0x00000002
    private const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
    private const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    private const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
    private const val TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010

    private val TEXT_PASSWORD_VARIATIONS = setOf(
        TYPE_TEXT_VARIATION_PASSWORD,
        TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        TYPE_TEXT_VARIATION_WEB_PASSWORD,
    )
}
