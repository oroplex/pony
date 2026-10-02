package app.pony.companion.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Rounded rectangle with continuous-curvature corners. Each corner starts
 * [smoothing] times its radius early and eases in, so edges flow into
 * corners without the visible kink of a circular arc.
 */
class SquircleShape(
    private val radius: Dp,
    private val smoothing: Float = 0.6f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        val maxReach = minOf(w, h) / 2f
        val reach = minOf(with(density) { radius.toPx() } * (1f + smoothing), maxReach)
        if (reach <= 0f) {
            path.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
            return Outline.Generic(path)
        }
        val r = reach / (1f + smoothing)
        val c = ((2.343f * r - reach) / 3f).coerceAtLeast(0f)
        path.moveTo(reach, 0f)
        path.lineTo(w - reach, 0f)
        path.cubicTo(w - c, 0f, w, c, w, reach)
        path.lineTo(w, h - reach)
        path.cubicTo(w, h - c, w - c, h, w - reach, h)
        path.lineTo(reach, h)
        path.cubicTo(c, h, 0f, h - c, 0f, h - reach)
        path.lineTo(0f, reach)
        path.cubicTo(0f, c, c, 0f, reach, 0f)
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is SquircleShape && other.radius == radius && other.smoothing == smoothing

    override fun hashCode(): Int = radius.hashCode() * 31 + smoothing.hashCode()
}

object Shapes {
    val chip = SquircleShape(Radius.chip)
    val control = SquircleShape(Radius.control)
    val card = SquircleShape(Radius.card)
    val sheet = SquircleShape(Radius.sheet)
    val tile = SquircleShape(16.dp)
    val icon = SquircleShape(12.dp)
}
