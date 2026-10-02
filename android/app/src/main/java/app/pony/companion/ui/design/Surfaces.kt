package app.pony.companion.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Shapes
import app.pony.companion.ui.theme.Space

enum class Tone { Neutral, Iris, Success, Warning, Danger, Mane }

data class ToneColors(val fg: Color, val soft: Color)

@Composable
fun toneColors(tone: Tone): ToneColors {
    val c = Pony.colors
    return when (tone) {
        Tone.Neutral -> ToneColors(c.inkMuted, c.surfaceHigh)
        Tone.Iris -> ToneColors(c.iris, c.irisSoft)
        Tone.Success -> ToneColors(c.success, c.successSoft)
        Tone.Warning -> ToneColors(c.warning, c.warningSoft)
        Tone.Danger -> ToneColors(c.danger, c.dangerSoft)
        Tone.Mane -> ToneColors(c.mane, c.maneSoft)
    }
}

/**
 * Pony's surface: a squircle defined by a hairline and a faint top light
 * rather than a heavy shadow. In the light theme it floats on a soft shadow.
 */
@Composable
fun PonyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    padding: PaddingValues = PaddingValues(Space.xl),
    shape: Shape = Shapes.card,
    color: Color = Pony.colors.surface,
    border: Color = Pony.colors.hairline,
    spacing: Dp = Space.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Pony.colors
    val lift = if (colors.dark) {
        Modifier
    } else {
        Modifier.shadow(14.dp, shape, ambientColor = colors.shadow.copy(alpha = 0.10f), spotColor = colors.shadow.copy(alpha = 0.14f))
    }
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick = onClick, label = onClickLabel, pressedScale = 0.98f) else Modifier)
            .then(lift)
            .clip(shape)
            .background(color)
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = if (colors.dark) 0.045f else 0f), 0.5f to Color.Transparent))
            .border(1.dp, border, shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = Pony.type.overline,
            color = Pony.colors.inkFaint,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null && onAction != null) {
            TextAction(action, onAction, icon = PonyIcons.ChevronRight)
        }
    }
}

@Composable
fun IconTile(icon: ImageVector, tone: Tone, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val colors = toneColors(tone)
    Box(
        modifier.size(size).clip(Shapes.icon).background(colors.soft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.fg, modifier = Modifier.size(size * 0.52f))
    }
}

sealed class Trailing {
    data object Chevron : Trailing()
    data class Value(val text: String) : Trailing()
    data class Switch(val checked: Boolean) : Trailing()
    data class Badge(val text: String, val tone: Tone) : Trailing()
    data object None : Trailing()
}

/** One row in a grouped list. The whole row is the touch target. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tone: Tone = Tone.Iris,
    trailing: Trailing = Trailing.Chevron,
    onClick: (() -> Unit)? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val colors = Pony.colors
    val interactive = when (trailing) {
        is Trailing.Switch -> Modifier.pressable(
            onClick = { onToggle?.invoke(!trailing.checked) },
            enabled = enabled && onToggle != null,
            role = Role.Switch,
            label = title,
            pressedScale = 0.985f,
            haptic = if (trailing.checked) Haptic.TOGGLE_OFF else Haptic.TOGGLE_ON,
        ).semantics(mergeDescendants = true) {}
        else -> if (onClick != null) Modifier.pressable(onClick = onClick, enabled = enabled, label = title, pressedScale = 0.985f) else Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(interactive)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (icon != null) IconTile(icon, if (danger) Tone.Danger else tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Pony.type.bodyStrong, color = if (danger) colors.danger else colors.ink)
            if (subtitle != null) Text(subtitle, style = Pony.type.bodySmall, color = colors.inkMuted)
        }
        when (trailing) {
            Trailing.Chevron -> Icon(PonyIcons.ChevronRight, contentDescription = null, tint = colors.inkFaint, modifier = Modifier.size(20.dp))
            is Trailing.Value -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trailing.text, style = Pony.type.bodySmall, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(PonyIcons.ChevronRight, contentDescription = null, tint = colors.inkFaint, modifier = Modifier.size(20.dp))
            }
            is Trailing.Switch -> PonyToggle(checked = trailing.checked, onCheckedChange = null, enabled = enabled)
            is Trailing.Badge -> Tag(trailing.text, trailing.tone)
            Trailing.None -> Unit
        }
    }
}

/** Rows on one surface, separated by inset hairlines. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, rows: List<@Composable () -> Unit>) {
    PonyCard(modifier, padding = PaddingValues(vertical = Space.xs), spacing = 0.dp) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(start = 64.dp, end = Space.lg)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Pony.colors.hairline),
                )
            }
            row()
        }
    }
}

@Composable
fun Tag(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val colors = toneColors(tone)
    Box(
        modifier
            .clip(Shapes.chip)
            .background(colors.soft)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = Pony.type.caption, color = colors.fg)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Pony.colors.hairline))
}
