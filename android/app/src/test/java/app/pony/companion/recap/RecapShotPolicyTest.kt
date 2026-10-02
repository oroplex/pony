package app.pony.companion.recap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapShotPolicyTest {
    @Test
    fun keepsAWorkScreenButNeverPonysOwn() {
        assertTrue(RecapShotPolicy.keep(ponyForeground = false))
        assertFalse(RecapShotPolicy.keep(ponyForeground = true))
    }
}
