package app.pony.companion.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeStatusTest {
    private val id = "app.pony.companion.debug/app.pony.companion.input.PonyInputMethodService"

    @Test
    fun matchesFullIdShortIdAndSubtypeSuffix() {
        assertTrue(ImeStatus.listed(id, id, id))
        assertTrue(ImeStatus.listed("$id:com.samsung.android.honeyboard/.service.HoneyBoardService", id, id))
        assertTrue(ImeStatus.listed("$id;1234:com.samsung/.Other", id, ".input.PonyInputMethodService"))
        assertTrue(ImeStatus.listed("com.samsung/.Other:$id", "unused", id))
    }

    @Test
    fun ponyUsableRequiresEnabledAndSelectedOrActive() {
        assertTrue(ImeSnapshot("gboard", ponyEnabled = true, ponySelected = true, ponyActive = false).ponyUsable)
        assertTrue(ImeSnapshot("gboard", ponyEnabled = true, ponySelected = false, ponyActive = true).ponyUsable)
        assertFalse(ImeSnapshot("gboard", ponyEnabled = false, ponySelected = false, ponyActive = false).ponyUsable)
        assertFalse(ImeSnapshot("gboard", ponyEnabled = true, ponySelected = false, ponyActive = false).ponyUsable)
    }

    @Test
    fun missesOtherKeyboardsAndBlankLists() {
        assertFalse(ImeStatus.listed("com.samsung.android.honeyboard/.service.HoneyBoardService", id, id))
        assertFalse(ImeStatus.listed(null, id, id))
        assertFalse(ImeStatus.listed("", id, id))
        assertFalse(ImeStatus.listed("$id.extra", id, id))
    }
}
