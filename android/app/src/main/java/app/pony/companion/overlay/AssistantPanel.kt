package app.pony.companion.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskRuntime
import app.pony.companion.tasks.TaskState
import app.pony.companion.ui.design.ButtonTone
import app.pony.companion.ui.design.Haptic
import app.pony.companion.ui.design.OrbMood
import app.pony.companion.ui.design.PonyButton
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.design.PresenceOrb
import app.pony.companion.ui.design.pressable
import app.pony.companion.ui.design.safeInsets
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.SquircleShape
import app.pony.companion.voice.NoticeAction
import app.pony.companion.voice.VoiceController
import app.pony.companion.voice.VoiceUi
import kotlinx.coroutines.delay

/**
 * What the assistant gesture and "Hey Pony" show: a card at the bottom of
 * the screen. It closes on its own a moment after the result, on Stop, on
 * Back, or when the owner taps outside it.
 */
@Composable
fun AssistantPanel(onClose: () -> Unit, openedAt: Long = System.currentTimeMillis()) {
    val voice by VoiceController.voice.collectAsState()
    val task by TaskRuntime.live.collectAsState()
    val relevant = task?.takeIf { it.createdAt >= openedAt - 1_000 }
    val idle = !voice.listening && voice.prompt == null && (relevant == null || !relevant.running)
    LaunchedEffect(idle, relevant?.state, voice.notice) {
        if (idle) {
            delay(if (relevant != null) 2_800 else if (voice.notice != null) 5_500 else 2_200)
            onClose()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Pony.colors.scrim)
            .pressable(onClick = onClose, label = "Close", pressedScale = 1f, haptic = Haptic.NONE),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(safeInsets())
                .padding(12.dp)
                .clip(SquircleShape(34.dp))
                .background(Pony.colors.surface)
                .pressable(onClick = {}, label = null, pressedScale = 1f, haptic = Haptic.NONE)
                .padding(24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PresenceOrb(mood = moodFor(voice, relevant), level = voice.level, size = 132.dp)
            AnimatedContent(
                targetState = headline(voice, relevant),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "assistant-line",
            ) { line ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(line.first, style = Pony.type.headline, color = Pony.colors.ink, textAlign = TextAlign.Center)
                    if (line.second.isNotBlank()) {
                        Text(line.second, style = Pony.type.body, color = Pony.colors.inkMuted, textAlign = TextAlign.Center)
                    }
                }
            }
            val prompt = voice.prompt
            when {
                prompt != null && prompt.confirm -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PonyButton("No", { VoiceController.answer(false) }, tone = ButtonTone.Secondary, modifier = Modifier.weight(1f))
                    PonyButton("Yes", { VoiceController.answer(true) }, modifier = Modifier.weight(1f))
                }
                voice.notice?.action == NoticeAction.ALLOW_MIC ->
                    PonyButton("Allow microphone", { VoiceController.openPonyFor(NoticeAction.ALLOW_MIC); onClose() }, icon = PonyIcons.Mic)
                relevant != null && relevant.running ->
                    PonyButton("Stop", { VoiceController.stop(); onClose() }, tone = ButtonTone.Danger, icon = PonyIcons.Stop, haptic = Haptic.STOP)
                voice.listening ->
                    PonyButton("Cancel", { VoiceController.cancelListening(); onClose() }, tone = ButtonTone.Secondary)
                else -> PonyButton("Close", onClose, tone = ButtonTone.Secondary)
            }
        }
    }
}

private fun moodFor(voice: VoiceUi, task: TaskRecord?): OrbMood = when {
    voice.listening -> OrbMood.Listening
    voice.prompt != null -> OrbMood.Waiting
    voice.notice != null -> OrbMood.Offline
    task == null -> OrbMood.Idle
    task.state == TaskState.Done -> OrbMood.Success
    task.state == TaskState.Failed -> OrbMood.Error
    task.state == TaskState.Waiting || task.state == TaskState.Queued -> OrbMood.Waiting
    task.running -> OrbMood.Working
    else -> OrbMood.Idle
}

private fun headline(voice: VoiceUi, task: TaskRecord?): Pair<String, String> {
    voice.prompt?.let { return it.text to (if (it.confirm) "Say yes or no, or tap below." else "Pony is listening for your answer.") }
    voice.notice?.let { return it.title to it.body }
    if (voice.listening) return (if (voice.transcript.isBlank()) "Listening…" else "“${voice.transcript}”") to "Say stop to cancel."
    if (task != null) {
        return when {
            task.running -> "“${task.text}”" to (task.headline ?: "Pony is on it")
            task.state == TaskState.Done -> "Done" to (task.outcome ?: "")
            task.state == TaskState.Stopped -> "Stopped" to "You can pick it back up from Pony."
            else -> "Couldn't finish" to (task.outcome ?: "")
        }
    }
    return "Hi" to "What should Pony do?"
}
