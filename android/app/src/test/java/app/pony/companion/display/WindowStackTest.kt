package app.pony.companion.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowStackTest {
    private fun win(
        layer: Int,
        left: Int = 0,
        top: Int = 0,
        right: Int = 1080,
        bottom: Int = 2000,
        pkg: String? = "com.app",
        type: WinType = WinType.APPLICATION,
        focused: Boolean = false,
    ) = WindowShot(type, layer, left, top, right, bottom, pkg, focused)

    @Test
    fun topmostAtPrefersTheHigherLayerWindow() {
        val under = win(layer = 1, pkg = "com.android.settings")
        val popup = win(layer = 5, top = 800, bottom = 1600, pkg = "com.google.android.googlequicksearchbox")
        val top = WindowStack.topmostAt(540, 1200, listOf(under, popup))
        assertEquals("com.google.android.googlequicksearchbox", top?.packageName)
    }

    @Test
    fun topmostAtReturnsNullWhenNothingCoversThePoint() {
        val popup = win(layer = 5, top = 800, bottom = 1600)
        assertNull(WindowStack.topmostAt(540, 100, listOf(popup)))
    }

    @Test
    fun ownOverlaysAreNeverTargets() {
        val pill = win(layer = 99, type = WinType.ACCESSIBILITY_OVERLAY, pkg = "app.pony.companion")
        val app = win(layer = 2, pkg = "com.android.settings")
        assertTrue(WindowStack.targetable(listOf(pill, app)).none { it.type == WinType.ACCESSIBILITY_OVERLAY })
        // Even though the pill sits on the highest layer, a tap resolves to the app under it.
        assertEquals("com.android.settings", WindowStack.topmostAt(540, 1000, listOf(pill, app))?.packageName)
    }

    @Test
    fun orderedIsTopmostFirst() {
        val a = win(layer = 1, pkg = "a")
        val b = win(layer = 7, pkg = "b")
        val c = win(layer = 3, pkg = "c")
        assertEquals(listOf("b", "c", "a"), WindowStack.ordered(listOf(a, b, c)).map { it.packageName })
    }

    @Test
    fun leadWindowPrefersFocusedThenApplication() {
        val sys = win(layer = 8, type = WinType.SYSTEM, pkg = "android")
        val app = win(layer = 2, pkg = "com.android.settings")
        val popup = win(layer = 5, pkg = "com.google.android.googlequicksearchbox", focused = true)
        assertEquals("com.google.android.googlequicksearchbox", WindowStack.leadWindow(listOf(sys, app, popup))?.packageName)
        // With nothing focused, the topmost application wins over a higher system bar.
        assertEquals("com.android.settings", WindowStack.leadWindow(listOf(sys, app))?.packageName)
    }
}
