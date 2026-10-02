package app.pony.companion.a11y

/** Y plane of a camera frame, rotated upright for the QR decoder. */
data class YPlane(val bytes: ByteArray, val width: Int, val height: Int)

fun rotateLuminance(src: ByteArray, width: Int, height: Int, degrees: Int): YPlane {
    val rotation = ((degrees % 360) + 360) % 360
    if (rotation == 0 || (rotation != 90 && rotation != 180 && rotation != 270)) {
        return YPlane(src, width, height)
    }
    val newW = if (rotation == 180) width else height
    val newH = if (rotation == 180) height else width
    val dst = ByteArray(width * height)
    for (sy in 0 until height) {
        for (sx in 0 until width) {
            val dx: Int
            val dy: Int
            when (rotation) {
                90 -> {
                    dx = height - 1 - sy
                    dy = sx
                }
                180 -> {
                    dx = width - 1 - sx
                    dy = height - 1 - sy
                }
                else -> {
                    dx = sy
                    dy = width - 1 - sx
                }
            }
            dst[dy * newW + dx] = src[sy * width + sx]
        }
    }
    return YPlane(dst, newW, newH)
}
