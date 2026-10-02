package app.pony.companion.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pony.companion.ui.theme.LocalReduceMotion
import app.pony.companion.ui.theme.LocalStillFrame
import app.pony.companion.ui.theme.Motion
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Shapes
import app.pony.companion.ui.theme.Space
import kotlinx.coroutines.delay

@Composable
fun Spinner(color: Color, size: Dp = 20.dp, stroke: Dp = 2.dp) {
    val still = LocalStillFrame.current
    val angle = if (still) {
        120f
    } else {
        val t = rememberInfiniteTransition(label = "spinner")
        val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin")
        a
    }
    Canvas(Modifier.size(size)) {
        val w = stroke.toPx()
        drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(w))
        drawArc(color, angle, 110f, false, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Loading placeholder with a slow sheen, shaped like the content it stands in for. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = Shapes.chip) {
    val colors = Pony.colors
    val still = LocalStillFrame.current || LocalReduceMotion.current
    val sweep = if (still) {
        0.35f
    } else {
        val t = rememberInfiniteTransition(label = "skeleton")
        val s by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1_400, easing = Motion.Emphasized)), label = "sheen")
        s
    }
    Box(
        modifier
            .clip(shape)
            .background(colors.surfaceHigh)
            .drawWithContent {
                drawContent()
                val x = size.width * sweep
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = if (colors.dark) 0.06f else 0.5f), Color.Transparent),
                        start = Offset(x - size.width * 0.4f, 0f),
                        end = Offset(x + size.width * 0.4f, size.height),
                    ),
                )
            },
    )
}

/** A status dot and label. When [live], soft rings pulse out of the dot. */
@Composable
fun StatusPill(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: String? = null,
) {
    val colors = toneColors(tone)
    val still = LocalStillFrame.current || LocalReduceMotion.current
    val pulse = if (!live || still) {
        0.35f
    } else {
        val t = rememberInfiniteTransition(label = "pulse")
        val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing)), label = "pulse-t")
        p
    }
    Row(
        modifier
            .heightIn(min = 40.dp)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick, label = text, pressedScale = 0.96f) else Modifier)
            .clip(Shapes.control)
            .background(colors.soft)
            .border(1.dp, colors.fg.copy(alpha = 0.22f), Shapes.control)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.fg, modifier = Modifier.size(16.dp))
        } else {
            Canvas(Modifier.size(14.dp)) {
                val c = center
                if (live) {
                    drawCircle(colors.fg.copy(alpha = (1f - pulse) * 0.45f), radius = size.minDimension / 2f * (0.45f + pulse * 0.55f), center = c)
                }
                drawCircle(colors.fg, radius = size.minDimension / 2f * 0.42f, center = c)
            }
        }
        Text(text, style = Pony.type.label, color = Pony.colors.ink)
        if (trailing != null) {
            Box(Modifier.size(width = 1.dp, height = 16.dp).background(Pony.colors.hairlineStrong))
            Text(trailing, style = Pony.type.label, color = Pony.colors.iris)
            Icon(PonyIcons.ChevronRight, contentDescription = null, tint = Pony.colors.iris, modifier = Modifier.size(14.dp))
        }
    }
}

data class BannerAction(val label: String, val onClick: () -> Unit, val primary: Boolean = false, val icon: ImageVector? = null)

/** Inline status that explains what is happening and offers the fix right there. */
@Composable
fun Banner(
    title: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    actions: List<BannerAction> = emptyList(),
) {
    val colors = toneColors(tone)
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(colors.soft)
            .border(1.dp, colors.fg.copy(alpha = 0.26f), Shapes.card)
            .padding(Space.lg)
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.Top) {
            if (icon != null) Icon(icon, contentDescription = null, tint = colors.fg, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = Pony.type.bodyStrong, color = Pony.colors.ink)
                if (body != null) Text(body, style = Pony.type.bodySmall, color = Pony.colors.inkMuted)
            }
        }
        if (actions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.fillMaxWidth()) {
                actions.forEach { action ->
                    PonyButton(
                        text = action.label,
                        onClick = action.onClick,
                        tone = if (action.primary) ButtonTone.Primary else ButtonTone.Secondary,
                        icon = if (actions.size == 1) action.icon else null,
                        height = 48.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    mood: OrbMood = OrbMood.Idle,
    icon: ImageVector? = null,
    action: BannerAction? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (icon != null) IconTile(icon, Tone.Iris, size = 56.dp) else PresenceOrb(mood = mood, size = 96.dp)
        Text(title, style = Pony.type.titleSmall, color = Pony.colors.ink, textAlign = TextAlign.Center)
        Text(body, style = Pony.type.bodySmall, color = Pony.colors.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Space.xl))
        if (action != null) {
            PonyButton(action.label, action.onClick, tone = ButtonTone.Secondary, icon = action.icon, fillWidth = false, height = 48.dp)
        }
    }
}

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, size: Dp = 44.dp, color: Color = Pony.colors.iris) {
    val track = Pony.colors.hairlineStrong
    Canvas(modifier.size(size)) {
        val w = 4.dp.toPx()
        val inset = w / 2
        val arc = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
        drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arc, style = Stroke(w))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft = Offset(inset, inset), size = arc, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Fades and lifts content in on first appearance, staggered by [index]. */
@Composable
fun Modifier.enter(index: Int = 0, key: Any? = Unit): Modifier {
    val still = LocalStillFrame.current || LocalReduceMotion.current
    val progress = remember(key) { Animatable(if (still) 1f else 0f) }
    val lift = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(key) {
        if (!still) {
            delay(40L * index.coerceAtMost(10))
            progress.animateTo(1f, tween(Motion.ENTER, easing = Motion.Glide))
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * lift
    }
}

class Toaster {
    var message by mutableStateOf<String?>(null)
        private set
    private var serial by mutableStateOf(0)

    fun show(text: String) {
        message = text
        serial += 1
    }

    fun dismiss() {
        message = null
    }

    @Composable
    fun Host(modifier: Modifier = Modifier) {
        val current = message
        LaunchedEffect(serial) {
            if (current != null) {
                delay(2_800)
                dismiss()
            }
        }
        Box(modifier.fillMaxWidth().windowInsetsPadding(safeBottom()).padding(Space.gutter), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = current != null,
                enter = fadeIn(Motion.fade(Motion.QUICK)) + slideInVertically(Motion.slide) { it / 2 },
                exit = fadeOut(Motion.fade(Motion.QUICK)) + slideOutVertically(Motion.slide) { it / 2 },
            ) {
                Row(
                    Modifier
                        .clip(Shapes.control)
                        .background(Pony.colors.primaryFill)
                        .padding(horizontal = Space.xl, vertical = 14.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Icon(PonyIcons.CheckCircle, contentDescription = null, tint = Pony.colors.onPrimary, modifier = Modifier.size(18.dp))
                    Text(current ?: "", style = Pony.type.label, color = Pony.colors.onPrimary)
                }
            }
        }
    }
}

val LocalToaster = staticCompositionLocalOf { Toaster() }

@Composable
fun BoxScope.BottomFade(color: Color, height: Dp = 28.dp) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(Color.Transparent, color))),
    )
}
