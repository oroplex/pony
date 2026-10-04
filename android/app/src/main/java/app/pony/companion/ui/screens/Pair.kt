package app.pony.companion.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pony.companion.ui.CodeBlock
import app.pony.companion.ui.design.Banner
import app.pony.companion.ui.design.BannerAction
import app.pony.companion.ui.design.ButtonTone
import app.pony.companion.ui.design.Haptic
import app.pony.companion.ui.design.IconTile
import app.pony.companion.ui.design.OrbMood
import app.pony.companion.ui.design.PonyButton
import app.pony.companion.ui.design.PonyCard
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.design.PonyPage
import app.pony.companion.ui.design.PonyTextField
import app.pony.companion.ui.design.PresenceOrb
import app.pony.companion.ui.design.SectionHeader
import app.pony.companion.ui.design.Segmented
import app.pony.companion.ui.design.Tone
import app.pony.companion.ui.theme.LocalReduceMotion
import app.pony.companion.ui.theme.LocalStillFrame
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Shapes
import app.pony.companion.ui.theme.Space

enum class PairMode { SCAN, PASTE }

data class PairState(
    val cameraGranted: Boolean,
    val error: String?,
    val pending: Boolean,
    val pairing: Boolean,
    val safetyCode: String?,
    val connectedName: String?,
    val grokName: String,
    val relayHost: String? = null,
    val ownerConfirmed: Boolean = false,
)

@Composable
fun PairScreen(
    state: PairState,
    onRequestCamera: () -> Unit,
    onPair: (String) -> Unit,
    onUsePending: () -> Unit,
    onTemplate: () -> Unit,
    onListenHelp: () -> Unit,
    onDone: () -> Unit,
    onConfirm: () -> Unit = onDone,
    onBack: () -> Unit,
    scanner: @Composable () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(PairMode.SCAN) }
    var draft by rememberSaveable { mutableStateOf("") }
    val code = state.safetyCode
    PonyPage(
        title = if (code != null) "Compare the code" else "Connect ${state.grokName}",
        subtitle = if (code != null) {
            "Make sure ${state.grokName} shows the same six digits. Nothing starts until you tap It matches."
        } else {
            "Scan the code ${state.grokName} shows, or paste its pony:// link. Pairing codes last 15 minutes."
        },
        onBack = onBack,
        bottomBar = if (code != null) {
            {
                PonyButton(
                    if (state.ownerConfirmed) "Sharing the screen…"
                    else if (code != null) "It matches"
                    else "Waiting for ${state.grokName}…",
                    onConfirm,
                    enabled = code != null && !state.ownerConfirmed,
                    icon = PonyIcons.Check,
                    haptic = Haptic.SUCCESS,
                )
            }
        } else {
            null
        },
    ) {
        if (code != null) {
            state.relayHost?.let { host ->
                Banner(
                    "Pairing with $host",
                    Tone.Iris,
                    body = "Only tap It matches if this is the assistant you just asked to pair.",
                    icon = PonyIcons.Link,
                )
            }
            SafetyCodeCard(code, state.connectedName ?: state.grokName, connected = state.connectedName != null)
            return@PonyPage
        }
        if (state.pending) {
            Banner(
                "A pairing link is waiting",
                Tone.Iris,
                body = "It came in before setup was done. Use it now?",
                icon = PonyIcons.Link,
                actions = listOf(BannerAction("Use the link", onUsePending, primary = true)),
            )
        }
        state.error?.let { Banner(it, Tone.Danger, icon = PonyIcons.AlertCircle) }
        Segmented(listOf(PairMode.SCAN to "Scan code", PairMode.PASTE to "Paste link"), mode, { mode = it })
        when (mode) {
            PairMode.SCAN -> if (state.cameraGranted) {
                Viewfinder(scanner)
            } else {
                PonyCard(spacing = Space.lg) {
                    IconTile(PonyIcons.Camera, Tone.Iris, size = 48.dp)
                    Text("Pony needs the camera to scan", style = Pony.type.titleSmall, color = Pony.colors.ink)
                    Text("The camera is used only on this screen, to read the pairing code.", style = Pony.type.bodySmall, color = Pony.colors.inkMuted)
                    PonyButton("Allow the camera", onRequestCamera, tone = ButtonTone.Iris, icon = PonyIcons.Camera)
                }
            }
            PairMode.PASTE -> PonyCard(spacing = Space.lg) {
                PonyTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "Pairing link or code",
                    placeholder = "pony://pair?v=1&relay=…",
                    singleLine = false,
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                PonyButton("Pair", { onPair(draft) }, enabled = draft.isNotBlank(), icon = PonyIcons.Link)
            }
        }
        SectionHeader("No code yet?")
        PonyCard(spacing = Space.md) {
            Text("Set up Grok Bot once", style = Pony.type.titleSmall, color = Pony.colors.ink)
            Text(
                "Run Pony's listener on your computer. It shows a code for this screen and keeps Grok Bot connected, so asks from this phone get answered right away.",
                style = Pony.type.bodySmall,
                color = Pony.colors.inkMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PonyButton("How to set up", onListenHelp, tone = ButtonTone.Secondary, height = 48.dp, modifier = Modifier.weight(1f))
                PonyButton("Template", onTemplate, tone = ButtonTone.Secondary, height = 48.dp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Viewfinder(scanner: @Composable () -> Unit) {
    val colors = Pony.colors
    val still = LocalStillFrame.current || LocalReduceMotion.current
    val sweep = if (still) {
        0.42f
    } else {
        val t = rememberInfiniteTransition(label = "scan")
        val s by t.animateFloat(0.08f, 0.92f, infiniteRepeatable(tween(2_200, easing = LinearEasing), RepeatMode.Reverse), label = "scan-line")
        s
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(Shapes.card)
            .background(Color.Black)
            .semantics { contentDescription = "Camera viewfinder. Point it at the pairing code." },
    ) {
        scanner()
        Canvas(Modifier.fillMaxSize().padding(28.dp)) {
            val w = size.width
            val h = size.height
            val arm = w * 0.14f
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val corner = colors.iris
            fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(corner, Offset(x, y), Offset(x + dx * arm, y), stroke.width, StrokeCap.Round)
                drawLine(corner, Offset(x, y), Offset(x, y + dy * arm), stroke.width, StrokeCap.Round)
            }
            bracket(0f, 0f, 1f, 1f)
            bracket(w, 0f, -1f, 1f)
            bracket(0f, h, 1f, -1f)
            bracket(w, h, -1f, -1f)
            val y = h * sweep
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, corner.copy(alpha = 0.35f), Color.Transparent), startY = y - 40f, endY = y + 40f),
                topLeft = Offset(0f, y - 40f),
                size = androidx.compose.ui.geometry.Size(w, 80f),
            )
            drawLine(corner.copy(alpha = 0.9f), Offset(w * 0.04f, y), Offset(w * 0.96f, y), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun SafetyCodeCard(code: String, name: String, connected: Boolean) {
    val colors = Pony.colors
    PonyCard(border = colors.mane.copy(alpha = 0.45f), spacing = Space.lg) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PresenceOrb(if (connected) OrbMood.Success else OrbMood.Thinking, size = 120.dp)
        }
        Text("SAFETY CODE", style = Pony.type.overline, color = colors.mane, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(
            code.replace("-", "  "),
            style = Pony.type.code,
            color = colors.mane,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Safety code ${code.toList().joinToString(" ")}" },
            textAlign = TextAlign.Center,
        )
        Text(
            if (connected) "$name is connected. The session is end-to-end encrypted; the relay can't read it." else "Pony is finishing the secure handshake with $name…",
            style = Pony.type.bodySmall,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

data class ListenCommand(val title: String, val body: String, val code: String)

@Composable
fun ListenHelpScreen(relay: String, onCopy: (String) -> Unit, onBack: () -> Unit) {
    val relayFlag = if (relay.isBlank()) "" else " --relay $relay"
    val commands = listOf(
        ListenCommand(
            "Keep Grok Bot listening",
            "From the Pony folder on your computer. It pairs once, stays connected, reconnects after drops, and prints every ask from this phone. Add --exec to hand each ask to your agent; the words are in \$PONY_REQUEST_TEXT.",
            if (relayFlag.isEmpty()) "npm run listen" else "npm run listen --$relayFlag",
        ),
        ListenCommand(
            "Or give Grok Bot the MCP server in listen mode",
            "For MCP hosts like Grok Bot: point the host at http://127.0.0.1:43123/mcp. It remembers the pairing, reconnects by itself, and wait_for_request returns asks the moment they arrive.",
            "npm run -s mcp -- --listen --http 43123$relayFlag",
        ),
    )
    PonyPage(
        title = "Keep it listening",
        subtitle = "Grok Bot answers asks from this phone only while something on your computer is listening for them.",
        onBack = onBack,
    ) {
        commands.forEach { command ->
            PonyCard(spacing = Space.md) {
                Text(command.title, style = Pony.type.titleSmall, color = Pony.colors.ink)
                Text(command.body, style = Pony.type.bodySmall, color = Pony.colors.inkMuted)
                CodeBlock(command.code, onCopy = { onCopy(command.code) })
            }
        }
        Banner(
            "Scan the code it prints",
            Tone.Iris,
            body = "The listener prints a QR and a pony:// link on its first run; in listen mode, Grok Bot's pair tool shows them. Scan it from Connect Grok Bot. After that it reconnects by itself.",
            icon = PonyIcons.Qr,
        )
    }
}
