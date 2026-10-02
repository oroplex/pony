package app.pony.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pony.companion.recap.Recap
import app.pony.companion.recap.RecapShots
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskState
import app.pony.companion.ui.RecapShotsRow
import app.pony.companion.ui.ShotViewer
import app.pony.companion.ui.StepTimeline
import app.pony.companion.ui.TaskRow
import app.pony.companion.ui.UndoControl
import app.pony.companion.ui.design.Banner
import app.pony.companion.ui.design.ButtonTone
import app.pony.companion.ui.design.EmptyState
import app.pony.companion.ui.design.Haptic
import app.pony.companion.ui.design.OrbMood
import app.pony.companion.ui.design.PonyButton
import app.pony.companion.ui.design.PonyCard
import app.pony.companion.ui.design.PonyIconButton
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.design.PonyPage
import app.pony.companion.ui.design.PonySheet
import app.pony.companion.ui.design.PresenceOrb
import app.pony.companion.ui.design.SectionHeader
import app.pony.companion.ui.design.Segmented
import app.pony.companion.ui.design.Skeleton
import app.pony.companion.ui.design.Tag
import app.pony.companion.ui.design.Tone
import app.pony.companion.ui.durationText
import app.pony.companion.ui.moodFor
import app.pony.companion.ui.shared
import app.pony.companion.ui.taskStateLabel
import app.pony.companion.ui.taskTone
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Shapes
import app.pony.companion.ui.theme.Space
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class HistoryFilter(val label: String) { ALL("All"), DONE("Done"), ATTENTION("Needs a look") }

@Composable
fun HistoryScreen(
    tasks: List<TaskRecord>,
    loading: Boolean,
    now: Long,
    onOpen: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onAsk: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = tasks.filter { task ->
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.DONE -> task.state == TaskState.Done
            HistoryFilter.ATTENTION -> task.state == TaskState.Failed || task.state == TaskState.Stopped || task.state == TaskState.Expired
        }
    }
    PonyPage(
        title = "History",
        subtitle = "What you asked, what Pony did, and how it went. Kept only on this phone.",
        onBack = onBack,
        actions = {
            if (tasks.isNotEmpty()) PonyIconButton(PonyIcons.Trash, "Clear history", { confirmClear = true })
        },
        overlay = {
            PonySheet(
                visible = confirmClear,
                onDismiss = { confirmClear = false },
                title = "Clear all history?",
                subtitle = "This deletes every task and screenshot on this phone. It can't be undone.",
            ) {
                PonyButton("Clear history", { confirmClear = false; onClear() }, tone = ButtonTone.Danger, icon = PonyIcons.Trash, haptic = Haptic.REJECT)
                PonyButton("Keep it", { confirmClear = false }, tone = ButtonTone.Secondary, haptic = Haptic.TAP)
            }
        },
    ) {
        Segmented(HistoryFilter.entries.map { it to it.label }, filter, { filter = it })
        when {
            loading -> repeat(4) { Skeleton(Modifier.fillMaxWidth().height(76.dp), shape = Shapes.card) }
            shown.isEmpty() -> EmptyState(
                title = if (tasks.isEmpty()) "No tasks yet" else "Nothing here",
                body = if (tasks.isEmpty()) "When you ask Pony for something, each step and the result land here." else "Try another filter.",
                action = if (tasks.isEmpty()) app.pony.companion.ui.design.BannerAction("Ask Pony", onAsk, icon = PonyIcons.Sparkles) else null,
            )
            else -> {
                var index = 0
                shown.groupBy { dayLabel(it.createdAt, now) }.forEach { (day, group) ->
                    SectionHeader(day)
                    group.forEach { task ->
                        TaskRow(task, now, onClick = { onOpen(task.id) }, index = index++)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    task: TaskRecord?,
    now: Long,
    shotFile: (String, String) -> File?,
    onBack: () -> Unit,
    onAskAgain: (String) -> Unit,
    onDelete: (String) -> Unit,
    onStop: () -> Unit,
    recap: Recap? = null,
    recapShots: RecapShots.Shots = RecapShots.Shots(null, null),
    onUndo: () -> Unit = {},
) {
    var viewing by remember { mutableStateOf<String?>(null) }
    PonyPage(
        title = "Task",
        largeTitle = false,
        onBack = onBack,
        actions = {
            if (task != null && !task.running) PonyIconButton(PonyIcons.Trash, "Delete task", { onDelete(task.id) })
        },
        bottomBar = if (task == null) null else {
            {
                if (task.running) {
                    PonyButton("Stop", onStop, tone = ButtonTone.Danger, icon = PonyIcons.Stop, haptic = Haptic.STOP)
                } else if (task.resumable) {
                    PonyButton("Keep going", { onAskAgain(task.text) }, icon = PonyIcons.Play)
                } else {
                    PonyButton("Ask again", { onAskAgain(task.text) }, icon = PonyIcons.Refresh)
                }
            }
        },
        overlay = {
            val name = viewing
            if (name != null && task != null) ShotViewer(shotFile(task.id, name)) { viewing = null }
        },
    ) {
        if (task == null) {
            EmptyState("This task is gone", "It may have been cleared from history.", mood = OrbMood.Offline)
            return@PonyPage
        }
        PonyCard(Modifier.shared("task-${task.id}", bounds = true), spacing = Space.lg) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PresenceOrb(moodFor(task.state), size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text("You asked", style = Pony.type.overline, color = Pony.colors.inkFaint)
                    Text(timeLabel(task.createdAt, now), style = Pony.type.caption, color = Pony.colors.inkMuted)
                }
                Tag(taskStateLabel(task.state), taskTone(task.state))
            }
            Text("“${task.text}”", style = Pony.type.headline, color = Pony.colors.ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Tag(task.brainLabel, Tone.Iris)
                Tag(sourceLabel(task.source), Tone.Neutral)
                Tag(durationText(task.durationMs(now)), Tone.Neutral)
                val shots = task.screenshots.size
                if (shots > 0) Tag(if (shots == 1) "1 screenshot" else "$shots screenshots", Tone.Neutral)
            }
        }
        task.outcome?.let { outcome ->
            Banner(
                title = if (task.state == TaskState.Done) "Result" else taskStateLabel(task.state),
                tone = when (task.state) {
                    TaskState.Done -> Tone.Mane
                    TaskState.Stopped -> Tone.Neutral
                    TaskState.Failed, TaskState.Expired -> Tone.Danger
                    else -> Tone.Iris
                },
                body = outcome,
                icon = when (task.state) {
                    TaskState.Done -> PonyIcons.CheckCircle
                    TaskState.Stopped -> PonyIcons.Stop
                    else -> PonyIcons.Info
                },
            )
        }
        // The before/after + undo recap, held in memory only for the latest task,
        // so this shows for the one Pony just finished and nothing older.
        if (recap != null && (recapShots.any || recap.hasUndo)) {
            SectionHeader("Recap")
            PonyCard(spacing = Space.md) {
                RecapShotsRow(recapShots)
                recap.undo?.let { UndoControl(it, onUndo) }
            }
        }
        SectionHeader("Steps")
        if (task.steps.isEmpty()) {
            Text(
                if (task.running) "Waiting for the first step…" else "No steps were needed.",
                style = Pony.type.bodySmall,
                color = Pony.colors.inkMuted,
                modifier = Modifier.padding(bottom = Space.lg),
            )
        } else {
            PonyCard {
                StepTimeline(task.steps, task.createdAt, task.running, shotFile = { name -> shotFile(task.id, name) }, onShot = { viewing = it })
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = Space.sm)) {
            Text(
                "Typed text and passwords aren't stored. Screenshots stay on this phone.",
                style = Pony.type.caption,
                color = Pony.colors.inkFaint,
            )
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "typed" -> "Typed"
    "voice", "button" -> "Spoken"
    "assistant" -> "Assistant gesture"
    "wake_word" -> "Hey Pony"
    else -> source.replaceFirstChar { it.uppercase() }
}

private fun dayLabel(at: Long, now: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = day.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val diff = today.get(Calendar.DAY_OF_YEAR) - day.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && diff == 0 -> "Today"
        sameYear && diff == 1 -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(at))
    }
}

private fun timeLabel(at: Long, now: Long): String {
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(at))
    return "${dayLabel(at, now)} at $time"
}
