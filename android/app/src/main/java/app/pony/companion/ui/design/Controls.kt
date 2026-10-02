package app.pony.companion.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pony.companion.ui.theme.Motion
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Shapes
import app.pony.companion.ui.theme.Space
import app.pony.companion.ui.theme.SquircleShape

/**
 * Pony's switch. Off is an outlined track with a solid knob, both at 3:1 or
 * better against the surface, so an off switch never reads as disabled.
 */
@Composable
fun PonyToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Pony.colors
    val track by animateColorAsState(if (checked) colors.controlOn else Color.Transparent, Motion.fade(Motion.QUICK), label = "toggle-track")
    val knobColor by animateColorAsState(if (checked) Color.White else colors.control, Motion.fade(Motion.QUICK), label = "toggle-knob")
    val knob by animateDpAsState(if (checked) 24.dp else 18.dp, Motion.bounce(), label = "toggle-size")
    val offset by animateDpAsState(if (checked) 26.dp else 7.dp, Motion.bounce(), label = "toggle-offset")
    val shape = SquircleShape(16.dp, smoothing = 0.3f)
    val interactive = if (onCheckedChange != null) {
        Modifier.pressable(
            onClick = { onCheckedChange(!checked) },
            enabled = enabled,
            role = Role.Switch,
            pressedScale = 0.92f,
            haptic = if (checked) Haptic.TOGGLE_OFF else Haptic.TOGGLE_ON,
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .size(width = 54.dp, height = 32.dp)
            .then(interactive)
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .clip(shape)
            .background(track)
            .border(2.dp, if (checked) colors.controlOn else colors.control, shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(knob)
                .clip(SquircleShape(knob / 2, smoothing = 0f))
                .background(if (enabled) knobColor else knobColor.copy(alpha = 0.5f)),
        )
    }
}

/** A switch with its label; the whole row toggles. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tone: Tone = Tone.Iris,
    enabled: Boolean = true,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        tone = tone,
        trailing = Trailing.Switch(checked),
        onToggle = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
) {
    val colors = Pony.colors
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(Shapes.control)
            .background(colors.surfaceHigh)
            .border(1.dp, colors.hairline, Shapes.control)
            .padding(4.dp),
    ) {
        val cell = maxWidth / options.size
        val x by animateDpAsState(cell * index, Motion.glide(), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(cell)
                .fillMaxHeight()
                .then(if (colors.dark) Modifier else Modifier.shadow(4.dp, Shapes.chip, ambientColor = colors.shadow.copy(alpha = 0.12f)))
                .clip(Shapes.chip)
                .background(if (colors.dark) colors.surfaceHighest else colors.surface)
                .border(1.dp, colors.hairlineStrong, Shapes.chip),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { (value, label) ->
                val on = value == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(onClick = { if (!on) onSelect(value) }, role = Role.Tab, label = label, haptic = Haptic.SELECT, pressedScale = 0.95f)
                        .semantics { this.selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = Pony.type.label,
                        color = if (on) colors.ink else colors.inkMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun PonyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    error: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = Shapes.control,
    container: Color = Pony.colors.surfaceHigh,
) {
    val colors = Pony.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring by animateColorAsState(
        when {
            error != null -> colors.danger
            focused -> colors.iris
            else -> colors.hairlineStrong
        },
        Motion.fade(Motion.QUICK),
        label = "field-ring",
    )
    val width by animateDpAsState(if (focused || error != null) 1.5.dp else 1.dp, Motion.snap(), label = "field-width")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = Pony.type.label, color = colors.inkMuted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            textStyle = Pony.type.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.iris),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    if (label != null) contentDescription = label
                    if (error != null) error(error)
                },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(shape)
                        .background(container)
                        .border(width, ring, shape)
                        .padding(horizontal = Space.lg, vertical = 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = Pony.type.body, color = colors.inkFaint)
                        inner()
                    }
                    if (trailing != null) {
                        Box(Modifier.padding(start = Space.sm)) { trailing() }
                    }
                }
            },
        )
        if (error != null) Text(error, style = Pony.type.bodySmall, color = colors.danger)
    }
}

@Composable
fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
) {
    val colors = Pony.colors
    Row(
        modifier
            .heightIn(min = Space.touch)
            .pressable(onClick = onClick, label = text, haptic = Haptic.SELECT, pressedScale = 0.95f)
            .clip(Shapes.chip)
            .background(if (selected) colors.irisSoft else colors.surface)
            .border(1.dp, if (selected) colors.iris.copy(alpha = 0.5f) else colors.hairlineStrong, Shapes.chip)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.iris, modifier = Modifier.size(18.dp))
        Text(text, style = Pony.type.label, color = colors.ink, maxLines = 1)
    }
}

/** A large, clear consent control: the whole row is the checkbox. */
@Composable
fun ConsentRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = Pony.colors
    val fill by animateColorAsState(if (checked) colors.controlOn else Color.Transparent, Motion.fade(Motion.QUICK), label = "consent")
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .pressable(onClick = { onChange(!checked) }, role = Role.Checkbox, label = text, haptic = if (checked) Haptic.TOGGLE_OFF else Haptic.TOGGLE_ON, pressedScale = 0.985f)
            .semantics(mergeDescendants = true) { stateDescription = if (checked) "Checked" else "Not checked" }
            .background(colors.surface)
            .border(1.dp, if (checked) colors.iris.copy(alpha = 0.55f) else colors.hairlineStrong, Shapes.card)
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(SquircleShape(8.dp))
                .background(fill)
                .border(2.dp, if (checked) colors.controlOn else colors.control, SquircleShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(PonyIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(text, style = Pony.type.body, color = colors.ink, modifier = Modifier.weight(1f))
    }
}
