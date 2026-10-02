package app.pony.companion.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StallCheckTest {
    @Test
    fun identicalScreensCountAsStalled() {
        val tree = "Settings\nSearch\nConnections\nSounds\nDisplay"
        assertTrue(StallCheck.stalled(tree, tree))
    }

    @Test
    fun whitespaceOnlyDifferencesStillCountAsStalled() {
        val before = "Settings\n  Search \nConnections"
        val after = "Settings\nSearch\nConnections"
        assertTrue(StallCheck.stalled(before, after))
    }

    @Test
    fun aNewScreenIsNotStalled() {
        val before = "Settings\nSearch\nConnections\nSounds\nDisplay"
        val after = "Samsung Keyboard\nLanguages and types\nSmart typing\nAuto replace\nText shortcuts"
        assertFalse(StallCheck.stalled(before, after))
    }

    @Test
    fun theFirstLookIsNeverStalled() {
        assertFalse(StallCheck.stalled(null, "anything"))
        assertFalse(StallCheck.stalled("", "anything"))
    }

    @Test
    fun aTinyChangeOnABusyScreenStillReadsAsStalled() {
        val before = (1..40).joinToString("\n") { "row $it" }
        val after = before + "\nrow 41 highlighted"
        assertTrue(StallCheck.stalled(before, after))
    }
}
