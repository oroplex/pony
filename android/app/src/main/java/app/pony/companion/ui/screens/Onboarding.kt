package app.pony.companion.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.pony.companion.session.ReadinessState
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskState
import app.pony.companion.ui.PageDots
import app.pony.companion.ui.SetupItem
import app.pony.companion.ui.StepTimeline
import app.pony.companion.ui.ambientGlow
import app.pony.companion.ui.design.Banner
import app.pony.companion.ui.design.BannerAction
import app.pony.companion.ui.design.BrandMark
import app.pony.companion.ui.design.ButtonTone
import app.pony.companion.ui.design.ConsentRow
import app.pony.companion.ui.design.Haptic
import app.pony.companion.ui.design.IconTile
import app.pony.companion.ui.design.OrbMood
import app.pony.companion.ui.design.PonyButton
import app.pony.companion.ui.design.PonyCard
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.design.PonyPage
import app.pony.companion.ui.design.PresenceOrb
import app.pony.companion.ui.design.ProgressRing
import app.pony.companion.ui.design.StatusBarBand
import app.pony.companion.ui.design.Tag
import app.pony.companion.ui.design.TextAction
import app.pony.companion.ui.design.Tone
import app.pony.companion.ui.design.enter
import app.pony.companion.ui.design.rememberHaptics
import app.pony.companion.ui.design.safeBottom
import app.pony.companion.ui.design.safeHorizontal
import app.pony.companion.ui.moodFor
import app.pony.companion.ui.shared
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Space

@Composable
fun WelcomeScreen(pendingLink: Boolean, onStart: () -> Unit) {
    val colors = Pony.colors
    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.fillMaxSize()) {
            StatusBarBand(colors.canvas)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .ambientGlow(colors.canvasGlow, center = 0.34f, radius = 0.95f)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(safeHorizontal())
                    .padding(horizontal = Space.gutter),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 34.dp)
                    Text("Pony", style = Pony.type.title.copy(fontFamily = app.pony.companion.ui.theme.Serif), color = colors.ink, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    PageDots(4, 0)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = Space.section), contentAlignment = Alignment.Center) {
                    PresenceOrb(OrbMood.Idle, size = 250.dp, modifier = Modifier.enter(0).shared("orb"), description = "Pony")
                }
                Text(
                    buildAnnotatedString {
                        append("Your phone,\nwith a ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.iris)) { append("companion") }
                        append(".")
                    },
                    style = Pony.type.hero,
                    color = colors.ink,
                    modifier = Modifier.enter(1).semantics { heading() },
                )
                Spacer(Modifier.height(Space.lg))
                Text(
                    "Pony lets Grok Bot see your screen and tap, type, and open apps for you. You watch, and you can stop it at any time.",
                    style = Pony.type.body,
                    color = colors.inkMuted,
                    modifier = Modifier.enter(2),
                )
                Spacer(Modifier.height(Space.xl))
                Row(Modifier.enter(3), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Tag("No API key needed", Tone.Iris)
                    Tag("Encrypted", Tone.Mane)
                    Tag("Stop anytime", Tone.Neutral)
                }
                if (pendingLink) {
                    Spacer(Modifier.height(Space.lg))
                    Banner("A pairing link is waiting", Tone.Iris, body = "Finish this short setup and Pony will use it.", icon = PonyIcons.Link)
                }
                Spacer(Modifier.height(Space.xl))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.canvas)
                    .windowInsetsPadding(safeBottom())
                    .padding(horizontal = Space.gutter)
                    .padding(top = Space.sm, bottom = Space.md),
            ) {
                PonyButton("Get started", onStart, icon = PonyIcons.ArrowRight)
            }
        }
    }
}

private data class Promise(val icon: ImageVector, val title: String, val body: String, val tone: Tone)

@Composable
fun PrivacyScreen(
    checked: Boolean,
    pendingLink: Boolean,
    onChecked: (Boolean) -> Unit,
    onAgree: () -> Unit,
    onBack: () -> Unit,
    review: Boolean = false,
) {
    val promises = listOf(
        Promise(PonyIcons.Eye, "Only while you ask", "Pony sees the screen only during a session or a task you start.", Tone.Iris),
        Promise(PonyIcons.Lock, "Passwords stay hidden", "Password fields show as [password]. Pony won't read or type them.", Tone.Iris),
        Promise(PonyIcons.Shield, "It asks before anything risky", "Sending, paying, buying, deleting, calling, and security changes need your yes.", Tone.Mane),
        Promise(PonyIcons.Stop, "Stop means stop", "One tap ends the current task. Your connection stays, so the next ask just works.", Tone.Danger),
        Promise(PonyIcons.Key, "Your keys stay here", "API keys never leave this phone. Grok Bot doesn't need one.", Tone.Iris),
        Promise(PonyIcons.History, "History stays on the phone", "What you asked and each step are kept here. Clear them any time.", Tone.Neutral),
    )
    PonyPage(
        title = "You stay in charge.",
        eyebrow = if (review) "Your privacy" else "Privacy",
        onBack = onBack,
        actions = { if (!review) PageDots(4, 1, Modifier.padding(end = Space.md)) },
        bottomBar = if (review) {
            null
        } else {
            {
                ConsentRow("I understand Pony can see the screen and act only while I allow it.", checked, onChecked)
                PonyButton("Agree and continue", onAgree, enabled = checked, icon = PonyIcons.ArrowRight)
            }
        },
    ) {
        if (pendingLink) Banner("A pairing link is waiting", Tone.Iris, body = "It will be used after setup.", icon = PonyIcons.Link)
        PonyCard(spacing = Space.lg) {
            promises.forEachIndexed { index, promise ->
                Row(Modifier.enter(index), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    IconTile(promise.icon, promise.tone)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(promise.title, style = Pony.type.bodyStrong, color = Pony.colors.ink)
                        Text(promise.body, style = Pony.type.bodySmall, color = Pony.colors.inkMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen(
    items: List<SetupItem>,
    readiness: ReadinessState,
    restrictedHint: Boolean,
    samsung: Boolean,
    onboarding: Boolean,
    onAction: (String) -> Unit,
    onRestricted: () -> Unit,
    onSamsungBattery: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val done = items.count { it.done }
    PonyPage(
        title = if (onboarding) "A few switches." else "Setup",
        eyebrow = if (onboarding) "Setup" else null,
        subtitle = "Pony control is the one Pony needs. The rest make it smoother.",
        onBack = onBack,
        actions = { if (onboarding) PageDots(4, 2, Modifier.padding(end = Space.md)) },
        bottomBar = if (onboarding) {
            {
                PonyButton(
                    if (readiness.accessibility) "Continue" else "Continue for now",
                    onContinue,
                    tone = if (readiness.accessibility) ButtonTone.Primary else ButtonTone.Secondary,
                    icon = PonyIcons.ArrowRight,
                )
            }
        } else {
            null
        },
    ) {
        PonyCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                ProgressRing(done / items.size.toFloat(), size = 52.dp, color = if (readiness.accessibility) Pony.colors.success else Pony.colors.iris)
                Column(Modifier.weight(1f)) {
                    Text("$done of ${items.size} done", style = Pony.type.titleSmall, color = Pony.colors.ink)
                    Text(
                        if (readiness.accessibility) "Pony is ready. The rest are nice to have." else "Turn on Pony control to let Pony act.",
                        style = Pony.type.bodySmall,
                        color = Pony.colors.inkMuted,
                    )
                }
            }
        }
        items.forEachIndexed { index, item ->
            SetupCard(item, index, onAction = { onAction(item.key) })
            if (item.key == "control" && restrictedHint) {
                Banner(
                    "Is the switch greyed out?",
                    Tone.Iris,
                    body = "On Android 13 and newer, open App info, tap the three-dot menu, then Allow restricted settings. Then come back and turn Pony control on.",
                    icon = PonyIcons.Info,
                    actions = listOf(BannerAction("Open App info", onRestricted)),
                )
            }
            if (item.key == "battery" && samsung) {
                TextAction("Samsung: add Pony to Never sleeping apps", onSamsungBattery, icon = PonyIcons.External)
            }
        }
    }
}

@Composable
private fun SetupCard(item: SetupItem, index: Int, onAction: () -> Unit) {
    val colors = Pony.colors
    val icon = when (item.key) {
        "control" -> PonyIcons.Accessibility
        "notifications" -> PonyIcons.Bell
        "battery" -> PonyIcons.Battery
        "microphone" -> PonyIcons.Mic
        else -> PonyIcons.Keyboard
    }
    PonyCard(Modifier.enter(index), border = if (!item.done && item.required) colors.iris.copy(alpha = 0.45f) else colors.hairline) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.Top) {
            IconTile(icon, if (item.done) Tone.Success else if (item.required) Tone.Iris else Tone.Neutral)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(item.title, style = Pony.type.titleSmall, color = colors.ink, modifier = Modifier.weight(1f, fill = false))
                    AnimatedContent(
                        targetState = item.done,
                        transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.8f)) togetherWith fadeOut(tween(120)) },
                        label = "setup-tag-${item.key}",
                    ) { finished ->
                        when {
                            finished -> Tag("Done", Tone.Success)
                            item.required -> Tag("Required", Tone.Iris)
                            else -> Box(Modifier)
                        }
                    }
                }
                Text(item.body, style = Pony.type.bodySmall, color = colors.inkMuted)
            }
        }
        if (!item.done) {
            PonyButton(
                item.action,
                onAction,
                tone = if (item.required) ButtonTone.Iris else ButtonTone.Secondary,
                height = 48.dp,
                haptic = Haptic.TAP,
            )
        }
    }
}

@Composable
fun TryItScreen(
    task: TaskRecord?,
    controlOn: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onTurnOn: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    shotFile: (String, String) -> java.io.File? = { _, _ -> null },
) {
    val colors = Pony.colors
    val haptics = rememberHaptics()
    var celebrated by remember { mutableStateOf(false) }
    LaunchedEffect(task?.state) {
        if (task?.state == TaskState.Done && !celebrated) {
            celebrated = true
            haptics.perform(Haptic.SUCCESS)
        }
    }
    val running = task?.running == true
    val done = task?.state == TaskState.Done
    PonyPage(
        title = if (done) "It worked." else "Let's try it.",
        eyebrow = "First task",
        subtitle = if (done) "That was Pony, tapping Calculator for you. Next, connect Grok Bot for everything else." else "Watch Pony open Calculator and add 2\u00A0+\u00A02. It runs right on your phone, with no account or key.",
        onBack = onBack,
        actions = { PageDots(4, 3, Modifier.padding(end = Space.md)) },
        bottomBar = {
            when {
                running -> PonyButton("Stop", onStop, tone = ButtonTone.Danger, icon = PonyIcons.Stop, haptic = Haptic.STOP)
                done -> PonyButton("Go to Pony", onFinish, icon = PonyIcons.ArrowRight, haptic = Haptic.SUCCESS)
                !controlOn -> {
                    PonyButton("Turn on Pony control", onTurnOn, tone = ButtonTone.Iris, icon = PonyIcons.Accessibility)
                    PonyButton("Skip for now", onFinish, tone = ButtonTone.Quiet, haptic = Haptic.TAP)
                }
                else -> {
                    PonyButton(if (task == null) "Run it" else "Run it again", onRun, tone = ButtonTone.Iris, icon = PonyIcons.Play, haptic = Haptic.LISTEN)
                    PonyButton("Skip for now", onFinish, tone = ButtonTone.Quiet, haptic = Haptic.TAP)
                }
            }
        },
    ) {
        PonyCard(spacing = Space.lg) {
            Box(Modifier.fillMaxWidth().padding(top = Space.sm), contentAlignment = Alignment.Center) {
                PresenceOrb(task?.let { moodFor(it.state) } ?: OrbMood.Idle, size = 150.dp)
            }
            AnimatedContent(targetState = task?.state, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "try-state") { state ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (state) {
                        null -> {
                            Text("Try", style = Pony.type.overline, color = colors.iris)
                            Text("“Open Calculator and add 2\u00A0+\u00A02”", style = Pony.type.headline, color = colors.ink, textAlign = TextAlign.Center)
                        }
                        TaskState.Done -> {
                            Text("Calculator says", style = Pony.type.overline, color = colors.mane)
                            Text(task?.outcome ?: "2 + 2 = 4", style = Pony.type.display, color = colors.mane, textAlign = TextAlign.Center)
                        }
                        TaskState.Failed, TaskState.Stopped -> {
                            Text(if (state == TaskState.Stopped) "Stopped" else "That didn't work", style = Pony.type.titleSmall, color = colors.ink)
                            Text(task?.outcome.orEmpty(), style = Pony.type.bodySmall, color = colors.inkMuted, textAlign = TextAlign.Center)
                        }
                        else -> {
                            Text("Pony is working", style = Pony.type.overline, color = colors.iris)
                            Text(task?.headline ?: "Opening Calculator…", style = Pony.type.titleSmall, color = colors.ink, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            if (task != null && task.steps.isNotEmpty()) {
                StepTimeline(task.steps, task.createdAt, running, shotFile = { name -> shotFile(task.id, name) })
            }
        }
        if (!controlOn && task == null) {
            Banner(
                "Pony control is off",
                Tone.Warning,
                body = "Pony needs it to tap Calculator. Turn it on, come back, and run the try.",
                icon = PonyIcons.Accessibility,
            )
        }
    }
}
