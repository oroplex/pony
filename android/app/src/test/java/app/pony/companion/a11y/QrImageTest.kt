package app.pony.companion.a11y

import org.junit.Assert.assertEquals
import org.junit.Test

class QrImageTest {
    @Test
    fun rotatesNinetyDegreesClockwise() {
        val src = byteArrayOf(0, 1, 2, 3)
        val rotated = rotateLuminance(src, width = 2, height = 2, degrees = 90)
        assertEquals(2, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(listOf<Byte>(2, 0, 3, 1), rotated.bytes.toList())
    }

    @Test
    fun leavesZeroRotationUntouched() {
        val src = byteArrayOf(4, 5, 6)
        val rotated = rotateLuminance(src, 3, 1, 0)
        assertEquals(listOf<Byte>(4, 5, 6), rotated.bytes.toList())
    }
}
