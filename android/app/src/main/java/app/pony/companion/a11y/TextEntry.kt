package app.pony.companion.a11y

enum class TypeMode { INSERT, REPLACE, APPEND }

/**
 * How `type` writes into a focused field. Default is [TypeMode.REPLACE]:
 * overwrite the whole field so the agent cannot append by accident.
 * [TypeMode.INSERT] honors the caret; [TypeMode.APPEND] adds at the end.
 *
 * When the Pony keyboard is not the active IME, ACTION_SET_TEXT uses
 * [targetValue] so it does not wipe the field. [fieldValue] drops a hint.
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

    fun parseMode(mode: String?, appendFlag: Boolean): TypeMode = when {
        appendFlag || mode.equals("append", ignoreCase = true) -> TypeMode.APPEND
        mode.equals("insert", ignoreCase = true) -> TypeMode.INSERT
        else -> TypeMode.REPLACE
    }

    fun wireName(mode: TypeMode): String = when (mode) {
        TypeMode.INSERT -> "insert"
        TypeMode.REPLACE -> "replace"
        TypeMode.APPEND -> "append"
    }

    /**
     * The string ACTION_SET_TEXT should write. Insert honors the caret;
     * replace is the whole new value; append is existing + insert.
     */
    fun targetValue(
        mode: TypeMode,
        existing: String?,
        hint: String?,
        showingHint: Boolean,
        selectionStart: Int,
        selectionEnd: Int,
        insert: String,
    ): String {
        val field = fieldValue(existing, hint, showingHint) ?: ""
        return when (mode) {
            TypeMode.REPLACE -> insert
            TypeMode.APPEND -> field + insert
            TypeMode.INSERT -> compose(field, selectionStart, selectionEnd, insert)
        }
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
