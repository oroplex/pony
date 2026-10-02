package app.pony.companion.text

/**
 * Turns a model's markdown reply into the short, plain text Pony shows on the
 * result card and speaks out loud. Bold, italics, inline code, headings, links,
 * and bullet markers all become clean words — the owner never sees a literal
 * ** or a "- " on screen, and the voice never reads an asterisk.
 */
object Prose {
    private val FENCE = Regex("```[a-zA-Z0-9]*\\n?([\\s\\S]*?)```")
    private val IMAGE = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val CODE = Regex("`([^`]+)`")
    private val BOLD_STAR = Regex("\\*\\*([^*]+)\\*\\*")
    private val BOLD_UNDER = Regex("__([^_]+)__")
    private val STRIKE = Regex("~~([^~]+)~~")
    private val ITALIC_STAR = Regex("(?<![*\\w])\\*(?!\\s)([^*\\n]+?)(?<!\\s)\\*(?![*\\w])")
    private val ITALIC_UNDER = Regex("(?<![_\\w])_(?!\\s)([^_\\n]+?)(?<!\\s)_(?![_\\w])")
    private val LEFTOVER_EMPHASIS = Regex("\\*{2,}")
    private val HEADING = Regex("^\\s{0,3}#{1,6}\\s+")
    private val QUOTE = Regex("^\\s{0,3}>\\s?")
    private val BULLET = Regex("^\\s*([-*+]|\\d+[.)])\\s+")
    private val MANY_BLANKS = Regex("\n{3,}")

    fun plain(text: String): String {
        if (text.isBlank()) return ""
        var out = text.replace("\r\n", "\n").replace('\r', '\n')
        out = FENCE.replace(out) { it.groupValues[1] }
        out = IMAGE.replace(out) { it.groupValues[1] }
        out = LINK.replace(out) { it.groupValues[1] }
        out = CODE.replace(out) { it.groupValues[1] }
        out = BOLD_STAR.replace(out) { it.groupValues[1] }
        out = BOLD_UNDER.replace(out) { it.groupValues[1] }
        out = STRIKE.replace(out) { it.groupValues[1] }
        out = ITALIC_STAR.replace(out) { it.groupValues[1] }
        out = ITALIC_UNDER.replace(out) { it.groupValues[1] }
        out = LEFTOVER_EMPHASIS.replace(out, "")
        out = out.lines().joinToString("\n") { line ->
            line.replaceFirst(HEADING, "").replaceFirst(QUOTE, "").replaceFirst(BULLET, "").trimEnd()
        }
        return MANY_BLANKS.replace(out, "\n\n").trim()
    }
}
