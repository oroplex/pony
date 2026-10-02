package app.pony.companion.display

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverCheckTest {
    private val ctx = CoverCheck.Context(
        ownPackage = "app.pony.companion",
        statusBarHeight = 110,
        navBarHeight = 60,
        screenHeight = 3120,
        expectedPackage = "com.sec.android.app.popupcalculator",
    )
    private val calculator = WindowShot(WinType.APPLICATION, 1, 0, 0, 1440, 3120, "com.sec.android.app.popupcalculator", focused = true)
    private val statusBar = WindowShot(WinType.SYSTEM, 10, 0, 0, 1440, 110, "com.android.systemui")
    private val navBar = WindowShot(WinType.SYSTEM, 11, 0, 3060, 1440, 3120, "com.android.systemui")

    @Test
    fun aPlainAppWindowIsClear() {
        assertEquals(Cover.Clear, CoverCheck.at(700, 2000, listOf(calculator, statusBar, navBar), ctx))
    }

    @Test
    fun theThinStatusAndNavigationBarsAreNotPopups() {
        assertEquals(Cover.Clear, CoverCheck.at(700, 50, listOf(calculator, statusBar, navBar), ctx))
        assertEquals(Cover.Clear, CoverCheck.at(700, 3100, listOf(calculator, statusBar, navBar), ctx))
    }

    @Test
    fun aHeadsUpCoveringTheTopOfTheScreenIsAPopup() {
        val headsUp = WindowShot(WinType.SYSTEM, 12, 0, 0, 1440, 380, "com.android.systemui")
        val windows = listOf(calculator, headsUp, navBar)
        assertEquals(Cover.Popup("com.android.systemui", "system"), CoverCheck.at(700, 300, windows, ctx))
        assertEquals(Cover.Clear, CoverCheck.at(700, 1200, windows, ctx))
    }

    @Test
    fun aFullScreenAlarmFromTheClockCoversTheTarget() {
        val alarm = WindowShot(WinType.APPLICATION, 5, 0, 0, 1440, 3120, "com.sec.android.app.clockpackage")
        assertEquals(Cover.Popup("com.sec.android.app.clockpackage", "alert"), CoverCheck.at(700, 2000, listOf(calculator, alarm), ctx))
        val clockTask = ctx.copy(expectedPackage = "com.sec.android.app.clockpackage")
        assertEquals(Cover.Clear, CoverCheck.at(700, 2000, listOf(calculator, alarm), clockTask))
    }

    @Test
    fun callWindowsAreNeverTapped() {
        val incoming = WindowShot(WinType.APPLICATION, 6, 0, 0, 1440, 3120, "com.samsung.android.incallui")
        assertEquals(Cover.CallUi("com.samsung.android.incallui"), CoverCheck.at(700, 2000, listOf(calculator, incoming), ctx))
        val whatsappCall = ctx.copy(protectedPackages = CallPolicy.CALL_UI_PACKAGES + "com.whatsapp")
        val bubble = WindowShot(WinType.SYSTEM, 13, 1100, 600, 1400, 900, "com.whatsapp")
        assertEquals(Cover.CallUi("com.whatsapp"), CoverCheck.at(1200, 700, listOf(calculator, bubble), whatsappCall))
    }

    @Test
    fun ponysOwnOverlayIsIgnoredButItsAppWindowIsNotTapped() {
        val overlay = WindowShot(WinType.ACCESSIBILITY_OVERLAY, 20, 300, 120, 1140, 260, "app.pony.companion")
        assertEquals(Cover.Clear, CoverCheck.at(700, 200, listOf(calculator, statusBar, overlay), ctx))
        val ponyApp = WindowShot(WinType.APPLICATION, 4, 0, 0, 1440, 3120, "app.pony.companion")
        assertEquals(Cover.Popup("app.pony.companion", "pony"), CoverCheck.at(700, 2000, listOf(calculator, ponyApp), ctx))
    }

    @Test
    fun theKeyboardIsNotAPopup() {
        val ime = WindowShot(WinType.INPUT_METHOD, 7, 0, 2000, 1440, 3060, "com.samsung.android.honeyboard")
        assertEquals(Cover.Clear, CoverCheck.at(700, 2500, listOf(calculator, ime), ctx))
    }
}
