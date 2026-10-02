package app.pony.companion.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryLadderTest {
    @Test
    fun escalatesInTheOwnersOrder() {
        assertEquals(
            listOf(TapStrategy.A11yClick, TapStrategy.Gesture, TapStrategy.ScrollIntoView, TapStrategy.SearchPath),
            RetryLadder.plan(hasLabel = true),
        )
    }

    @Test
    fun dropsTheSearchPathWithoutALabelToMatch() {
        assertEquals(
            listOf(TapStrategy.A11yClick, TapStrategy.Gesture, TapStrategy.ScrollIntoView),
            RetryLadder.plan(hasLabel = false),
        )
    }

    @Test
    fun capsTheNumberOfRetries() {
        assertTrue(RetryLadder.plan(hasLabel = true).size <= RetryLadder.MAX_RETRIES)
        assertTrue(RetryLadder.plan(hasLabel = false).size <= RetryLadder.MAX_RETRIES)
    }

    @Test
    fun treatsAChangedScreenOrAClickableTargetAsLanded() {
        assertTrue(RetryLadder.landedFirstTap(screenChanged = true, onClickable = false))
        assertTrue(RetryLadder.landedFirstTap(screenChanged = false, onClickable = true))
        assertTrue(RetryLadder.landedFirstTap(screenChanged = true, onClickable = true))
    }

    @Test
    fun retriesOnlyWhenNothingChangedAndNothingWasClickable() {
        assertFalse(RetryLadder.landedFirstTap(screenChanged = false, onClickable = false))
    }
}
