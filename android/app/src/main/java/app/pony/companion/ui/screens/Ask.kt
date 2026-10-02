package app.pony.companion.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pony.companion.recap.Recap
import app.pony.companion.recap.RecapShots
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskState
import app.pony.companion.ui.AskFix
import app.pony.companion.ui.AskStatus
import app.pony.companion.ui.BrainChip
import app.pony.companion.ui.RecapShotsRow
import app.pony.companion.ui.StepTimeline
import app.pony.companion.ui.Suggestion
import app.pony.companion.ui.UndoControl
import app.pony.companion.ui.design.Banner
import app.pony.companion.ui.design.BannerAction
import app.pony.companion.ui.design.ButtonTone
import app.pony.companion.ui.design.Haptic
import app.pony.companion.ui.design.IconTone
import app.pony.companion.ui.design.OrbMood
import app.pony.companion.ui.design.PonyButton
import app.pony.companion.ui.design.PonyCard
import app.pony.companion.ui.design.PonyIconButton
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.design.PresenceOrb
import app.pony.companion.ui.design.Spinner
import app.pony.companion.ui.design.StatusBarBand
import app.pony.companion.ui.design.SuggestionChip
import app.pony.companion.ui.design.Tag
import app.pony.companion.ui.design.Tone
import app.pony.companion.ui.design.enter
import app.pony.companion.ui.design.rememberHaptics
import app.pony.companion.ui.design.safeBottom
import app.pony.companion.ui.design.safeHorizontal
import app.pony.companion.ui.durationText
import app.pony.companion.ui.iconFor
import app.pony.companion.ui.moodFor
import app.pony.companion.ui.shared
import app.pony.companion.ui.taskStateLabel
import app.pony.companion.ui.taskTone
import app.pony.companion.ui.theme.Motion
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Space
import app.pony.companion.ui.theme.SquircleShape
import app.pony.companion.voice.VoiceUi
import java.io.File

data class AskState(
    val status: AskStatus,
    val brainLabel: String,
    val brainTone: Tone,
    val brainLive: Boolean,
    val task: TaskRecord?,
    val voice: VoiceUi,
    val suggestions: List<Suggestion>,
    val now: Long,
    val autoFocus: Boolean = true,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(
    state: AskState,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    onStopListening: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onFix: (AskFix) -> Unit,
    onBrain: () -> Unit,
    onSuggestion: (Suggestion) -> Unit,
    onOpenTask: (String) -> Unit,
    onNewAsk: () -> Unit,
    shotFile: (String, String) -> File? = { _, _ -> null },
    recap: Recap? = null,
    recapShots: RecapShots.Shots = RecapShots.Shots(null, null),
    onUndo: () -> Unit = {},
) {
    val colors = Pony.colors
    val task = state.task
    val voice = state.voice
    val scroll = rememberScrollState()
    LaunchedEffect(task?.steps?.size, task?.state) {
        if (task != null) scroll.animateScrollTo(scroll.maxValue)
    }
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize()) {
            StatusBarBand(colors.canvas)
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(safeHorizontal()).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PonyIconButton(PonyIcons.Back, "Back", onBack, size = 40.dp)
                Text("Ask Pony", style = Pony.type.titleSmall, color = colors.ink, modifier = Modifier.padding(start = Space.sm).weight(1f).semantics { heading() })
                BrainChip(state.brainLabel, state.brainTone, onBrain, live = state.brainLive)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .windowInsetsPadding(safeHorizontal())
                        .padding(horizontal = Space.gutter)
                        .padding(bottom = Space.xl),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    if (task == null) {
                        Column(Modifier.fillMaxWidth().padding(top = Space.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                            PresenceOrb(
                                if (voice.listening) OrbMood.Listening else OrbMood.Idle,
                                level = voice.level,
                                size = 150.dp,
                                modifier = Modifier.shared("orb"),
                                description = if (voice.listening) "Pony is listening" else "Pony",
                            )
                            AnimatedContent(
                                targetState = when {
                                    voice.listening && voice.transcript.isNotBlank() -> "“${voice.transcript}”"
                                    voice.listening -> "Listening…"
                                    else -> "What should Pony do?"
                                },
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                                label = "ask-headline",
                            ) { line ->
                                Text(line, style = Pony.type.headline, color = colors.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                            }
                            if (voice.listening) {
                                Text("Speak now. Say stop to cancel.", style = Pony.type.bodySmall, color = colors.inkMuted)
                            }
                        }
                        voice.notice?.let { notice ->
                            Banner(notice.title, Tone.Warning, body = notice.body, icon = PonyIcons.MicOff)
                        }
                        StatusBanner(state.status, onFix)
                        if (state.suggestions.isNotEmpty()) {
                            FlowRow(
                                Modifier.fillMaxWidth().enter(2),
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                verticalArrangement = Arrangement.spacedBy(Space.sm),
                            ) {
                                state.suggestions.forEach { suggestion ->
                                    SuggestionChip(suggestion.label, { onSuggestion(suggestion) }, icon = iconFor(suggestion.icon))
                                }
                            }
                        }
                    } else {
                        Thread(task, state, onFix, onOpenTask, onNewAsk, onSend, shotFile, recap, recapShots, onUndo)
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.canvas)
                    .windowInsetsPadding(safeBottom())
                    .imePadding()
                    .padding(horizontal = Space.gutter)
                    .padding(top = Space.sm, bottom = Space.md),
            ) {
                val bar = when {
                    task?.running != true -> BottomBar.Compose
                    task.state == TaskState.Queued || task.state == TaskState.Sent || (task.state == TaskState.Waiting && task.actionCount == 0) -> BottomBar.Cancel
                    else -> BottomBar.Stop
                }
                AnimatedContent(targetState = bar, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "composer") { shown ->
                    if (shown == BottomBar.Stop) {
                        PonyButton("Stop", onStop, tone = ButtonTone.Danger, icon = PonyIcons.Stop, haptic = Haptic.STOP)
                    } else if (shown == BottomBar.Cancel) {
                        PonyButton("Cancel request", onStop, tone = ButtonTone.Secondary, icon = PonyIcons.Close, haptic = Haptic.TICK)
                    } else {
                        Composer(
                            draft = draft,
                            onDraft = onDraft,
                            onSend = onSend,
                            onMic = if (voice.listening) onStopListening else onMic,
                            listening = voice.listening,
                            autoFocus = state.autoFocus && task == null && !voice.listening,
                        )
                    }
                }
            }
        }
    }
}

/** A request only waiting for a brain can be cancelled quietly; one acting on the phone gets the red Stop. */
private enum class BottomBar { Compose, Cancel, Stop }

@Composable
private fun StatusBanner(status: AskStatus, onFix: (AskFix) -> Unit) {
    val icon = when (status.tone) {
        Tone.Success -> PonyIcons.CheckCircle
        Tone.Warning -> PonyIcons.Info
        else -> PonyIcons.Bot
    }
    Banner(
        title = status.title,
        tone = status.tone,
        body = status.body,
        icon = icon,
        actions = status.fixes.take(2).mapIndexed { index, fix ->
            when (fix) {
                AskFix.PAIR -> BannerAction("Pair Grok Bot", { onFix(fix) }, primary = index == 0, icon = PonyIcons.Qr)
                AskFix.ADD_KEY -> BannerAction("Add a key", { onFix(fix) }, primary = index == 0, icon = PonyIcons.Key)
                AskFix.USE_GROK -> BannerAction("Use Grok Bot", { onFix(fix) }, primary = index == 0, icon = PonyIcons.Bot)
                AskFix.LISTEN_HELP -> BannerAction("Keep it listening", { onFix(fix) }, primary = index == 0, icon = PonyIcons.Terminal)
            }
        },
    )
}

@Composable
private fun Thread(
    task: TaskRecord,
    state: AskState,
    onFix: (AskFix) -> Unit,
    onOpenTask: (String) -> Unit,
    onNewAsk: () -> Unit,
    onSend: (String) -> Unit,
    shotFile: (String, String) -> File?,
    recap: Recap?,
    recapShots: RecapShots.Shots,
    onUndo: () -> Unit,
) {
    val colors = Pony.colors
    Box(Modifier.fillMaxWidth().padding(top = Space.lg), contentAlignment = Alignment.CenterEnd) {
        Text(
            task.text,
            style = Pony.type.body,
            color = colors.ink,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .enter(0, task.id)
                .clip(SquircleShape(22.dp))
                .background(colors.irisSoft)
                .border(1.dp, colors.iris.copy(alpha = 0.3f), SquircleShape(22.dp))
                .padding(horizontal = Space.lg, vertical = 12.dp)
                .semantics { contentDescription = "You asked: ${task.text}" },
        )
    }
    Row(Modifier.enter(1, task.id), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        PresenceOrb(moodFor(task.state), size = 52.dp, modifier = Modifier.shared("orb"))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(task.brainLabel, style = Pony.type.caption, color = colors.iris)
                Tag(taskStateLabel(task.state), taskTone(task.state))
            }
            val line = when (task.state) {
                TaskState.Done -> "Finished in ${durationText(task.durationMs(state.now))}"
                TaskState.Stopped -> "You stopped this task"
                TaskState.Failed, TaskState.Expired -> "It didn't finish"
                else -> task.headline ?: taskStateLabel(task.state)
            }
            AnimatedContent(
                targetState = line,
                transitionSpec = {
                    (slideInVertically(Motion.slide) { it / 2 } + fadeIn(tween(180))) togetherWith fadeOut(tween(120))
                },
                label = "thread-headline",
            ) { text -> Text(text, style = Pony.type.bodyStrong, color = colors.ink) }
        }
    }
    val waiting = task.state == TaskState.Queued || task.state == TaskState.Waiting
    if (waiting && state.status.fixes.isNotEmpty()) StatusBanner(state.status, onFix)
    if (task.state.finished && task.actionCount > 0) {
        var expanded by remember(task.id) { mutableStateOf(false) }
        val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.glide(), label = "steps-chevron")
        PonyCard(onClick = { expanded = !expanded }, onClickLabel = if (expanded) "Hide steps" else "Show steps") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                androidx.compose.material3.Icon(PonyIcons.Layers, contentDescription = null, tint = colors.iris)
                Text(
                    "${task.actionCount} ${if (task.actionCount == 1) "step" else "steps"}",
                    style = Pony.type.bodyStrong,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(if (expanded) "Hide" else "Show", style = Pony.type.label, color = colors.iris)
                androidx.compose.material3.Icon(
                    PonyIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.iris,
                    modifier = Modifier.graphicsLayer { rotationZ = turn },
                )
            }
            AnimatedVisibility(expanded, enter = expandVertically(Motion.glide()) + fadeIn(), exit = shrinkVertically(Motion.glide()) + fadeOut()) {
                StepTimeline(task.steps, task.createdAt, running = false, shotFile = { name -> shotFile(task.id, name) })
            }
        }
    } else if (task.steps.isNotEmpty()) {
        PonyCard {
            StepTimeline(task.steps, task.createdAt, task.running && !waiting, shotFile = { name -> shotFile(task.id, name) })
        }
    } else if (task.running && !waiting) {
        PonyCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Spinner(colors.iris, size = 18.dp)
                Text("Getting started…", style = Pony.type.body, color = colors.inkMuted)
            }
        }
    }
    AnimatedVisibility(task.state.finished, enter = fadeIn(tween(260)) + scaleIn(initialScale = 0.96f)) {
        Outcome(task, recap, recapShots, onUndo, onOpenTask, onNewAsk, onSend)
    }
}

@Composable
private fun Outcome(
    task: TaskRecord,
    recap: Recap?,
    recapShots: RecapShots.Shots,
    onUndo: () -> Unit,
    onOpenTask: (String) -> Unit,
    onNewAsk: () -> Unit,
    onSend: (String) -> Unit,
) {
    val colors = Pony.colors
    val haptics = rememberHaptics()
    LaunchedEffect(task.id, task.state) {
        haptics.perform(if (task.state == TaskState.Done) Haptic.SUCCESS else Haptic.REJECT)
    }
    val (tone, title) = when (task.state) {
        TaskState.Done -> Tone.Mane to "Done"
        TaskState.Stopped -> Tone.Neutral to "Stopped"
        else -> Tone.Danger to "Didn't finish"
    }
    PonyCard(border = when (tone) {
        Tone.Mane -> colors.mane.copy(alpha = 0.45f)
        Tone.Danger -> colors.danger.copy(alpha = 0.4f)
        else -> colors.hairline
    }) {
        Text(title.uppercase(), style = Pony.type.overline, color = if (tone == Tone.Mane) colors.mane else if (tone == Tone.Danger) colors.danger else colors.inkMuted)
        Text(task.outcome ?: "", style = Pony.type.headline, color = colors.ink)
        if (recap != null) RecapBlock(recap, recapShots, onUndo)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            PonyButton("Details", { onOpenTask(task.id) }, tone = ButtonTone.Secondary, height = 48.dp, modifier = Modifier.weight(1f), haptic = Haptic.TAP)
            when {
                task.state == TaskState.Done ->
                    PonyButton("New ask", onNewAsk, height = 48.dp, modifier = Modifier.weight(1f), icon = PonyIcons.Plus, haptic = Haptic.TAP)
                task.resumable ->
                    PonyButton("Keep going", { onSend(task.text) }, height = 48.dp, modifier = Modifier.weight(1f), icon = PonyIcons.Play, haptic = Haptic.TAP)
                else ->
                    PonyButton("Try again", { onSend(task.text) }, height = 48.dp, modifier = Modifier.weight(1f), icon = PonyIcons.Refresh)
            }
        }
    }
}

/** "Here's what I did": the steps, before/after of the work, and a safe one-tap undo. */
@Composable
private fun RecapBlock(recap: Recap, shots: RecapShots.Shots, onUndo: () -> Unit) {
    val colors = Pony.colors
    if (recap.did.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Here's what I did", style = Pony.type.overline, color = colors.inkMuted)
            recap.did.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Box(Modifier.size(5.dp).clip(SquircleShape(3.dp)).background(colors.iris))
                    Text(line, style = Pony.type.bodySmall, color = colors.inkMuted, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    RecapShotsRow(shots)
    recap.undo?.let { UndoControl(it, onUndo) }
}

@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    listening: Boolean,
    autoFocus: Boolean,
) {
    val colors = Pony.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = rememberHaptics()
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focus.requestFocus() }
    }
    val send = {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            haptics.perform(Haptic.CONFIRM)
            keyboard?.hide()
            onSend(text)
        }
    }
    val shape = SquircleShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .shared("askbar", bounds = true)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, if (listening) colors.iris else colors.iris.copy(alpha = if (colors.dark) 0.35f else 0.28f), shape)
            .padding(start = Space.lg, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
            if (draft.isEmpty()) {
                Text(if (listening) "Listening…" else "Tell Pony what to do", style = Pony.type.body, color = colors.inkFaint)
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                textStyle = Pony.type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.iris),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                        if (enter && !event.isShiftPressed) {
                            if (event.type == KeyEventType.KeyDown) send()
                            true
                        } else {
                            false
                        }
                    }
                    .semantics { contentDescription = "Tell Pony what to do" },
            )
        }
        PonyIconButton(
            if (listening) PonyIcons.Stop else PonyIcons.Mic,
            if (listening) "Stop listening" else "Talk to Pony",
            onMic,
            tone = if (listening) IconTone.Iris else IconTone.Quiet,
            size = 44.dp,
            haptic = if (listening) Haptic.TICK else Haptic.LISTEN,
        )
        PonyIconButton(
            PonyIcons.Send,
            "Send",
            { send() },
            tone = IconTone.Filled,
            size = 44.dp,
            enabled = draft.isNotBlank(),
            haptic = Haptic.NONE,
        )
    }
}
