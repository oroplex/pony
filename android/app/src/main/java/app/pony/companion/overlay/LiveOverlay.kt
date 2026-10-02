package app.pony.companion.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pony.companion.display.CallGuard
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskRuntime
import app.pony.companion.tasks.TaskState
import app.pony.companion.ui.design.ButtonTone
import app.pony.companion.ui.design.Haptic
import app.pony.companion.ui.design.OrbMood
import app.pony.companion.ui.design.PonyButton
import app.pony.companion.ui.design.PonyIconButton
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.design.PresenceOrb
import app.pony.companion.ui.design.rememberHaptics
import app.pony.companion.ui.theme.Motion
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.PonyTheme
import app.pony.companion.ui.theme.SquircleShape
import app.pony.companion.voice.NoticeAction
import app.pony.companion.voice.VoiceController
import app.pony.companion.voice.VoiceUi
import kotlinx.coroutines.delay

/** What the pill should say right now. */
sealed class PillState {
    data class Confirm(val text: String) : PillState()
    data class Asking(val text: String, val heard: String) : PillState()
    data class Note(val title: String, val body: String, val action: NoticeAction?) : PillState()
    data class Listening(val heard: String, val level: Float) : PillState()
    data class Working(val task: TaskRecord) : PillState()
    data class Finished(val task: TaskRecord) : PillState()
    data object Hidden : PillState()
}

fun pillState(voice: VoiceUi, task: TaskRecord?, now: Long): PillState {
    voice.prompt?.let { return if (it.confirm) PillState.Confirm(it.text) else PillState.Asking(it.text, voice.transcript) }
    voice.notice?.let { return PillState.Note(it.title, it.body, it.action) }
    if (voice.listening) return PillState.Listening(voice.transcript, voice.level)
    if (task != null && task.running) return PillState.Working(task)
    if (task != null && task.endedAt != null && now - task.endedAt < 3_500) return PillState.Finished(task)
    return PillState.Hidden
}

/**
 * Whether the pill should take touches. It only does while it's offering the
 * owner something to answer; otherwise it passes taps through to the app below,
 * so it never covers an app's top bar or blocks a protected switch.
 */
fun pillTakesTouch(state: PillState): Boolean = when (state) {
    is PillState.Confirm, is PillState.Note, is PillState.Listening -> true
    else -> false
}

@Composable
fun LiveOverlay(onCollapsed: () -> Unit) {
    val voice by VoiceController.voice.collectAsState()
    val task by TaskRuntime.live.collectAsState()
    val ponyVisible by AppVisibility.visible.collectAsState()
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var inCall by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            inCall = CallGuard.inCall(context)
            delay(500)
        }
    }
    val live = pillState(voice, task, now)
    val state = if (ponyVisible) PillState.Hidden else live
    LaunchedEffect(pillTakesTouch(state)) {
        OverlayHost.setInteractive(pillTakesTouch(state))
    }
    LaunchedEffect(live is PillState.Hidden) {
        if (live is PillState.Hidden) {
            delay(1_200)
            OverlayHost.hide()
            onCollapsed()
        }
    }
    PonyTheme(dark = true) {
        OverlayPill(state = state, minimized = inCall && state is PillState.Working)
    }
}

@Composable
fun OverlayPill(state: PillState, minimized: Boolean) {
    val colors = Pony.colors
    val haptics = rememberHaptics()
    AnimatedVisibility(
        visible = state !is PillState.Hidden,
        enter = fadeIn(Motion.fade(Motion.QUICK)) + slideInVertically(Motion.slide) { -it / 2 } + scaleIn(initialScale = 0.92f),
        exit = fadeOut(Motion.fade(Motion.QUICK)) + slideOutVertically(Motion.slide) { -it / 2 } + scaleOut(targetScale = 0.92f),
    ) {
        val shape = SquircleShape(if (state is PillState.Working) 30.dp else 26.dp)
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .widthIn(max = if (minimized) 200.dp else 380.dp)
                .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f))
                .clip(shape)
                .background(Color(0xF20E0D1A))
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) },
                contentKey = { it::class },
                label = "pill",
            ) { current ->
                when (current) {
                    is PillState.Working -> WorkingPill(current.task, minimized)
                    is PillState.Finished -> FinishedPill(current.task)
                    is PillState.Listening -> ListeningPill(current)
                    is PillState.Confirm -> ConfirmCard(current.text) { yes ->
                        haptics.perform(if (yes) Haptic.CONFIRM else Haptic.REJECT)
                        VoiceController.answer(yes)
                    }
                    is PillState.Asking -> AskingCard(current)
                    is PillState.Note -> NoteCard(current)
                    PillState.Hidden -> Box(Modifier.size(1.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkingPill(task: TaskRecord, minimized: Boolean) {
    val colors = Pony.colors
    val mood = if (task.state == TaskState.Waiting || task.state == TaskState.Queued) OrbMood.Waiting else OrbMood.Working
    // The pill passes touches through to the app Pony is driving, so it carries no
    // buttons here (a tap would fall through onto the app). Stop stays one tap away
    // on Home and by voice; this is just a live status.
    Row(
        Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PresenceOrb(mood = mood, size = 36.dp)
        if (!minimized) {
            Column(Modifier.widthIn(min = 120.dp, max = 250.dp)) {
                Text(
                    task.brainLabel,
                    style = Pony.type.caption,
                    color = colors.inkMuted,
                    maxLines = 1,
                )
                AnimatedContent(
                    targetState = task.headline ?: task.text,
                    transitionSpec = {
                        (slideInVertically(Motion.slide) { it / 2 } + fadeIn(tween(160))) togetherWith
                            (slideOutVertically(Motion.slide) { -it / 2 } + fadeOut(tween(120)))
                    },
                    label = "step",
                ) { line ->
                    Text(line, style = Pony.type.label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FinishedPill(task: TaskRecord) {
    val colors = Pony.colors
    val (mood, tint, icon) = when (task.state) {
        TaskState.Done -> Triple(OrbMood.Success, colors.mane, PonyIcons.CheckCircle)
        TaskState.Stopped -> Triple(OrbMood.Idle, colors.inkMuted, PonyIcons.Stop)
        else -> Triple(OrbMood.Error, colors.danger, PonyIcons.AlertCircle)
    }
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PresenceOrb(mood = mood, size = 34.dp)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            task.outcome ?: "Done",
            style = Pony.type.label,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 260.dp),
        )
    }
}

@Composable
private fun ListeningPill(state: PillState.Listening) {
    val colors = Pony.colors
    Row(
        Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PresenceOrb(mood = OrbMood.Listening, level = state.level, size = 40.dp)
        Column(Modifier.widthIn(min = 140.dp, max = 240.dp)) {
            Text("Listening", style = Pony.type.caption, color = colors.iris)
            Text(
                state.heard.ifBlank { "Say what Pony should do" },
                style = Pony.type.label,
                color = if (state.heard.isBlank()) colors.inkMuted else colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PonyIconButton(PonyIcons.Close, "Stop listening", { VoiceController.cancelListening() }, size = 40.dp)
    }
}

@Composable
private fun ConfirmCard(text: String, onAnswer: (Boolean) -> Unit) {
    val colors = Pony.colors
    Column(Modifier.padding(16.dp).widthIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PresenceOrb(mood = OrbMood.Waiting, size = 32.dp)
            Text("Pony needs your OK", style = Pony.type.caption, color = colors.mane)
        }
        Text(text, style = Pony.type.titleSmall, color = colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PonyButton("No", { onAnswer(false) }, tone = ButtonTone.Secondary, height = 48.dp, modifier = Modifier.weight(1f), haptic = Haptic.NONE)
            PonyButton("Yes", { onAnswer(true) }, tone = ButtonTone.Primary, height = 48.dp, modifier = Modifier.weight(1f), haptic = Haptic.NONE)
        }
        Text("Or say yes or no. Anything else counts as no.", style = Pony.type.caption, color = colors.inkMuted)
    }
}

@Composable
private fun AskingCard(state: PillState.Asking) {
    val colors = Pony.colors
    Column(Modifier.padding(16.dp).widthIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PresenceOrb(mood = OrbMood.Listening, size = 32.dp)
            Text("Pony is asking", style = Pony.type.caption, color = colors.iris)
        }
        Text(state.text, style = Pony.type.titleSmall, color = colors.ink)
        if (state.heard.isNotBlank()) Text("“${state.heard}”", style = Pony.type.bodySmall, color = colors.inkMuted)
    }
}

@Composable
private fun NoteCard(state: PillState.Note) {
    val colors = Pony.colors
    Column(Modifier.padding(16.dp).widthIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PresenceOrb(mood = if (state.action == NoticeAction.ALLOW_MIC) OrbMood.Offline else OrbMood.Idle, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(state.title, style = Pony.type.titleSmall, color = colors.ink)
                Text(state.body, style = Pony.type.bodySmall, color = colors.inkMuted)
            }
            PonyIconButton(PonyIcons.Close, "Dismiss", { VoiceController.dismissNotice() }, size = 36.dp)
        }
        when (state.action) {
            NoticeAction.ALLOW_MIC -> PonyButton("Allow microphone", { VoiceController.openPonyFor(NoticeAction.ALLOW_MIC) }, icon = PonyIcons.Mic, height = 48.dp)
            NoticeAction.OPEN_PONY -> PonyButton("Open Pony", { VoiceController.openPonyFor(NoticeAction.OPEN_PONY) }, tone = ButtonTone.Secondary, height = 48.dp)
            NoticeAction.RETRY -> PonyButton("Try again", { VoiceController.retryListening() }, icon = PonyIcons.Mic, tone = ButtonTone.Secondary, height = 48.dp)
            null -> Unit
        }
    }
}
