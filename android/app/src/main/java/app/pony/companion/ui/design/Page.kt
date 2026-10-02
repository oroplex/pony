package app.pony.companion.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pony.companion.ui.theme.Motion
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Space
import app.pony.companion.ui.theme.SquircleShape

/**
 * Every screen's frame. The status bar sits on an opaque band so the clock,
 * signal, and battery are always legible; content scrolls between the top
 * bar and the navigation bar, never under either.
 */
@Composable
fun PonyPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    scroll: ScrollState = rememberScrollState(),
    largeTitle: Boolean = true,
    background: Color = Pony.colors.canvas,
    overlay: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Pony.colors
    val density = LocalDensity.current
    val threshold = with(density) { 44.dp.toPx() }
    val collapsed by remember(largeTitle) {
        derivedStateOf { if (!largeTitle) 1f else ((scroll.value - threshold * 0.4f) / threshold).coerceIn(0f, 1f) }
    }
    val lifted by remember { derivedStateOf { (scroll.value / threshold).coerceIn(0f, 1f) } }
    val atBottom by remember { derivedStateOf { scroll.value >= scroll.maxValue - 2 } }
    Box(modifier.fillMaxSize().background(background)) {
        Column(Modifier.fillMaxSize()) {
            StatusBarBand(background)
            TopBar(
                title = title,
                titleAlpha = collapsed,
                lift = lifted,
                onBack = onBack,
                actions = actions,
                background = background,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .windowInsetsPadding(safeHorizontal())
                        .padding(horizontal = Space.gutter)
                        .padding(bottom = if (bottomBar == null) Space.section else Space.xl),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    if (largeTitle) {
                        Column(
                            Modifier.padding(top = Space.xs, bottom = Space.sm).graphicsLayer { alpha = 1f - collapsed * 0.9f },
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            if (eyebrow != null) Text(eyebrow.uppercase(), style = Pony.type.overline, color = colors.iris)
                            Text(title, style = Pony.type.headline, color = colors.ink, modifier = Modifier.semantics { heading() })
                            if (subtitle != null) Text(subtitle, style = Pony.type.body, color = colors.inkMuted)
                        }
                    }
                    content()
                }
                if (!atBottom) BottomFade(background)
            }
            if (bottomBar == null) NavBarBand(background)
            if (bottomBar != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(background)
                        .windowInsetsPadding(safeBottom())
                        .imePadding()
                        .padding(horizontal = Space.gutter)
                        .padding(top = Space.sm, bottom = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    content = bottomBar,
                )
            }
        }
        overlay()
    }
}

@Composable
private fun TopBar(
    title: String,
    titleAlpha: Float,
    lift: Float,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    background: Color,
) {
    val colors = Pony.colors
    Column(Modifier.fillMaxWidth().background(background).windowInsetsPadding(safeHorizontal())) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(Space.bar + 4.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                PonyIconButton(PonyIcons.Back, "Back", onBack, size = 40.dp, modifier = Modifier.semantics { traversalIndex = -1f })
            } else {
                Spacer(Modifier.width(Space.md))
            }
            Text(
                title,
                style = Pony.type.titleSmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Space.sm)
                    .graphicsLayer {
                        alpha = titleAlpha
                        translationY = (1f - titleAlpha) * 12f
                    },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).graphicsLayer { alpha = lift }.background(colors.hairline))
    }
}

/**
 * A sheet that rises over the current screen. It lives inside the screen
 * (not a separate window) so it shares the screen's insets and theme.
 */
@Composable
fun PonySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Pony.colors
    if (visible) BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(Motion.fade(Motion.QUICK)), exit = fadeOut(Motion.fade(Motion.QUICK))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .pressable(onClick = onDismiss, label = "Close", pressedScale = 1f, haptic = Haptic.NONE),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(Motion.slide) { it } + fadeIn(Motion.fade(Motion.QUICK)),
            exit = slideOutVertically(Motion.slide) { it } + fadeOut(Motion.fade(Motion.QUICK)),
        ) {
            val shape = SquircleShape(34.dp)
            Column(
                modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface)
                    .windowInsetsPadding(safeBottom())
                    .padding(horizontal = Space.gutter)
                    .padding(top = Space.md, bottom = Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(SquircleShape(3.dp, 0f))
                        .background(colors.hairlineStrong),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = Pony.type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
                    if (subtitle != null) Text(subtitle, style = Pony.type.bodySmall, color = colors.inkMuted)
                }
                content()
            }
        }
    }
}
