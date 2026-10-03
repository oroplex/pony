package app.pony.companion.display

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class DisplayPolicyTest {
    @Test
    fun prefOnWithNoParamsUsesBackground() {
        assertEquals(DisplayPolicy.Choice.BACKGROUND, DisplayPolicy.choice(prefOn = true, background = null, display = null))
    }

    @Test
    fun prefOffWithNoParamsUsesMain() {
        assertEquals(DisplayPolicy.Choice.MAIN, DisplayPolicy.choice(prefOn = false, background = null, display = null))
    }

    @Test
    fun displayNameWinsOverPref() {
        assertEquals(DisplayPolicy.Choice.MAIN, DisplayPolicy.choice(true, true, "main"))
        assertEquals(DisplayPolicy.Choice.MAIN, DisplayPolicy.choice(true, null, "foreground"))
        assertEquals(DisplayPolicy.Choice.BACKGROUND, DisplayPolicy.choice(false, false, "background"))
    }

    @Test
    fun explicitBackgroundFlagWinsWhenDisplayIsAbsent() {
        assertEquals(DisplayPolicy.Choice.MAIN, DisplayPolicy.choice(true, false, null))
        assertEquals(DisplayPolicy.Choice.BACKGROUND, DisplayPolicy.choice(false, true, null))
        assertEquals(DisplayPolicy.Choice.BACKGROUND, DisplayPolicy.choice(false, true, " "))
    }

    @Test
    fun theOwnerIsAskedForAFullScreenFallback() {
        val line = "Calculator can't open out of sight on this phone. Open it full screen on your screen instead? Pony will only ask once this session."
        assertEquals(line, DisplayPolicy.consentText("Calculator", popups = true))
        assertEquals(line, DisplayPolicy.consentText("Calculator", popups = false))
    }

    @Test
    fun requestedBackgroundIsTrueOnlyForAnExplicitBackgroundAsk() {
        assertTrue(DisplayPolicy.requestedBackground(JSONObject().put("display", "background")))
        assertTrue(DisplayPolicy.requestedBackground(JSONObject().put("background", true)))
        assertTrue(DisplayPolicy.requestedBackground(JSONObject().put("display", "BACKGROUND")))
    }

    @Test
    fun requestedBackgroundIsFalseWithoutAnExplicitAsk() {
        assertFalse(DisplayPolicy.requestedBackground(null))
        assertFalse(DisplayPolicy.requestedBackground(JSONObject()))
        assertFalse(DisplayPolicy.requestedBackground(JSONObject().put("background", false)))
        assertFalse(DisplayPolicy.requestedBackground(JSONObject().put("display", "main")))
        // An explicit main wins even if the background flag is set.
        assertFalse(DisplayPolicy.requestedBackground(JSONObject().put("display", "main").put("background", true)))
    }

    @Test
    fun unavailableTextNamesWhereItLandedInstead() {
        assertEquals(
            "You asked to keep Settings on Pony's hidden screen, but this phone can't run it there, so it opened in a pop-up on your screen instead.",
            DisplayPolicy.unavailableText("Settings", "freeform"),
        )
        assertEquals(
            "You asked to keep Settings on Pony's hidden screen, but this phone can't run it there, so it opened on your screen instead.",
            DisplayPolicy.unavailableText("Settings", "main"),
        )
    }

    @Test
    fun maximizedBoundsFillTheWholeScreen() {
        // A refused app falls back maximized, not as a small pop-up needing a tap.
        assertArrayEquals(intArrayOf(0, 0, 1080, 2400), DisplayPolicy.maximizedBounds(1080, 2400))
    }

    @Test
    fun maximizedBoundsStayPositive() {
        assertArrayEquals(intArrayOf(0, 0, 1, 1), DisplayPolicy.maximizedBounds(0, -5))
    }

    @Test
    fun screenSizePrefersTheRealPanelOverTheAppWindow() {
        // S26 Ultra panel is 1440x3120; the freeform chrome used to report 3269.
        assertArrayEquals(intArrayOf(1440, 3120), DisplayPolicy.screenSize(1440, 2971, 1440, 3120))
        assertArrayEquals(intArrayOf(1080, 2400), DisplayPolicy.screenSize(1080, 2400, 0, 0))
    }
}
