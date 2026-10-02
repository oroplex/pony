package app.pony.companion.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pony.companion.ui.theme.Motion
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Shapes
import app.pony.companion.ui.theme.Space
import app.pony.companion.ui.theme.SquircleShape

enum class ButtonTone { Primary, Iris, Secondary, Quiet, Danger }

@Composable
fun PonyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    haptic: Haptic = Haptic.CONFIRM,
    height: Dp = 56.dp,
    fillWidth: Boolean = true,
) {
    val colors = Pony.colors
    val (fill, content) = when (tone) {
        ButtonTone.Primary -> colors.primaryFill to colors.onPrimary
        ButtonTone.Iris -> colors.irisFill to colors.onIris
        ButtonTone.Secondary -> colors.surfaceHigh to colors.ink
        ButtonTone.Quiet -> Color.Transparent to colors.ink
        ButtonTone.Danger -> colors.dangerFill to Color.White
    }
    val animatedFill by animateColorAsState(fill, Motion.fade(), label = "button-fill")
    val shape = Shapes.control
    val base = if (fillWidth) modifier.fillMaxWidth() else modifier
    Box(
        base
            .heightIn(min = height)
            .alpha(if (enabled) 1f else 0.42f)
            .pressable(onClick = onClick, enabled = enabled && !loading, label = text, haptic = haptic)
            .clip(shape)
            .background(animatedFill)
            .then(
                when (tone) {
                    ButtonTone.Iris -> Modifier.background(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)),
                    )
                    ButtonTone.Secondary -> Modifier.border(1.dp, colors.hairlineStrong, shape)
                    else -> Modifier
                },
            )
            .semantics { if (loading) stateDescription = "Working" }
            .padding(horizontal = if (height < 56.dp) Space.md else Space.lg),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedContent(
                targetState = loading,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) },
                label = "button-icon",
            ) { busy ->
                when {
                    busy -> Spinner(color = content, size = 18.dp)
                    icon != null -> Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                    else -> Box(Modifier)
                }
            }
            Text(
                text,
                style = Pony.type.button,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

enum class IconTone { Quiet, Filled, Iris, Danger }

@Composable
fun PonyIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: IconTone = IconTone.Quiet,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    haptic: Haptic = Haptic.TAP,
    badge: Boolean = false,
) {
    val colors = Pony.colors
    val (fill, tint) = when (tone) {
        IconTone.Quiet -> colors.surfaceHigh to colors.ink
        IconTone.Filled -> colors.primaryFill to colors.onPrimary
        IconTone.Iris -> colors.irisFill to colors.onIris
        IconTone.Danger -> colors.dangerFill to Color.White
    }
    val shape = SquircleShape(size * 0.38f)
    Box(
        modifier
            .size(maxOf(size, Space.touch))
            .alpha(if (enabled) 1f else 0.42f)
            .pressable(onClick = onClick, enabled = enabled, label = description, haptic = haptic, pressedScale = 0.9f)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(fill)
                .then(if (tone == IconTone.Quiet) Modifier.border(1.dp, colors.hairline, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(9.dp)
                    .clip(SquircleShape(4.dp))
                    .background(colors.mane),
            )
        }
    }
}

/** A text action with full contrast and a 48 dp target. Never a pale, tiny link. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Pony.colors.iris,
    icon: ImageVector? = null,
    haptic: Haptic = Haptic.TAP,
) {
    Row(
        modifier
            .heightIn(min = Space.touch)
            .clip(Shapes.chip)
            .pressable(onClick = onClick, label = text, haptic = haptic)
            .padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = Pony.type.label, color = color)
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
    }
}
