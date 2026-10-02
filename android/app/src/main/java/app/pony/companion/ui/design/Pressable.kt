package app.pony.companion.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import app.pony.companion.ui.theme.Motion

/**
 * Pony's touch response: the surface settles in under the finger on a
 * spring and gives a light haptic. There is no ripple; the scale and the
 * pressed tint carry the feedback.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    label: String? = null,
    pressedScale: Float = 0.965f,
    haptic: Haptic = Haptic.TAP,
    onLongClick: (() -> Unit)? = null,
    interaction: MutableInteractionSource? = null,
): Modifier = composed {
    val source = interaction ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) pressedScale else 1f, Motion.snap(), label = "press")
    val haptics = rememberHaptics()
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClickLabel = label,
            role = role,
            onLongClick = onLongClick?.let { long -> { haptics.perform(Haptic.LONG); long() } },
            onClick = {
                haptics.perform(haptic)
                onClick()
            },
        )
}
