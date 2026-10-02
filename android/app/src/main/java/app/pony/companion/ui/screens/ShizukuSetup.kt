package app.pony.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pony.companion.ui.design.Banner
import app.pony.companion.ui.design.ButtonTone
import app.pony.companion.ui.design.PonyButton
import app.pony.companion.ui.design.PonyCard
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.design.PonyPage
import app.pony.companion.ui.design.Spinner
import app.pony.companion.ui.design.Tone
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Space
import app.pony.companion.ui.theme.SquircleShape
import kotlinx.coroutines.delay

data class ShizukuSetupState(
    val installed: Boolean,
    val devOptions: Boolean,
    val running: Boolean,
    val granted: Boolean,
)

/**
 * A guided, self-advancing walkthrough for turning Shizuku on. Pony polls the
 * live state, lights up the step the owner is on, and once Shizuku is running it
 * asks for permission on its own and flips the toggle. Nobody has to read a wiki.
 */
@Composable
fun ShizukuSetupScreen(
    state: ShizukuSetupState,
    onGetShizuku: () -> Unit,
    onBuildNumber: () -> Unit,
    onWirelessDebugging: () -> Unit,
    onOpenShizuku: () -> Unit,
    onGrant: () -> Unit,
    onGranted: () -> Unit,
    onRefresh: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        while (true) {
            onRefresh()
            delay(1500)
        }
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.running, state.granted) {
        if (!state.running) asked = false
        if (state.running && !state.granted && !asked) {
            asked = true
            onGrant()
        }
        if (state.granted) onGranted()
    }

    val connecting = state.installed && state.devOptions && !state.running
    val doneBar: (@Composable ColumnScope.() -> Unit)? = if (state.granted) {
        { PonyButton("Done", onDone, icon = PonyIcons.Check) }
    } else {
        null
    }

    PonyPage(
        title = "Set up Shizuku",
        subtitle = "A few quick steps. Pony checks each one and moves on by itself.",
        onBack = onBack,
        bottomBar = doneBar,
    ) {
        if (state.granted) {
            Banner(
                "Shizuku is ready",
                Tone.Success,
                body = "Pony can run apps on the hidden screen. You're all set.",
                icon = PonyIcons.Check,
            )
        }

        StepCard(
            number = 1,
            title = "Install Shizuku",
            body = "Shizuku is a small, free app that lets Pony use a hidden screen. Get it from the Play Store.",
            status = statusOf(done = state.installed, active = !state.installed),
        ) {
            if (!state.installed) {
                PonyButton("Get Shizuku", onGetShizuku, tone = ButtonTone.Primary, height = 48.dp, icon = PonyIcons.Download)
            }
        }

        StepCard(
            number = 2,
            title = "Turn on Developer options",
            body = "Open About phone and tap \u201CBuild number\u201D seven times, until it says you're now a developer.",
            status = statusOf(done = state.devOptions, active = state.installed && !state.devOptions),
        ) {
            if (state.installed && !state.devOptions) {
                PonyButton("Open About phone", onBuildNumber, tone = ButtonTone.Secondary, height = 48.dp, icon = PonyIcons.Phone)
            }
        }

        StepCard(
            number = 3,
            title = "Turn on Wireless debugging",
            body = "In Developer options, switch Wireless debugging on. If asked, allow it on this network.",
            status = statusOf(done = state.running, active = connecting),
        ) {
            if (connecting) {
                PonyButton("Open Wireless debugging", onWirelessDebugging, tone = ButtonTone.Secondary, height = 48.dp, icon = PonyIcons.Wifi)
            }
        }

        StepCard(
            number = 4,
            title = "Pair Shizuku",
            body = "Open Shizuku and tap Pairing. In Wireless debugging, tap \u201CPair device with pairing code\u201D and type that six-digit code into Shizuku.",
            status = statusOf(done = state.running, active = connecting),
        ) {
            if (connecting) {
                PonyButton("Open Shizuku", onOpenShizuku, tone = ButtonTone.Secondary, height = 48.dp, icon = PonyIcons.Terminal)
            }
        }

        StepCard(
            number = 5,
            title = "Start Shizuku",
            body = "Back in Shizuku, tap Start. It connects to this phone and stays on until the next reboot.",
            status = statusOf(done = state.running, active = connecting),
        )

        StepCard(
            number = 6,
            title = "Let Pony in",
            body = if (state.granted) {
                "Done. Pony has permission to use the hidden screen."
            } else {
                "Pony asks Shizuku for permission on its own. Tap Allow when the box shows up."
            },
            status = statusOf(done = state.granted, active = state.running && !state.granted),
        ) {
            if (state.running && !state.granted) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Spinner(color = Pony.colors.iris, size = 18.dp)
                    Text("Waiting for Shizuku\u2026", style = Pony.type.bodySmall, color = Pony.colors.inkMuted)
                }
                PonyButton("Ask again", onGrant, tone = ButtonTone.Secondary, height = 48.dp)
            }
        }
    }
}

private enum class StepStatus { Done, Active, Todo }

private fun statusOf(done: Boolean, active: Boolean): StepStatus = when {
    done -> StepStatus.Done
    active -> StepStatus.Active
    else -> StepStatus.Todo
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    body: String,
    status: StepStatus,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = Pony.colors
    val railFill = when (status) {
        StepStatus.Done -> colors.success
        StepStatus.Active -> colors.irisFill
        StepStatus.Todo -> colors.surfaceHigh
    }
    val railInk = when (status) {
        StepStatus.Done -> Color.White
        StepStatus.Active -> colors.onIris
        StepStatus.Todo -> colors.inkMuted
    }
    PonyCard(
        padding = PaddingValues(Space.lg),
        border = if (status == StepStatus.Active) colors.iris.copy(alpha = 0.45f) else colors.hairline,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(30.dp).clip(SquircleShape(11.dp)).background(railFill),
                contentAlignment = Alignment.Center,
            ) {
                if (status == StepStatus.Done) {
                    Icon(PonyIcons.Check, contentDescription = null, tint = railInk, modifier = Modifier.size(17.dp))
                } else {
                    Text("$number", style = Pony.type.label, color = railInk)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(title, style = Pony.type.titleSmall, color = if (status == StepStatus.Todo) colors.inkMuted else colors.ink)
                Text(body, style = Pony.type.bodySmall, color = colors.inkMuted)
                content()
            }
        }
    }
}
