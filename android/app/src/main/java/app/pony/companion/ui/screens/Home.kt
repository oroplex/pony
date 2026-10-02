package app.pony.companion.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.ui.AskBar
import app.pony.companion.ui.AssistantLink
import app.pony.companion.ui.Badge
import app.pony.companion.ui.BadgeAction
import app.pony.companion.ui.LiveTaskCard
import app.pony.companion.ui.Suggestion
import app.pony.companion.ui.TaskRow
import app.pony.companion.ui.ambientGlow
import app.pony.companion.ui.design.Banner
import app.pony.companion.ui.design.BannerAction
import app.pony.companion.ui.design.BrandMark
import app.pony.companion.ui.design.EmptyState
import app.pony.companion.ui.design.OrbMood
import app.pony.companion.ui.design.PonyIconButton
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.design.PresenceOrb
import app.pony.companion.ui.design.SectionHeader
import app.pony.companion.ui.design.Skeleton
import app.pony.companion.ui.design.StatusBarBand
import app.pony.companion.ui.design.StatusPill
import app.pony.companion.ui.design.SuggestionChip
import app.pony.companion.ui.design.Tone
import app.pony.companion.ui.design.BottomFade
import app.pony.companion.ui.design.NavBarBand
import app.pony.companion.ui.design.enter
import app.pony.companion.ui.design.pressable
import app.pony.companion.ui.design.safeHorizontal
import app.pony.companion.ui.iconFor
import app.pony.companion.ui.shared
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Serif
import app.pony.companion.ui.theme.Shapes
import app.pony.companion.ui.theme.Space

data class HomeState(
    val greeting: String,
    val subline: String,
    val badge: Badge,
    val assistant: AssistantLink? = null,
    val mood: OrbMood,
    val live: TaskRecord?,
    val recent: List<TaskRecord>,
    val suggestions: List<Suggestion>,
    val needsSetup: Boolean,
    val loading: Boolean,
    val version: String,
    val now: Long,
    val updateReady: Boolean = false,
)

@Composable
fun HomeScreen(
    state: HomeState,
    onAsk: () -> Unit,
    onListen: () -> Unit,
    onSuggestion: (Suggestion) -> Unit,
    onBadge: (BadgeAction) -> Unit,
    onStop: () -> Unit,
    onOpenTask: (String) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onSetup: () -> Unit,
) {
    val colors = Pony.colors
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize()) {
            StatusBarBand(colors.canvas)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .ambientGlow(colors.canvasGlow, center = 0.2f, radius = 0.85f)
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(safeHorizontal())
                        .padding(horizontal = Space.gutter)
                        .padding(bottom = Space.section),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    Row(Modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(size = 36.dp)
                        Text(
                            "Pony",
                            style = Pony.type.title.copy(fontFamily = Serif, fontSize = Pony.type.headline.fontSize * 0.8f),
                            color = colors.ink,
                            modifier = Modifier.padding(start = 10.dp).weight(1f).semantics { heading() },
                        )
                        PonyIconButton(PonyIcons.History, "Task history", onHistory)
                        PonyIconButton(PonyIcons.Settings, "Settings", onSettings, badge = state.updateReady)
                    }
                    val badge = state.badge
                    StatusPill(
                        text = badge.title,
                        tone = badge.tone,
                        live = badge.live,
                        trailing = when (badge.action) {
                            BadgeAction.RECONNECT -> "Reconnect"
                            BadgeAction.RETRY -> "Retry now"
                            BadgeAction.CONNECT -> "Connect"
                            BadgeAction.DISCONNECT -> "Disconnect"
                            BadgeAction.NONE -> null
                        },
                        onClick = if (badge.action != BadgeAction.NONE) ({ onBadge(badge.action) }) else null,
                        modifier = Modifier.enter(0),
                    )
                    state.assistant?.let { link ->
                        AssistantChip(link, onClick = { onBadge(link.action) }, modifier = Modifier.enter(0))
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(top = Space.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        PresenceOrb(state.mood, size = 188.dp, modifier = Modifier.shared("orb"), description = "Pony")
                        Text(
                            "${state.greeting}.",
                            style = Pony.type.display,
                            color = colors.ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.enter(1),
                        )
                        AnimatedContent(targetState = state.subline, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(140)) }, label = "subline") { line ->
                            Text(line, style = Pony.type.body, color = colors.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = Space.lg))
                        }
                    }
                    AnimatedContent(targetState = state.live?.id, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }, label = "entry") { liveId ->
                        val live = state.live
                        if (liveId != null && live != null) {
                            LiveTaskCard(live, onStop = onStop, onOpen = { onOpenTask(live.id) })
                        } else {
                            AskBar(onOpen = onAsk, onMic = onListen, modifier = Modifier.enter(2))
                        }
                    }
                    if (state.suggestions.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).enter(3),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            state.suggestions.forEach { suggestion ->
                                SuggestionChip(suggestion.label, { onSuggestion(suggestion) }, icon = iconFor(suggestion.icon))
                            }
                        }
                    }
                    if (state.needsSetup) {
                        Banner(
                            "Finish setup",
                            Tone.Warning,
                            body = "Turn on Pony control so Pony can use the screen for you.",
                            icon = PonyIcons.Accessibility,
                            actions = listOf(BannerAction("Finish setup", onSetup, primary = true)),
                            modifier = Modifier.enter(4),
                        )
                    }
                    SectionHeader("Recent", action = if (state.recent.isNotEmpty()) "See all" else null, onAction = onHistory)
                    when {
                        state.loading -> repeat(3) {
                            Skeleton(Modifier.fillMaxWidth().height(76.dp), shape = Shapes.card)
                        }
                        state.recent.isEmpty() -> EmptyState(
                            title = "Nothing yet",
                            body = "Ask Pony for something and it will show up here, step by step.",
                            icon = PonyIcons.History,
                        )
                        else -> state.recent.take(4).forEachIndexed { index, task ->
                            TaskRow(task, state.now, onClick = { onOpenTask(task.id) }, index = index)
                        }
                    }
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "Pony ${state.version}",
                        style = Pony.type.caption,
                        color = colors.inkFaint,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                BottomFade(colors.canvas)
            }
            NavBarBand(colors.canvas)
        }
    }
}

/** A quiet, optional row under the ready pill: links an assistant without ever implying Pony is broken. */
@Composable
private fun AssistantChip(link: AssistantLink, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Pony.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(Shapes.control)
            .pressable(onClick = onClick, label = link.label, pressedScale = 0.98f)
            .background(colors.surfaceHigh)
            .border(1.dp, colors.hairline, Shapes.control)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(PonyIcons.Link, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(link.label, style = Pony.type.label, color = colors.ink)
            Text(link.detail, style = Pony.type.caption, color = colors.inkMuted)
        }
        Icon(PonyIcons.ChevronRight, contentDescription = null, tint = colors.iris, modifier = Modifier.size(16.dp))
    }
}
