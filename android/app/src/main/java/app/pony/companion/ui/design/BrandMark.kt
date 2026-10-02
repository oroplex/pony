package app.pony.companion.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pony.companion.ui.theme.Palette
import app.pony.companion.ui.theme.SquircleShape

/** The pony head from the launcher icon: white head, mane-gold mane, iris eye. */
val PonyMarkVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pony.Mark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 50f,
        viewportHeight = 50f,
    ).apply {
        group(translationX = -11.5f, translationY = -6f) {
            addPath(
                pathData = addPathNodes("M21,54l3,-17c-2,-9 3,-18 11,-22l2,-7 4,6c6,2 11,8 12,15l1,6c0,3 -3,5 -6,4l-6,-2 -4,5 1,12z"),
                fill = SolidColor(Color.White),
            )
            addPath(
                pathData = addPathNodes("M35,15c-7,3 -12,10 -12,19l-2,5c-2,-9 1,-19 9,-24z"),
                fill = SolidColor(Palette.OrbMane),
            )
            path(fill = SolidColor(Color(0xFF6C4DF6))) {
                moveTo(39.7f, 25f)
                arcTo(2.3f, 2.3f, 0f, true, true, 44.3f, 25f)
                arcTo(2.3f, 2.3f, 0f, true, true, 39.7f, 25f)
                close()
            }
        }
    }.build()
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val shape = SquircleShape(size * 0.3f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF7B63FF), Color(0xFF5A3FE6), Color(0xFF4A2FD0))))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(PonyMarkVector, contentDescription = null, modifier = Modifier.fillMaxSize().padding(size * 0.12f))
    }
}
