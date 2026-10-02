package app.pony.companion.ui.design

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertTrue
import org.junit.Test

class PonyIconsTest {
    @Test
    fun everyIconBuildsFromValidPathData() {
        val getters = PonyIcons::class.java.methods.filter { it.returnType == ImageVector::class.java && it.parameterCount == 0 }
        assertTrue("expected the generated icon set", getters.size >= 60)
        getters.forEach { getter ->
            val icon = getter.invoke(PonyIcons) as ImageVector
            assertTrue(getter.name, icon.name.startsWith("Pony."))
        }
    }
}
