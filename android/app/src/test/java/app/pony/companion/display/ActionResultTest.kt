package app.pony.companion.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ActionResultTest {
    @Test
    fun screenshotBytesNeverLeakIntoTheJson() {
        val result = ActionResult(
            ok = true,
            target = ScreenTarget("background", 18, 1440, 3120, null),
            fields = mapOf("jpeg" to ByteArray(8) { 7 }, "width" to 1440, "height" to 3120, "note" to null),
        )
        val json = result.json()
        assertFalse(json.has("jpeg"))
        assertFalse(json.toString().contains("[B@"))
        assertEquals(1440, json.getInt("width"))
        assertEquals("background", json.getString("display"))
        assertFalse(json.has("note"))
    }
}
