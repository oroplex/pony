package app.pony.companion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pony.companion.brain.ProviderDraft
import app.pony.companion.brain.ProviderPreset
import app.pony.companion.brain.ProviderRecord
import app.pony.companion.ui.BrainChoice
import app.pony.companion.ui.brainChoices
import app.pony.companion.ui.design.Banner
import app.pony.companion.ui.design.ButtonTone
import app.pony.companion.ui.design.Haptic
import app.pony.companion.ui.design.IconTile
import app.pony.companion.ui.design.IconTone
import app.pony.companion.ui.design.ListGroup
import app.pony.companion.ui.design.PonyButton
import app.pony.companion.ui.design.PonyCard
import app.pony.companion.ui.design.PonyIconButton
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.design.PonyPage
import app.pony.companion.ui.design.PonySheet
import app.pony.companion.ui.design.PonyTextField
import app.pony.companion.ui.design.SectionHeader
import app.pony.companion.ui.design.StatusPill
import app.pony.companion.ui.design.SuggestionChip
import app.pony.companion.ui.design.Tag
import app.pony.companion.ui.design.TextAction
import app.pony.companion.ui.design.Tone
import app.pony.companion.ui.design.pressable
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Space

data class BrainsState(
    val grokName: String,
    val grokSelected: Boolean,
    val grokConnected: Boolean,
    val grokListening: Boolean,
    val providers: List<ProviderRecord>,
)

/**
 * The Connect picker. It lists every brain Pony supports: Grok Bot (paired over
 * Pony Cloud) and each API-key brain that runs on the phone. Tapping a brain
 * that isn't set up opens its setup sheet; a connected brain is selectable and
 * can be disconnected. OpenRouter and Custom live under Advanced.
 */
@Composable
fun BrainsScreen(
    state: BrainsState,
    onUseGrok: () -> Unit,
    onUsePhone: (String?) -> Unit,
    onSave: (ProviderDraft) -> String?,
    onDelete: (String) -> Unit,
    onTest: (ProviderDraft, String, (Boolean, String) -> Unit) -> Unit,
    onConnect: (ProviderPreset, String, (Boolean, String) -> Unit) -> Unit,
    onGetKey: (String) -> Unit,
    onPair: () -> Unit,
    onDisconnectGrok: () -> Unit,
    onTemplate: () -> Unit,
    onListenHelp: () -> Unit,
    onBack: () -> Unit,
    startEditing: Boolean = false,
    startSetup: ProviderPreset? = null,
) {
    var editing by remember { mutableStateOf(if (startEditing) customDraft() else null) }
    var setupPreset by remember { mutableStateOf(startSetup) }
    var confirm by remember { mutableStateOf<ConfirmSpec?>(null) }
    val choices = brainChoices(state.providers, phoneBrainSelected = !state.grokSelected)

    // Disconnecting one brain never touches the others: it clears just this key.
    // BrainLibrary promotes another saved brain to active, or Pony falls back to
    // the "No brain yet" state when nothing is left.
    val askDisconnectBrain: (String, String) -> Unit = { label, recordId ->
        confirm = ConfirmSpec(
            title = "Disconnect $label?",
            body = "Pony clears its key from this phone. Your other brains stay connected.",
            confirmLabel = "Disconnect $label",
            onConfirm = { onDelete(recordId) },
        )
    }
    val askDisconnectGrok: () -> Unit = {
        confirm = ConfirmSpec(
            title = "Disconnect ${state.grokName}?",
            body = "Pony ends the live session and unpairs ${state.grokName}. Your saved brains stay connected.",
            confirmLabel = "Disconnect ${state.grokName}",
            onConfirm = onDisconnectGrok,
        )
    }

    Box(Modifier.fillMaxSize()) {
        val draft = editing
        if (draft != null) {
            BrainEditor(
                draft = draft,
                saved = state.providers.firstOrNull { it.id == draft.id },
                onSave = onSave,
                onDelete = onDelete,
                onTest = onTest,
                onChange = { editing = it },
                onClose = { editing = null },
            )
        } else {
            BrainPicker(
                state = state,
                choices = choices,
                onUseGrok = onUseGrok,
                onPair = onPair,
                onTemplate = onTemplate,
                onListenHelp = onListenHelp,
                onBack = onBack,
                onSetup = { setupPreset = it },
                onDisconnectBrain = askDisconnectBrain,
                onDisconnectGrok = askDisconnectGrok,
                onAddCustom = { editing = customDraft() },
                onEdit = { record -> editing = ProviderDraft(record.id, record.name, record.preset, record.model, record.baseUrl, null) },
            )
        }
        SetupSheet(
            preset = setupPreset,
            choice = setupPreset?.let { p -> choices.firstOrNull { it.preset == p } },
            onDismiss = { setupPreset = null },
            onGetKey = onGetKey,
            onConnect = onConnect,
            onUse = { recordId -> onUsePhone(recordId); setupPreset = null },
            onDisconnect = askDisconnectBrain,
        )
        ConfirmSheet(
            spec = confirm,
            onDismiss = { confirm = null },
            onConfirm = {
                confirm?.onConfirm?.invoke()
                confirm = null
                setupPreset = null
            },
        )
    }
}

@Composable
private fun BrainPicker(
    state: BrainsState,
    choices: List<BrainChoice>,
    onUseGrok: () -> Unit,
    onPair: () -> Unit,
    onTemplate: () -> Unit,
    onListenHelp: () -> Unit,
    onBack: () -> Unit,
    onSetup: (ProviderPreset) -> Unit,
    onDisconnectBrain: (String, String) -> Unit,
    onDisconnectGrok: () -> Unit,
    onAddCustom: () -> Unit,
    onEdit: (ProviderRecord) -> Unit,
) {
    var advanced by remember { mutableStateOf(false) }
    val custom = state.providers.filter { it.preset !in ProviderPreset.pickable }
    PonyPage(
        title = "Brains",
        subtitle = "Pick who thinks for Pony. Link your Grok Bot, or connect a brain that runs right here with your own API key.",
        onBack = onBack,
    ) {
        GrokCard(state, onUseGrok, onPair, onListenHelp, onTemplate, onDisconnectGrok)

        SectionHeader("On this phone")
        ListGroup(
            rows = choices.map { choice ->
                {
                    ProviderChoiceRow(
                        choice = choice,
                        onManage = { onSetup(choice.preset) },
                        onDisconnect = { choice.recordId?.let { onDisconnectBrain(choice.preset.title, it) } },
                    )
                }
            },
        )
        val backup = state.providers.firstOrNull { it.active }
        if (state.grokSelected && backup != null) {
            Banner(
                "${backup.name} is Grok Bot's backup",
                Tone.Iris,
                body = "When Grok Bot isn't connected or doesn't pick up an ask within a few seconds, ${backup.name} runs it on this phone.",
                icon = PonyIcons.Shield,
            )
        }

        SectionHeader("Advanced", action = if (advanced) "Hide" else "Show", onAction = { advanced = !advanced })
        AnimatedVisibility(advanced) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                if (custom.isNotEmpty()) {
                    ListGroup(
                        rows = custom.map { record ->
                            {
                                BrainRow(
                                    record = record,
                                    grokSelected = state.grokSelected,
                                    onEdit = { onEdit(record) },
                                    onRemove = { onDisconnectBrain(record.name, record.id) },
                                )
                            }
                        },
                    )
                }
                PonyButton("Add a custom or OpenRouter brain", onAddCustom, tone = ButtonTone.Secondary, icon = PonyIcons.Plus, height = 48.dp)
            }
        }
    }
}

@Composable
private fun GrokCard(
    state: BrainsState,
    onUseGrok: () -> Unit,
    onPair: () -> Unit,
    onListenHelp: () -> Unit,
    onTemplate: () -> Unit,
    onDisconnect: () -> Unit,
) {
    PonyCard(border = if (state.grokSelected) Pony.colors.iris.copy(alpha = 0.55f) else Pony.colors.hairline, spacing = Space.md) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            IconTile(PonyIcons.Bot, Tone.Iris, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.grokName, style = Pony.type.title, color = Pony.colors.ink)
                    if (state.grokSelected) Tag("In use", Tone.Iris)
                }
                Text("Your Grok Bot controls this phone through Pony. No API key.", style = Pony.type.bodySmall, color = Pony.colors.inkMuted)
            }
        }
        StatusPill(
            when {
                state.grokConnected && state.grokListening -> "Listening"
                state.grokConnected -> "Connected"
                else -> "Not connected"
            },
            when {
                state.grokConnected && state.grokListening -> Tone.Success
                state.grokConnected -> Tone.Iris
                else -> Tone.Neutral
            },
            live = state.grokConnected && state.grokListening,
        )
        when {
            !state.grokConnected -> PonyButton("Pair Grok Bot", onPair, tone = ButtonTone.Iris, height = 48.dp, icon = PonyIcons.Qr)
            !state.grokSelected -> PonyButton("Use ${state.grokName}", onUseGrok, tone = ButtonTone.Iris, height = 48.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            PonyButton("Keep it listening", onListenHelp, tone = ButtonTone.Secondary, height = 48.dp, modifier = Modifier.weight(1.3f))
            PonyButton("Template", onTemplate, tone = ButtonTone.Secondary, height = 48.dp, modifier = Modifier.weight(1f))
        }
        if (state.grokConnected) {
            PonyButton(
                "Disconnect ${state.grokName}",
                onDisconnect,
                tone = ButtonTone.Quiet,
                icon = PonyIcons.Unplug,
                height = 48.dp,
                haptic = Haptic.REJECT,
            )
        }
    }
}

@Composable
private fun ProviderChoiceRow(
    choice: BrainChoice,
    onManage: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val preset = choice.preset
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .pressable(
                onClick = onManage,
                label = if (choice.connected) "Manage ${preset.title}" else "Connect ${preset.title}",
                haptic = Haptic.TAP,
                pressedScale = 0.985f,
            )
            .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        IconTile(PonyIcons.Key, if (choice.active) Tone.Iris else Tone.Neutral)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(preset.title, style = Pony.type.bodyStrong, color = Pony.colors.ink)
                when {
                    choice.active -> Tag("In use", Tone.Iris)
                    choice.connected -> Icon(PonyIcons.CheckCircle, contentDescription = "Connected", tint = Pony.colors.success, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                if (choice.connected) "Connected · ••••${choice.keyLast4}" else preset.blurb,
                style = Pony.type.bodySmall,
                color = Pony.colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (choice.connected) {
            PonyIconButton(PonyIcons.Unplug, "Disconnect ${preset.title}", onDisconnect, tone = IconTone.Danger)
        } else {
            PonyButton("Connect", onManage, tone = ButtonTone.Secondary, height = 40.dp, fillWidth = false)
        }
    }
}

/**
 * The per-provider setup and manage sheet. For a brain that isn't connected it
 * shows one line of what it is, a button to the real key page, a paste field
 * with a one-tap clipboard paste, and Test & Save (a cheap call validates the
 * key before sealing it on the phone). For a connected brain it adds a way to
 * use it, replace its key, and a clear Disconnect button that asks to confirm.
 */
@Composable
private fun SetupSheet(
    preset: ProviderPreset?,
    choice: BrainChoice?,
    onDismiss: () -> Unit,
    onGetKey: (String) -> Unit,
    onConnect: (ProviderPreset, String, (Boolean, String) -> Unit) -> Unit,
    onUse: (String?) -> Unit,
    onDisconnect: (String, String) -> Unit,
) {
    var shown by remember { mutableStateOf<ProviderPreset?>(null) }
    LaunchedEffect(preset) { if (preset != null) shown = preset }
    val target = shown
    val connected = choice?.connected == true
    val recordId = choice?.recordId
    val clipboard = LocalClipboardManager.current
    var pasted by remember(preset) { mutableStateOf("") }
    var busy by remember(preset) { mutableStateOf(false) }
    var message by remember(preset) { mutableStateOf<Pair<Boolean, String>?>(null) }
    PonySheet(
        visible = preset != null,
        onDismiss = onDismiss,
        title = target?.let { if (connected) it.title else "Connect ${it.title}" } ?: "",
        subtitle = target?.let {
            if (!connected) {
                it.blurb
            } else {
                "Connected · ••••${choice?.keyLast4}" + if (choice?.active == true) " · in use" else ""
            }
        },
    ) {
        if (target == null) return@PonySheet
        if (connected && choice?.active != true) {
            PonyButton("Use ${target.title}", { onUse(recordId) }, tone = ButtonTone.Iris, icon = PonyIcons.Check)
        }
        PonyButton(
            "Get your ${target.provider} API key",
            { onGetKey(target.keyUrl) },
            tone = ButtonTone.Secondary,
            icon = PonyIcons.External,
            haptic = Haptic.TAP,
        )
        PonyTextField(
            pasted,
            { pasted = it; message = null },
            label = "API key",
            placeholder = if (connected) "Paste a new key to replace it" else "Paste your ${target.provider} key",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            trailing = {
                TextAction("Paste", { clipboard.getText()?.text?.let { pasted = it.trim() } }, icon = PonyIcons.Copy)
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Icon(PonyIcons.Lock, contentDescription = null, tint = Pony.colors.inkFaint, modifier = Modifier.size(15.dp))
            Text("Saved only on this phone, sealed by the Android keystore.", style = Pony.type.bodySmall, color = Pony.colors.inkFaint)
        }
        message?.let { (ok, text) -> Banner(text, if (ok) Tone.Success else Tone.Danger, icon = if (ok) PonyIcons.CheckCircle else PonyIcons.AlertCircle) }
        PonyButton(
            if (busy) "Testing…" else if (connected) "Replace key" else "Test & Save",
            {
                busy = true
                message = null
                onConnect(target, pasted) { ok, text ->
                    busy = false
                    if (ok) onDismiss() else message = false to text
                }
            },
            icon = PonyIcons.Check,
            loading = busy,
            enabled = pasted.isNotBlank(),
        )
        if (connected && recordId != null) {
            PonyButton(
                "Disconnect ${target.title}",
                { onDisconnect(target.title, recordId) },
                tone = ButtonTone.Danger,
                icon = PonyIcons.Unplug,
                haptic = Haptic.REJECT,
            )
        }
    }
}

/** A short "are you sure?" for a disconnect: one danger button and a way out. */
private data class ConfirmSpec(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

@Composable
private fun ConfirmSheet(spec: ConfirmSpec?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var shown by remember { mutableStateOf<ConfirmSpec?>(null) }
    LaunchedEffect(spec) { if (spec != null) shown = spec }
    val target = shown
    PonySheet(
        visible = spec != null,
        onDismiss = onDismiss,
        title = target?.title ?: "",
        subtitle = target?.body,
    ) {
        if (target == null) return@PonySheet
        PonyButton(target.confirmLabel, onConfirm, tone = ButtonTone.Danger, icon = PonyIcons.Unplug, haptic = Haptic.REJECT)
        PonyButton("Keep it connected", onDismiss, tone = ButtonTone.Secondary)
    }
}

@Composable
private fun BrainEditor(
    draft: ProviderDraft,
    saved: ProviderRecord?,
    onSave: (ProviderDraft) -> String?,
    onDelete: (String) -> Unit,
    onTest: (ProviderDraft, String, (Boolean, String) -> Unit) -> Unit,
    onChange: (ProviderDraft) -> Unit,
    onClose: () -> Unit,
) {
    var pasted by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    PonyPage(
        title = if (draft.id == null) "Add a brain" else "Edit ${draft.name}",
        subtitle = "The key stays on this phone, sealed by the Android keystore. Pony shows only its last four characters.",
        onBack = onClose,
        bottomBar = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PonyButton(
                    if (busy) "Testing…" else "Test key",
                    {
                        busy = true
                        message = null
                        onTest(draft, pasted) { ok, text ->
                            busy = false
                            message = ok to text
                        }
                    },
                    tone = ButtonTone.Secondary,
                    loading = busy,
                    modifier = Modifier.weight(1f),
                )
                PonyButton(
                    "Save",
                    {
                        val err = onSave(draft.copy(newKey = pasted.trim().ifEmpty { null }))
                        if (err == null) {
                            onClose()
                        } else {
                            message = false to err
                        }
                    },
                    modifier = Modifier.weight(1f),
                    icon = PonyIcons.Check,
                )
            }
        },
    ) {
        Editor(draft, pasted, saved, onChange = onChange, onPaste = { pasted = it })
        message?.let { (ok, text) -> Banner(text, if (ok) Tone.Success else Tone.Danger, icon = if (ok) PonyIcons.CheckCircle else PonyIcons.AlertCircle) }
        if (draft.id != null) {
            PonyButton("Delete this brain", { onDelete(draft.id); onClose() }, tone = ButtonTone.Quiet, icon = PonyIcons.Trash, haptic = Haptic.REJECT)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editor(draft: ProviderDraft, pasted: String, saved: ProviderRecord?, onChange: (ProviderDraft) -> Unit, onPaste: (String) -> Unit) {
    var advanced by remember(draft.preset) { mutableStateOf(draft.preset == ProviderPreset.CUSTOM) }
    SectionHeader("Provider")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        ProviderPreset.entries.forEach { preset ->
            SuggestionChip(
                preset.title,
                {
                    onChange(
                        draft.copy(
                            preset = preset,
                            name = if (draft.name.isBlank() || ProviderPreset.entries.any { it.title == draft.name }) preset.title else draft.name,
                            model = preset.defaultModel.ifBlank { draft.model },
                            baseUrl = if (preset == ProviderPreset.CUSTOM) draft.baseUrl else preset.defaultBaseUrl,
                        ),
                    )
                },
                selected = draft.preset == preset,
            )
        }
    }
    PonyCard(spacing = Space.lg) {
        PonyTextField(draft.name, { onChange(draft.copy(name = it)) }, label = "Name", placeholder = "My brain")
        PonyTextField(
            pasted,
            onPaste,
            label = "API key",
            placeholder = if (saved != null) "Saved ••••${saved.keyLast4}. Paste a new key to replace it." else "Paste your key",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        )
    }
    SectionHeader("Advanced", action = if (advanced) "Hide" else "Show", onAction = { advanced = !advanced })
    if (!advanced) {
        Text(
            "${draft.preset.title} defaults · ${draft.model.ifBlank { "model set by provider" }}",
            style = Pony.type.bodySmall,
            color = Pony.colors.inkFaint,
        )
    }
    AnimatedVisibility(advanced) {
        PonyCard(spacing = Space.lg) {
            PonyTextField(draft.model, { onChange(draft.copy(model = it)) }, label = "Model", placeholder = "model-name")
            PonyTextField(
                draft.baseUrl,
                { if (draft.preset == ProviderPreset.CUSTOM) onChange(draft.copy(baseUrl = it)) },
                label = "Base URL",
                placeholder = "https://…",
                enabled = draft.preset == ProviderPreset.CUSTOM,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
    }
}

@Composable
private fun BrainRow(
    record: ProviderRecord,
    grokSelected: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .pressable(onClick = onEdit, label = "Edit ${record.name}", haptic = Haptic.TAP, pressedScale = 0.985f)
            .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        IconTile(PonyIcons.Key, Tone.Iris)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(record.name, style = Pony.type.bodyStrong, color = Pony.colors.ink)
                if (record.active) Tag(if (grokSelected) "Backup" else "In use", if (grokSelected) Tone.Neutral else Tone.Iris)
            }
            Text(
                "${record.preset.title} · ${record.model} · ••••${record.keyLast4}",
                style = Pony.type.bodySmall,
                color = Pony.colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PonyIconButton(PonyIcons.Unplug, "Remove ${record.name}", onRemove)
    }
}

private fun customDraft(): ProviderDraft {
    val preset = ProviderPreset.CUSTOM
    return ProviderDraft(null, preset.title, preset, preset.defaultModel, preset.defaultBaseUrl, null)
}
