package app.pony.companion.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TypeRouteTest {
    @Test
    fun passwordStopsBeforeClipboardOrKeys() {
        val order = TypeRoute.order(
            password = true,
            imeActive = true,
            imeEnabled = true,
            clipboardOptIn = true,
        )
        assertEquals(listOf(TypeRoute.PASSWORD), order)
    }

    @Test
    fun activeKeyboardIsFirstAndClipboardStaysOutByDefault() {
        val order = TypeRoute.order(
            password = false,
            imeActive = true,
            imeEnabled = true,
            clipboardOptIn = false,
        )
        assertEquals(listOf(TypeRoute.IME, TypeRoute.SET_TEXT, TypeRoute.KEY_EVENTS), order)
        assertFalse(order.contains(TypeRoute.PASTE))
    }

    @Test
    fun notesPathAsksForTheKeyboardBeforeAnyPaste() {
        val order = TypeRoute.order(
            password = false,
            imeActive = false,
            imeEnabled = true,
            clipboardOptIn = true,
        )
        assertEquals(
            listOf(TypeRoute.SET_TEXT, TypeRoute.ASK_IME, TypeRoute.PASTE, TypeRoute.KEY_EVENTS),
            order,
        )
        assertEquals(TypeRoute.ASK_IME, order[order.indexOf(TypeRoute.PASTE) - 1])
    }

    @Test
    fun notesWithoutOptInNeverPastes() {
        val order = TypeRoute.order(
            password = false,
            imeActive = false,
            imeEnabled = true,
            clipboardOptIn = false,
        )
        assertEquals(listOf(TypeRoute.SET_TEXT, TypeRoute.ASK_IME, TypeRoute.KEY_EVENTS), order)
        assertFalse(order.contains(TypeRoute.PASTE))
    }

    @Test
    fun optInOffOmitsPasteEvenWhenSetTextWillFail() {
        val order = TypeRoute.order(
            password = false,
            imeActive = false,
            imeEnabled = false,
            clipboardOptIn = false,
        )
        assertEquals(listOf(TypeRoute.SET_TEXT, TypeRoute.KEY_EVENTS), order)
        assertEquals("ime_disabled", TypeRoute.failure(imeEnabled = false, askedToSwitch = false))
    }

    @Test
    fun pickerTimeoutIsImeRequired() {
        assertEquals("ime_required", TypeRoute.failure(imeEnabled = true, askedToSwitch = true))
    }

    @Test
    fun hintIsNotPartOfTheFieldValue() {
        assertEquals(null, TextEntry.fieldValue("What are you looking for?", "What are you looking for?", false))
        assertEquals(null, TextEntry.fieldValue("What are you looking for?", "other", true))
        assertEquals("hello", TextEntry.fieldValue("hello", "What are you looking for?", false))
        assertEquals("display", TextEntry.compose(null, 0, 28, "display"))
    }
}
