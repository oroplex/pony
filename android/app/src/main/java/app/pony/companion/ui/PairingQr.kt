package app.pony.companion.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun PairingQr(payload: String, modifier: Modifier = Modifier) {
    val bitmap = remember(payload) { runCatching { pairingQrBitmap(payload) }.getOrNull() } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Pairing code",
        modifier = modifier.size(220.dp),
    )
}

fun pairingQrBitmap(text: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.parseColor("#2A1C06") else AndroidColor.parseColor("#FFFBF5"))
        }
    }
    return bitmap
}
