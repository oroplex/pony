package app.pony.companion.display

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundLaunchTest {
    @Test
    fun trustedDisplayCarriesTrustedPublicAndDecorationFlags() {
        val flags = ShellLaunch.displayFlags(trusted = true)
        assertTrue(flags and ShellLaunch.TRUSTED != 0)
        assertTrue(flags and ShellLaunch.PUBLIC != 0)
        assertTrue(flags and ShellLaunch.DECORATIONS != 0)
        assertTrue(flags and ShellLaunch.PRESENTATION != 0)
    }

    @Test
    fun untrustedDisplayDropsTrustAndDecorations() {
        val flags = ShellLaunch.displayFlags(trusted = false)
        assertEquals(0, flags and ShellLaunch.TRUSTED)
        assertEquals(0, flags and ShellLaunch.PUBLIC)
        assertEquals(0, flags and ShellLaunch.DECORATIONS)
        assertTrue(flags and ShellLaunch.PRESENTATION != 0)
    }

    @Test
    fun startTargetsTheDisplayFullscreenAndWaits() {
        assertArrayEquals(
            arrayOf("am", "start", "-W", "--display", "7", "--windowingMode", "1", "-n", "com.app/.Main"),
            ShellLaunch.startArgs("com.app/.Main", 7),
        )
    }

    @Test
    fun forceResizableTogglesTheGlobalSetting() {
        assertArrayEquals(
            arrayOf("settings", "put", "global", "force_resizable_activities", "1"),
            ShellLaunch.resizableArgs(true),
        )
        assertArrayEquals(
            arrayOf("settings", "put", "global", "force_resizable_activities", "0"),
            ShellLaunch.resizableArgs(false),
        )
    }

    @Test
    fun priorResizableValueIsReadBackHonestly() {
        assertTrue(ShellLaunch.resizableWasOn("1"))
        assertTrue(ShellLaunch.resizableWasOn("1\n"))
        assertFalse(ShellLaunch.resizableWasOn("0"))
        assertFalse(ShellLaunch.resizableWasOn("null"))
        assertFalse(ShellLaunch.resizableWasOn(null))
    }

    @Test
    fun appCountsAsLandedOnlyWhenItsOwnWindowLeadsTheHiddenDisplay() {
        val own = "app.pony.companion"
        assertTrue(LaunchCheck.landedOnHidden("com.google.android.apps.photos", "com.google.android.apps.photos", own))
        // Nothing there yet, or only Pony's own UI: the app never arrived.
        assertFalse(LaunchCheck.landedOnHidden("com.google.android.apps.photos", null, own))
        assertFalse(LaunchCheck.landedOnHidden("com.google.android.apps.photos", "", own))
        assertFalse(LaunchCheck.landedOnHidden("com.google.android.apps.photos", own, own))
        // A different package leading means the system refused ours and put something else up.
        assertFalse(LaunchCheck.landedOnHidden("com.postmates.android", "com.android.launcher", own))
    }
}
