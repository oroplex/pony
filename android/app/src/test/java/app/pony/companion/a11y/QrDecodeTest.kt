package app.pony.companion.a11y

import app.pony.companion.proto.PairingPayload
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises the real scan path on the JVM: render the actual pairing QR, feed it
 * through the same packing + rotation + decode the camera analyzer uses, and
 * parse the result back into a [PairingPayload]. Guards the Galaxy-S26 report
 * that scanning did nothing — covers padded rows, interleaved planes, and every
 * sensor rotation.
 */
class QrDecodeTest {
    private val token = "ab".repeat(32)
    private val payload = PairingPayload(
        relay = "wss://relay.pony.karlmagendavid.com",
        token = token,
        pk = "z_0qekXbJeYuSG-eeLL65tW9xFgQxAHZFosjY6MRlSk",
    ).toJson()

    @Test
    fun decodesTheRealPayloadThroughEveryRotationStrideAndSize() {
        for (size in listOf(220, 300, 380)) {
            val upright = qrCanvas(payload, size, padRight = 40, padBottom = 80)
            for (rotation in listOf(0, 90, 180, 270)) {
                // Build the "sensor" frame: the upright QR pre-rotated by the
                // inverse, so the analyzer's forward rotation lands it upright.
                val sensor = rotateLuminance(upright.bytes, upright.width, upright.height, (360 - rotation) % 360)
                val pixelStride = 2
                val rowStride = sensor.width * pixelStride + 12
                val plane = ByteArray(rowStride * sensor.height)
                for (row in 0 until sensor.height) {
                    for (col in 0 until sensor.width) {
                        plane[row * rowStride + col * pixelStride] = sensor.bytes[row * sensor.width + col]
                    }
                }
                val packed = QrDecode.pack(plane, sensor.width, sensor.height, rowStride, pixelStride)
                assertArrayEquals("packing a padded plane should recover the row-major bytes", sensor.bytes, packed)

                val text = QrDecode.decode(packed, sensor.width, sensor.height, rotation)
                assertNotNull("size $size rotation $rotation should decode", text)
                val parsed = PairingPayload.parse(text!!)
                assertEquals("wss://relay.pony.karlmagendavid.com", parsed.relay)
                assertEquals(token, parsed.token)
                assertEquals("z_0qekXbJeYuSG-eeLL65tW9xFgQxAHZFosjY6MRlSk", parsed.pk)
            }
        }
    }

    @Test
    fun packPullsEveryPixelOutOfAPaddedInterleavedPlane() {
        // width 3, height 2, two padding bytes per row, Y interleaved every 2nd byte.
        val plane = byteArrayOf(
            10, 0, 20, 0, 30, 0, 99, 99,
            40, 0, 50, 0, 60, 0, 99, 99,
        )
        val packed = QrDecode.pack(plane, width = 3, height = 2, rowStride = 8, pixelStride = 2)
        assertArrayEquals(byteArrayOf(10, 20, 30, 40, 50, 60), packed)
    }

    @Test
    fun packToleratesAShortFinalRowWithoutCrashing() {
        // A real camera buffer often omits the trailing padding of the last row.
        val plane = byteArrayOf(1, 2, 0, 0, 3, 4)
        val packed = QrDecode.pack(plane, width = 2, height = 2, rowStride = 4, pixelStride = 1)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), packed)
    }

    @Test
    fun garbageFramesDecodeToNull() {
        val noise = ByteArray(200 * 200) { ((it * 31 + 7) % 256 - 128).toByte() }
        assertNull(QrDecode.decode(noise, 200, 200, 0))
        assertNull(QrDecode.decode(ByteArray(0), 0, 0, 90))
    }

    /** Renders [payload] as a QR into a white, deliberately non-square canvas. */
    private fun qrCanvas(payload: String, size: Int, padRight: Int, padBottom: Int): YPlane {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2,
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        val width = size + padRight
        val height = size + padBottom
        val bytes = ByteArray(width * height) { -1 } // 0xFF: white background
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (matrix.get(x, y)) bytes[y * width + x] = 0 // black module
            }
        }
        return YPlane(bytes, width, height)
    }
}
