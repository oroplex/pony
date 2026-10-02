package app.pony.companion.display

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnUiTest {
    private val own = "app.pony.companion"

    @Test
    fun ponyInFrontOnTheMainScreenMeansLeaveFirst() {
        assertTrue(OwnUi.inFront(onMainScreen = true, foreground = own, ownPackage = own))
    }

    @Test
    fun anotherAppInFrontIsTheRealTask() {
        assertFalse(OwnUi.inFront(onMainScreen = true, foreground = "com.android.settings", ownPackage = own))
    }

    @Test
    fun theHiddenScreenNeverShowsPonySoItIsNeverLeft() {
        assertFalse(OwnUi.inFront(onMainScreen = false, foreground = own, ownPackage = own))
    }

    @Test
    fun nothingInFrontIsNotPony() {
        assertFalse(OwnUi.inFront(onMainScreen = true, foreground = null, ownPackage = own))
        assertFalse(OwnUi.inFront(onMainScreen = true, foreground = "", ownPackage = own))
    }
}
