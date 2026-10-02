package app.pony.companion.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pony.companion.ui.theme.LocalReduceMotion
import app.pony.companion.ui.theme.LocalStillFrame
import app.pony.companion.ui.theme.Motion
import app.pony.companion.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbMood { Idle, Offline, Listening, Thinking, Working, Waiting, Success, Error }

private data class OrbTone(val glow: Color, val swirl: List<Color>, val core: Color, val deep: Color)

private fun toneFor(mood: OrbMood): OrbTone = when (mood) {
    OrbMood.Success -> OrbTone(
        glow = Palette.OrbMane,
        swirl = listOf(Palette.OrbMane, Color(0xFFFFE3A3), Palette.OrbIris, Palette.OrbMane),
        core = Color(0xFFFFFBEF),
        deep = Color(0xFF7A4E0B),
    )
    OrbMood.Error -> OrbTone(
        glow = Color(0xFFFF6B81),
        swirl = listOf(Color(0xFFFF6B81), Palette.OrbRose, Color(0xFF9A6BFF), Color(0xFFFF6B81)),
        core = Color(0xFFFFF1F4),
        deep = Color(0xFF6B1430),
    )
    OrbMood.Offline -> OrbTone(
        glow = Color(0xFF6E6A85),
        swirl = listOf(Color(0xFF8C88A6), Color(0xFF5B5775), Color(0xFF9D99B8), Color(0xFF8C88A6)),
        core = Color(0xFFE9E7F2),
        deep = Color(0xFF26243A),
    )
    OrbMood.Waiting -> OrbTone(
        glow = Color(0xFFFFB84D),
        swirl = listOf(Palette.OrbIris, Color(0xFFFFB84D), Palette.OrbAzure, Palette.OrbIris),
        core = Palette.OrbCore,
        deep = Color(0xFF2D2170),
    )
    else -> OrbTone(
        glow = Palette.OrbIris,
        swirl = listOf(Palette.OrbIris, Palette.OrbAzure, Palette.OrbRose, Palette.OrbIris),
        core = Palette.OrbCore,
        deep = Color(0xFF2A1F86),
    )
}

/**
 * Pony's presence. Idle it breathes; listening it follows the owner's voice
 * ([level] 0–1); thinking and working it orbits; success flashes gold.
 */
@Composable
fun PresenceOrb(
    mood: OrbMood,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    level: Float = 0f,
    description: String? = null,
) {
    val still = LocalStillFrame.current || LocalReduceMotion.current
    val tone = toneFor(mood)
    val glow by animateColorAsState(tone.glow, Motion.fade(Motion.SLOW), label = "orb-glow")
    val core by animateColorAsState(tone.core, Motion.fade(Motion.SLOW), label = "orb-core")
    val deep by animateColorAsState(tone.deep, Motion.fade(Motion.SLOW), label = "orb-deep")
    val s0 by animateColorAsState(tone.swirl[0], Motion.fade(Motion.SLOW), label = "orb-s0")
    val s1 by animateColorAsState(tone.swirl[1], Motion.fade(Motion.SLOW), label = "orb-s1")
    val s2 by animateColorAsState(tone.swirl[2], Motion.fade(Motion.SLOW), label = "orb-s2")
    val voice by animateFloatAsState(if (mood == OrbMood.Listening) level.coerceIn(0f, 1f) else 0f, Motion.glide(), label = "orb-voice")
    val pop by animateFloatAsState(if (mood == OrbMood.Success) 1.06f else 1f, Motion.bounce(), label = "orb-pop")

    val phase: Float
    val breath: Float
    val ripple: Float
    if (still) {
        phase = 38f
        breath = 0.5f
        ripple = 0.42f
    } else {
        val transition = rememberInfiniteTransition(label = "orb")
        val spin = when (mood) {
            OrbMood.Thinking -> 2_600
            OrbMood.Working -> 4_200
            OrbMood.Listening -> 7_000
            OrbMood.Offline -> 26_000
            else -> 14_000
        }
        val p by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(spin, easing = LinearEasing)), label = "orb-phase")
        val b by transition.animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(if (mood == OrbMood.Listening) 1_400 else 4_200, easing = Motion.Emphasized), RepeatMode.Reverse),
            label = "orb-breath",
        )
        val r by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2_200, easing = LinearEasing)), label = "orb-ripple")
        phase = p
        breath = b
        ripple = r
    }

    val semantics = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.size(size).then(semantics)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val half = this.size.minDimension / 2f
        val body = half * 0.62f * pop * (1f + 0.035f * (breath - 0.5f) * 2f + 0.14f * voice)
        drawGlow(center, half, body, glow, mood, breath, voice)
        if (mood == OrbMood.Listening) drawRipples(center, body, glow, ripple, voice)
        drawBody(center, body, core, deep, listOf(s0, s1, s2, s0), phase)
        if (mood == OrbMood.Thinking || mood == OrbMood.Working || mood == OrbMood.Waiting) {
            drawOrbit(center, body, glow, phase, mood)
        }
    }
}

private fun DrawScope.drawGlow(center: Offset, half: Float, body: Float, glow: Color, mood: OrbMood, breath: Float, voice: Float) {
    val strength = when (mood) {
        OrbMood.Offline -> 0.18f
        OrbMood.Idle -> 0.34f + 0.08f * breath
        OrbMood.Success -> 0.55f
        else -> 0.42f + 0.1f * breath + 0.25f * voice
    }
    drawCircle(
        brush = Brush.radialGradient(
            0f to glow.copy(alpha = strength),
            0.45f to glow.copy(alpha = strength * 0.45f),
            1f to Color.Transparent,
            center = center,
            radius = half,
        ),
        radius = half,
        center = center,
    )
    drawCircle(
        brush = Brush.radialGradient(
            0.6f to glow.copy(alpha = strength * 0.9f),
            1f to Color.Transparent,
            center = center,
            radius = body * 1.35f,
        ),
        radius = body * 1.35f,
        center = center,
    )
}

private fun DrawScope.drawRipples(center: Offset, body: Float, glow: Color, ripple: Float, voice: Float) {
    for (i in 0 until 3) {
        val t = (ripple + i / 3f) % 1f
        val radius = body * (1.05f + t * (0.55f + 0.35f * voice))
        drawCircle(
            color = glow.copy(alpha = (1f - t) * (0.28f + 0.3f * voice)),
            radius = radius,
            center = center,
            style = Stroke(width = body * 0.025f + 1.5f),
        )
    }
}

/** Soft color fields that drift inside the sphere, like light moving through glass. */
private data class Field(val speed: Float, val start: Float, val distance: Float, val size: Float, val alpha: Float)

private val FIELDS = listOf(
    Field(speed = 1f, start = 200f, distance = 0.42f, size = 0.95f, alpha = 0.78f),
    Field(speed = -0.7f, start = 20f, distance = 0.46f, size = 0.85f, alpha = 0.62f),
    Field(speed = 0.45f, start = 110f, distance = 0.3f, size = 0.7f, alpha = 0.4f),
)

private fun DrawScope.drawBody(center: Offset, body: Float, core: Color, deep: Color, swirl: List<Color>, phase: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to core,
            0.34f to lerp(core, swirl[0], 0.55f),
            0.78f to swirl[0],
            1f to deep,
            center = center + Offset(-body * 0.28f, -body * 0.34f),
            radius = body * 1.55f,
        ),
        radius = body,
        center = center,
    )
    val sphere = Path().apply { addOval(Rect(center, body)) }
    clipPath(sphere) {
        FIELDS.forEachIndexed { i, field ->
            val angle = (field.start + phase * field.speed) * PI.toFloat() / 180f
            val spot = center + Offset(cos(angle) * body * field.distance, sin(angle) * body * field.distance)
            val tint = if (i == 2) lerp(core, swirl[1], 0.5f) else swirl[i + 1]
            drawCircle(
                brush = Brush.radialGradient(
                    0f to tint.copy(alpha = field.alpha),
                    0.5f to tint.copy(alpha = field.alpha * 0.45f),
                    1f to Color.Transparent,
                    center = spot,
                    radius = body * field.size,
                ),
                radius = body * field.size,
                center = spot,
            )
        }
    }
    drawCircle(
        brush = Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to deep.copy(alpha = 0.72f),
            center = center,
            radius = body,
        ),
        radius = body,
        center = center,
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.42f),
            0.45f to Color.White.copy(alpha = 0.12f),
            1f to Color.Transparent,
            center = center + Offset(-body * 0.34f, -body * 0.4f),
            radius = body * 0.5f,
        ),
        radius = body * 0.5f,
        center = center + Offset(-body * 0.34f, -body * 0.4f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            0.82f to Color.Transparent,
            1f to Color.White.copy(alpha = 0.16f),
            center = center,
            radius = body,
        ),
        radius = body,
        center = center,
    )
}

private fun DrawScope.drawOrbit(center: Offset, body: Float, glow: Color, phase: Float, mood: OrbMood) {
    val count = if (mood == OrbMood.Thinking) 3 else 2
    val radius = body * 1.22f
    for (i in 0 until count) {
        val angle = (phase * (if (mood == OrbMood.Thinking) 1.6f else 1f) + i * 360f / count) * PI.toFloat() / 180f
        val dot = center + Offset(cos(angle) * radius, sin(angle) * radius * 0.92f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.95f),
                0.4f to glow.copy(alpha = 0.8f),
                1f to Color.Transparent,
                center = dot,
                radius = body * 0.12f,
            ),
            radius = body * 0.12f,
            center = dot,
        )
    }
}
