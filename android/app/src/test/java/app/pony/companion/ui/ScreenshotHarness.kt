package app.pony.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pony.companion.ui.design.LocalSafeInsets
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.theme.LocalStillFrame
import app.pony.companion.ui.theme.PonyTheme
import app.pony.companion.ui.theme.SquircleShape

val STATUS_BAR = 36.dp
val GESTURE_BAR = 24.dp

/**
 * A phone frame for JVM screenshots. Robolectric draws no system UI, so this
 * gives the screen the same insets a Galaxy reports and paints a status bar
 * and gesture handle where the real ones sit. Pony's content must stay clear
 * of both, exactly as on the phone.
 */
@Composable
fun DeviceFrame(dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale),
        LocalSafeInsets provides WindowInsets(top = STATUS_BAR, bottom = GESTURE_BAR),
        LocalStillFrame provides true,
    ) {
        PonyTheme(dark = dark) {
            Box(Modifier.fillMaxSize()) {
                content()
                FakeStatusBar(dark, Modifier.align(Alignment.TopCenter))
                GestureHandle(dark, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun FakeStatusBar(dark: Boolean, modifier: Modifier) {
    val ink = if (dark) Color.White else Color(0xFF111111)
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
        Row(
            modifier.fillMaxWidth().height(STATUS_BAR).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("10:28", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(PonyIcons.Wifi, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
                Canvas(Modifier.size(width = 16.dp, height = 13.dp)) {
                    val bar = size.width / 5.5f
                    for (i in 0 until 4) {
                        val h = size.height * (0.35f + i * 0.217f)
                        drawRoundRect(ink, Offset(i * bar * 1.45f, size.height - h), Size(bar, h), CornerRadius(1.5f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("82", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink))
                    Canvas(Modifier.size(width = 22.dp, height = 11.dp)) {
                        val stroke = 1.4.dp.toPx()
                        drawRoundRect(ink, Offset(0f, 0f), Size(size.width - 3.dp.toPx(), size.height), CornerRadius(3.dp.toPx()), style = Stroke(stroke))
                        drawRoundRect(ink, Offset(stroke * 1.6f, stroke * 1.6f), Size((size.width - 3.dp.toPx() - stroke * 3.2f) * 0.82f, size.height - stroke * 3.2f), CornerRadius(1.5.dp.toPx()))
                        drawRoundRect(ink, Offset(size.width - 2.dp.toPx(), size.height * 0.3f), Size(2.dp.toPx(), size.height * 0.4f), CornerRadius(1.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureHandle(dark: Boolean, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(GESTURE_BAR), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(132.dp)
                .height(5.dp)
                .clip(SquircleShape(3.dp, 0f))
                .background(if (dark) Color.White.copy(alpha = 0.85f) else Color(0xFF111111).copy(alpha = 0.8f)),
        )
    }
}

/** Something that looks like another app under Pony's floating pill. */
@Composable
fun FakeApp(dark: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF101418) else Color(0xFFF2F4F7)),
    ) {
        Canvas(Modifier.fillMaxSize().padding(top = 520.dp, start = 18.dp, end = 18.dp, bottom = 40.dp)) {
            val cols = 4
            val rows = 5
            val gap = 14.dp.toPx()
            val w = (size.width - gap * (cols - 1)) / cols
            val h = (size.height - gap * (rows - 1)) / rows
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val op = c == cols - 1
                    drawRoundRect(
                        if (op) Color(0xFF3D7BF7) else if (dark) Color(0xFF1E252D) else Color.White,
                        Offset(c * (w + gap), r * (h + gap)),
                        Size(w, h),
                        CornerRadius(h / 2),
                    )
                }
            }
        }
        Text(
            "12 + 34",
            style = TextStyle(fontSize = 34.sp, color = if (dark) Color(0xFF9AA4AF) else Color(0xFF6B7280)),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 300.dp, end = 28.dp),
        )
        Text(
            "46",
            style = TextStyle(fontSize = 72.sp, color = if (dark) Color.White else Color(0xFF111827)),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 360.dp, end = 28.dp),
        )
        Box(Modifier.align(Alignment.TopCenter).padding(top = STATUS_BAR)) { content() }
    }
}
