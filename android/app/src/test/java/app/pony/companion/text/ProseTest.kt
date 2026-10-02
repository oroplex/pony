package app.pony.companion.text

import org.junit.Assert.assertEquals
import org.junit.Test

class ProseTest {
    @Test
    fun stripsBoldSoNoLiteralAsterisksShow() {
        assertEquals("What I can help with:", Prose.plain("**What I can help with:**"))
        assertEquals("really", Prose.plain("__really__"))
    }

    @Test
    fun turnsBulletListsIntoPlainLines() {
        assertEquals(
            "What I can help with:\nfix autocorrect\nadd a language",
            Prose.plain("**What I can help with:**\n- fix autocorrect\n- add a language"),
        )
        assertEquals("first\nsecond", Prose.plain("1. first\n2. second"))
    }

    @Test
    fun stripsInlineCodeItalicsHeadingsAndLinks() {
        assertEquals("Open the Settings app", Prose.plain("Open the `Settings` app"))
        assertEquals("really important", Prose.plain("*really* important"))
        assertEquals("Keyboard setup", Prose.plain("# Keyboard setup"))
        assertEquals("the docs", Prose.plain("[the docs](https://example.com)"))
        assertEquals("gone", Prose.plain("~~gone~~"))
    }

    @Test
    fun leavesPlainSentencesAndMathAlone() {
        assertEquals("I turned on autocorrect for you.", Prose.plain("I turned on autocorrect for you."))
        assertEquals("2 + 2 = 4", Prose.plain("2 + 2 = 4"))
        assertEquals("Stopped by you", Prose.plain("Stopped by you"))
    }

    @Test
    fun keepsUnderscoresInsideWords() {
        assertEquals("open user_settings now", Prose.plain("open user_settings now"))
    }

    @Test
    fun isIdempotent() {
        val messy = "**Done!**\n- turned on `auto spell`\n- see [more](https://x.y)"
        val once = Prose.plain(messy)
        assertEquals(once, Prose.plain(once))
    }

    @Test
    fun collapsesABlankRun() {
        assertEquals("a\n\nb", Prose.plain("a\n\n\n\nb"))
    }
}
