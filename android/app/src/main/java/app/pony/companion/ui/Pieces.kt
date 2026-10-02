package app.pony.companion.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pony.companion.tasks.StepKind
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskState
import app.pony.companion.tasks.TaskStep
import app.pony.companion.ui.design.ButtonTone
import app.pony.companion.ui.design.Haptic
import app.pony.companion.ui.design.IconTile
import app.pony.companion.ui.design.IconTone
import app.pony.companion.ui.design.OrbMood
import app.pony.companion.ui.design.PonyButton
import app.pony.companion.ui.design.PonyCard
import app.pony.companion.ui.design.PonyIconButton
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.design.PresenceOrb
import app.pony.companion.ui.design.Tag
import app.pony.companion.ui.design.Tone
import app.pony.companion.ui.design.enter
import app.pony.companion.ui.design.pressable
import app.pony.companion.ui.design.safeInsets
import app.pony.companion.ui.design.toneColors
import app.pony.companion.ui.theme.Motion
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Shapes
import app.pony.companion.ui.theme.Space
import app.pony.companion.ui.theme.SquircleShape
import java.io.File

fun iconFor(icon: SuggestionIcon): ImageVector = when (icon) {
    SuggestionIcon.Car -> PonyIcons.Car
    SuggestionIcon.Bag -> PonyIcons.ShoppingBag
    SuggestionIcon.Bed -> PonyIcons.Bed
    SuggestionIcon.Utensils -> PonyIcons.Utensils
    SuggestionIcon.Photos -> PonyIcons.Image
    SuggestionIcon.Package -> PonyIcons.Package
    SuggestionIcon.Home -> PonyIcons.Home
    SuggestionIcon.Plane -> PonyIcons.Plane
    SuggestionIcon.Pin -> PonyIcons.MapPin
    SuggestionIcon.Mail -> PonyIcons.Mail
    SuggestionIcon.Bolt -> PonyIcons.Bolt
    SuggestionIcon.Music -> PonyIcons.Music
    SuggestionIcon.Play -> PonyIcons.Play
}

/**
 * A fresh set of suggestion chips for a screen, filtered to apps actually installed on
 * this phone. Remembered per screen instance so it stays put while you read it but
 * reshuffles the next time the surface opens.
 */
@Composable
fun rememberSuggestions(count: Int = 4): List<Suggestion> {
    val context = LocalContext.current
    return remember {
        val pm = context.packageManager
        pickSuggestions(
            isInstalled = { pkg ->
                try {
                    pm.getLaunchIntentForPackage(pkg) != null
                } catch (_: Exception) {
                    false
                }
            },
            count = count,
        )
    }
}

fun iconFor(kind: StepKind): ImageVector = when (kind) {
    StepKind.Open -> PonyIcons.Layers
    StepKind.Tap -> PonyIcons.Tap
    StepKind.Type -> PonyIcons.Type
    StepKind.Swipe -> PonyIcons.Swipe
    StepKind.Key -> PonyIcons.Back
    StepKind.Look -> PonyIcons.Eye
    StepKind.Read -> PonyIcons.Search
    StepKind.Speak -> PonyIcons.Speak
    StepKind.Ask -> PonyIcons.Ask
    StepKind.Confirm -> PonyIcons.Shield
    StepKind.Wait -> PonyIcons.Clock
    StepKind.Status -> PonyIcons.Sparkles
    StepKind.Result -> PonyIcons.CheckCircle
    StepKind.Error -> PonyIcons.AlertCircle
}

fun iconFor(state: TaskState): ImageVector = when (state) {
    TaskState.Done -> PonyIcons.CheckCircle
    TaskState.Stopped -> PonyIcons.Stop
    TaskState.Failed, TaskState.Expired -> PonyIcons.AlertCircle
    TaskState.Waiting, TaskState.Queued -> PonyIcons.Clock
    else -> PonyIcons.Activity
}

fun moodFor(state: TaskState): OrbMood = when (state) {
    TaskState.Done -> OrbMood.Success
    TaskState.Failed, TaskState.Expired -> OrbMood.Error
    TaskState.Stopped -> OrbMood.Idle
    TaskState.Waiting, TaskState.Queued -> OrbMood.Waiting
    TaskState.Sent -> OrbMood.Thinking
    TaskState.Running -> OrbMood.Working
}

/** A soft pool of iris light behind a screen's hero. */
fun Modifier.ambientGlow(color: Color, center: Float = 0.18f, radius: Float = 0.9f): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            0f to color.copy(alpha = 0.55f),
            0.5f to color.copy(alpha = 0.18f),
            1f to Color.Transparent,
            center = Offset(size.width / 2f, size.height * center),
            radius = size.width * radius,
        ),
    )
}

@Composable
fun TaskRow(task: TaskRecord, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier, index: Int = 0) {
    val colors = Pony.colors
    val meta = buildList {
        add(task.brainLabel)
        if (task.actionCount > 0) add("${task.actionCount} ${if (task.actionCount == 1) "step" else "steps"}")
        add(relativeTime(task.createdAt, now))
    }.joinToString(" · ")
    PonyCard(
        modifier = modifier.enter(index, task.id).shared("task-${task.id}", bounds = true),
        onClick = onClick,
        onClickLabel = "Open task",
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.lg, vertical = 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
            IconTile(iconFor(task.state), taskTone(task.state))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(task.text, style = Pony.type.bodyStrong, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(meta, style = Pony.type.caption, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(PonyIcons.ChevronRight, contentDescription = null, tint = colors.inkFaint, modifier = Modifier.size(18.dp))
        }
    }
}

/** Steps as a vertical timeline. New steps glide in; screenshots open full screen. */
@Composable
fun StepTimeline(
    steps: List<TaskStep>,
    startedAt: Long,
    running: Boolean,
    modifier: Modifier = Modifier,
    shotFile: (String) -> File? = { null },
    onShot: (String) -> Unit = {},
) {
    val colors = Pony.colors
    Column(modifier.fillMaxWidth().animateContentSize(Motion.glide())) {
        steps.forEachIndexed { index, step ->
            val last = index == steps.lastIndex
            Row(Modifier.fillMaxWidth().enter(0, "${step.at}-$index"), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val tone = when {
                        !step.ok -> Tone.Warning
                        step.kind == StepKind.Result -> Tone.Success
                        step.kind == StepKind.Status -> Tone.Iris
                        else -> Tone.Neutral
                    }
                    val tc = toneColors(tone)
                    Box(
                        Modifier.size(30.dp).clip(SquircleShape(10.dp)).background(tc.soft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(iconFor(step.kind), contentDescription = null, tint = if (step.ok) tc.fg else colors.warning, modifier = Modifier.size(15.dp))
                    }
                    if (!last || running) {
                        Box(
                            Modifier
                                .padding(vertical = 2.dp)
                                .width(2.dp)
                                .height(if (step.shot != null) 150.dp else 20.dp)
                                .background(colors.hairlineStrong),
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(top = 5.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (step.count > 1) "${step.label} ×${step.count}" else step.label,
                            style = Pony.type.bodySmall,
                            color = if (step.ok) colors.ink else colors.warning,
                            modifier = Modifier.weight(1f),
                        )
                        Text("+${elapsed(step.at - startedAt)}", style = Pony.type.caption, color = colors.inkFaint)
                    }
                    val shot = step.shot
                    if (shot != null) {
                        ShotThumb(shotFile(shot), onClick = { onShot(shot) })
                    }
                }
            }
        }
        if (running) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { PresenceOrb(OrbMood.Working, size = 30.dp) }
                Text("Working…", style = Pony.type.bodySmall, color = colors.inkMuted)
            }
        }
    }
}

private fun elapsed(ms: Long): String {
    val s = (ms / 100) / 10.0
    return if (s < 60) "${"%.1f".format(s)}s" else "${(s / 60).toInt()}m"
}

@Composable
fun rememberShot(file: File?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = file?.takeIf { it.exists() }?.let { f ->
            runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
        }
    }
    return bitmap
}

@Composable
fun ShotThumb(file: File?, onClick: () -> Unit, height: Dp = 140.dp) {
    val colors = Pony.colors
    val image = rememberShot(file)
    Box(
        Modifier
            .height(height)
            .width(height * 0.52f)
            .clip(Shapes.tile)
            .background(colors.surfaceHigh)
            .border(1.dp, colors.hairlineStrong, Shapes.tile)
            .pressable(onClick = onClick, label = "Open screenshot", pressedScale = 0.96f)
            .semantics { contentDescription = "Screenshot from this step" },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(PonyIcons.Image, contentDescription = null, tint = colors.inkFaint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun ShotViewer(file: File?, onClose: () -> Unit) {
    val image = rememberShot(file)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .pressable(onClick = onClose, label = "Close screenshot", pressedScale = 1f, haptic = Haptic.NONE)
            .windowInsetsPadding(safeInsets())
            .padding(Space.xl),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = "Screenshot", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().clip(Shapes.card))
        }
        PonyIconButton(PonyIcons.Close, "Close", onClose, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/** Home's big entry point. Tapping the words opens Ask; the mic starts listening. */
@Composable
fun AskBar(onOpen: () -> Unit, onMic: () -> Unit, modifier: Modifier = Modifier, hint: String = "Ask Pony anything…") {
    val colors = Pony.colors
    val shape = SquircleShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .shared("askbar", bounds = true)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.iris.copy(alpha = if (colors.dark) 0.35f else 0.28f), shape)
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 60.dp)
                .clip(SquircleShape(22.dp))
                .pressable(onClick = onOpen, label = "Ask Pony", pressedScale = 0.98f)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(PonyIcons.Sparkles, contentDescription = null, tint = colors.iris, modifier = Modifier.size(22.dp))
            Text(hint, style = Pony.type.body, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PonyIconButton(PonyIcons.Mic, "Talk to Pony", onMic, tone = IconTone.Iris, size = 48.dp, haptic = Haptic.LISTEN)
    }
}

/** The running task on Home: what Pony is doing now, with Stop in reach. */
@Composable
fun LiveTaskCard(task: TaskRecord, onStop: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Pony.colors
    PonyCard(modifier.shared("askbar", bounds = true), onClick = onOpen, onClickLabel = "Open task", border = colors.iris.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            PresenceOrb(moodFor(task.state), size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(task.brainLabel, style = Pony.type.caption, color = colors.iris)
                AnimatedContent(
                    targetState = task.headline ?: taskStateLabel(task.state),
                    transitionSpec = {
                        (slideInVertically(Motion.slide) { it / 2 } + fadeIn(tween(160))) togetherWith
                            (slideOutVertically(Motion.slide) { -it / 2 } + fadeOut(tween(120)))
                    },
                    label = "live-headline",
                ) { line -> Text(line, style = Pony.type.bodyStrong, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Tag("${task.actionCount} ${if (task.actionCount == 1) "step" else "steps"}", Tone.Iris)
        }
        Text("“${task.text}”", style = Pony.type.bodySmall, color = colors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        PonyButton("Stop", onStop, tone = ButtonTone.Danger, icon = PonyIcons.Stop, haptic = Haptic.STOP)
    }
}

@Composable
fun BrainChip(label: String, tone: Tone, onClick: () -> Unit, live: Boolean = false) {
    val colors = Pony.colors
    val tc = toneColors(tone)
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(Shapes.chip)
            .pressable(onClick = onClick, label = "Choose a brain", pressedScale = 0.95f, haptic = Haptic.SELECT)
            .background(colors.surfaceHigh)
            .border(1.dp, colors.hairlineStrong, Shapes.chip)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(SquircleShape(4.dp, 0f)).background(if (live) tc.fg else colors.control))
        Text(label, style = Pony.type.label, color = colors.ink, maxLines = 1)
        Icon(PonyIcons.ChevronDown, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun CodeBlock(code: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Pony.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.control)
            .background(if (colors.dark) Color(0xFF07060D) else colors.surfaceHigh)
            .border(1.dp, colors.hairlineStrong, Shapes.control)
            .padding(start = Space.lg, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Text(code, style = Pony.type.mono, color = colors.ink, softWrap = false)
        }
        PonyIconButton(PonyIcons.Copy, "Copy command", onCopy, size = 40.dp, haptic = Haptic.CONFIRM)
    }
}

/** Numbered page dots for onboarding. The current page stretches into a pill. */
@Composable
fun PageDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    val colors = Pony.colors
    Row(modifier.semantics { contentDescription = "Step ${index + 1} of $count" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { dot ->
            val on = dot == index
            val width by androidx.compose.animation.core.animateDpAsState(if (on) 22.dp else 7.dp, Motion.glide(), label = "dot")
            Box(
                Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(SquircleShape(4.dp, 0f))
                    .background(if (on) colors.iris else if (dot < index) colors.iris.copy(alpha = 0.45f) else colors.hairlineStrong),
            )
        }
    }
}
