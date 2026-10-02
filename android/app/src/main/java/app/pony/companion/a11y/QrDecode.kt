package app.pony.companion.a11y

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

/**
 * Turns a camera Y plane into QR text, robustly. Pulled out of the CameraX
 * analyzer so the whole path — packing a padded, possibly interleaved plane,
 * rotating it upright, and reading it with more than one binarizer — runs in a
 * plain JVM test instead of only on a device.
 */
object QrDecode {
    private val HINTS = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.CHARACTER_SET to "UTF-8",
        // Spend more effort per frame, and read a code printed light-on-dark too.
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.ALSO_INVERTED to true,
    )

    /**
     * Copies the luminance bytes out of a camera plane into a tight
     * width×height array. Camera planes pad each row (rowStride > width) and can
     * interleave channels (pixelStride > 1); both are handled, and every read is
     * bounds-checked so a short final row can't crash the analyzer.
     */
    fun pack(plane: ByteArray, width: Int, height: Int, rowStride: Int, pixelStride: Int): ByteArray {
        val out = ByteArray(width * height)
        var dst = 0
        for (row in 0 until height) {
            val rowStart = row * rowStride
            var col = 0
            while (col < width) {
                val idx = rowStart + col * pixelStride
                if (idx in plane.indices) out[dst + col] = plane[idx]
                col++
            }
            dst += width
        }
        return out
    }

    /**
     * Reads a QR from a tight luminance array. The frame is rotated upright by
     * [rotationDegrees] first, then tried with the hybrid binarizer, the
     * global-histogram binarizer, and finally a centre crop — so even, uneven,
     * and busy backgrounds all have a chance. Null when nothing decodes.
     */
    fun decode(packed: ByteArray, width: Int, height: Int, rotationDegrees: Int): String? {
        val upright = rotateLuminance(packed, width, height, rotationDegrees)
        if (upright.width <= 0 || upright.height <= 0) return null
        val source = PlanarYUVLuminanceSource(
            upright.bytes,
            upright.width,
            upright.height,
            0,
            0,
            upright.width,
            upright.height,
            false,
        )
        readEachWay(source)?.let { return it }
        val cropW = upright.width * 2 / 3
        val cropH = upright.height * 2 / 3
        if (cropW > 0 && cropH > 0) {
            val left = (upright.width - cropW) / 2
            val top = (upright.height - cropH) / 2
            val cropped = runCatching { source.crop(left, top, cropW, cropH) }.getOrNull()
            if (cropped != null) readEachWay(cropped)?.let { return it }
        }
        return null
    }

    private fun readEachWay(source: LuminanceSource): String? {
        read(BinaryBitmap(HybridBinarizer(source)))?.let { return it }
        read(BinaryBitmap(GlobalHistogramBinarizer(source)))?.let { return it }
        return null
    }

    private fun read(bitmap: BinaryBitmap): String? = runCatching {
        MultiFormatReader().decode(bitmap, HINTS).text
    }.getOrNull()
}
