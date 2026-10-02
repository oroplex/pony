package app.pony.companion.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** A 4 dp grid. Screens use [gutter] at the edges and [section] between groups. */
object Space {
    val hair: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val section: Dp = 32.dp
    val hero: Dp = 48.dp
    val gutter: Dp = 20.dp
    val touch: Dp = 48.dp
    val bar: Dp = 56.dp
}

object Radius {
    val chip: Dp = 14.dp
    val control: Dp = 18.dp
    val card: Dp = 26.dp
    val sheet: Dp = 34.dp
    val pill: Dp = 999.dp
}

/**
 * Motion follows three gaits: [snap] for touch feedback, [glide] for layout,
 * and [drift] for ambient life. Springs keep motion interruptible.
 */
object Motion {
    val Glide = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    const val QUICK = 140
    const val BASE = 260
    const val SLOW = 420
    const val ENTER = 520

    fun <T> snap(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = 900f)
    fun <T> glide(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 420f)
    fun <T> bounce(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = 380f)
    fun <T> drift(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 120f)
    fun <T> fade(duration: Int = BASE, delay: Int = 0): FiniteAnimationSpec<T> = tween(duration, delay, Glide)

    val slide: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = 0.9f, stiffness = 420f, visibilityThreshold = IntOffset(1, 1))
}
