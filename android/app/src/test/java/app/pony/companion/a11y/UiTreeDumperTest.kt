package app.pony.companion.a11y

import org.junit.Assert.assertEquals
import org.junit.Test

class UiTreeDumperTest {
    @Test
    fun formatsLabelFlagsAndBounds() {
        assertEquals(
            "TextView \"Stay awake\" clickable bounds=0,100,200,160",
            UiTreeDumper.nodeLine(0, "TextView", "\"Stay awake\"", listOf("clickable"), 0, 100, 200, 160),
        )
    }

    @Test
    fun indentsByDepth() {
        assertEquals(
            "    Switch bounds=10,20,30,40",
            UiTreeDumper.nodeLine(2, "Switch", null, emptyList(), 10, 20, 30, 40),
        )
    }

    @Test
    fun keepsOffscreenRowsVisibleInTheDump() {
        // A row accessibility hides from sight is still listed, tagged offscreen.
        assertEquals(
            "Switch \"Stay awake\" clickable offscreen bounds=0,0,100,50",
            UiTreeDumper.nodeLine(0, "Switch", "\"Stay awake\"", listOf("clickable", "offscreen"), 0, 0, 100, 50),
        )
    }

    @Test
    fun aBareContainerIsJustClassAndBounds() {
        assertEquals(
            "FrameLayout bounds=0,0,1080,2400",
            UiTreeDumper.nodeLine(0, "FrameLayout", null, emptyList(), 0, 0, 1080, 2400),
        )
    }

    @Test
    fun aCheckableRowReportsWhetherItIsOn() {
        assertEquals(listOf("clickable", "checked"), UiTreeDumper.nodeFlags(
            clickable = true, editable = false, focused = false, password = false,
            checkable = true, checked = true, visibleToUser = true,
        ))
        assertEquals(listOf("clickable", "unchecked"), UiTreeDumper.nodeFlags(
            clickable = true, editable = false, focused = false, password = false,
            checkable = true, checked = false, visibleToUser = true,
        ))
    }

    @Test
    fun aPlainRowHasNoCheckState() {
        assertEquals(emptyList<String>(), UiTreeDumper.nodeFlags(
            clickable = false, editable = false, focused = false, password = false,
            checkable = false, checked = false, visibleToUser = true,
        ))
    }

    @Test
    fun theCheckStateShowsOnTheSwitchLine() {
        assertEquals(
            "Switch \"Stay awake\" clickable checked bounds=0,0,100,50",
            UiTreeDumper.nodeLine(0, "Switch", "\"Stay awake\"", listOf("clickable", "checked"), 0, 0, 100, 50),
        )
    }
}
