package app.pony.companion.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEntryTest {
    @Test
    fun insertsAtCursor() {
        assertEquals("heXYllo", TextEntry.compose("hello", 2, 2, "XY"))
    }

    @Test
    fun replacesSelection() {
        assertEquals("hXYlo", TextEntry.compose("hello", 1, 3, "XY"))
    }

    @Test
    fun appendsWhenCaretUnknown() {
        assertEquals("helloXY", TextEntry.compose("hello", -1, -1, "XY"))
    }

    @Test
    fun emptyFieldUsesTheInsert() {
        assertEquals("XY", TextEntry.compose("", -1, -1, "XY"))
        assertEquals("XY", TextEntry.compose(null, 0, 0, "XY"))
    }

    @Test
    fun caretAtStart() {
        assertEquals("XYhello", TextEntry.compose("hello", 0, 0, "XY"))
    }

    @Test
    fun outOfRangeCaretAppends() {
        assertEquals("helloXY", TextEntry.compose("hello", 9, 9, "XY"))
    }

    @Test
    fun passwordInputTypesAreRefused() {
        assertTrue(TextEntry.isPasswordField(true, 0))
        assertTrue(TextEntry.isPasswordField(false, 0x00000081))
        assertTrue(TextEntry.isPasswordField(false, 0x00000091))
        assertTrue(TextEntry.isPasswordField(false, 0x000000e1))
        assertTrue(TextEntry.isPasswordField(false, 0x00000012))
        assertFalse(TextEntry.isPasswordField(false, 0x00000001))
        assertFalse(TextEntry.isPasswordField(false, 0x00000021))
    }
}
