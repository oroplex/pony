package app.pony.companion.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.pony.companion.recap.RecapShots
import app.pony.companion.recap.UndoableAction
import app.pony.companion.ui.design.ButtonTone
import app.pony.companion.ui.design.Haptic
import app.pony.companion.ui.design.PonyButton
import app.pony.companion.ui.design.PonyIcons
import app.pony.companion.ui.theme.Pony
import app.pony.companion.ui.theme.Space
import app.pony.companion.ui.theme.SquircleShape

/**
 * The before/after screenshots of the work, side by side. These live only in
 * memory for the latest task (see [RecapShots]), so this quietly renders nothing
 * for any older task. Shown in the Ask outcome card and in History → Task detail.
 */
@Composable
fun RecapShotsRow(shots: RecapShots.Shots, modifier: Modifier = Modifier) {
    val before = rememberJpeg(shots.before)
    val after = rememberJpeg(shots.after)
    if (before == null && after == null) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        RecapShot("Before", before, Modifier.weight(1f))
        RecapShot("After", after, Modifier.weight(1f))
    }
}

/**
 * The one safe undo the recap offered for the latest task, with a small confirm
 * step. Collapses to an "Undone" line once run. Shown nowhere once the task has
 * committed something that left the phone — [RecapBuilder] withholds the offer.
 */
@Composable
fun UndoControl(undo: UndoableAction, onUndo: () -> Unit) {
    val colors = Pony.colors
    var confirming by remember(undo) { mutableStateOf(false) }
    var done by remember(undo) { mutableStateOf(false) }
    when {
        done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Icon(PonyIcons.CheckCircle, contentDescription = null, tint = colors.mane, modifier = Modifier.size(18.dp))
            Text("Undone", style = Pony.type.bodySmall, color = colors.inkMuted)
        }
        confirming -> Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(undo.confirm, style = Pony.type.bodySmall, color = colors.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PonyButton("Cancel", { confirming = false }, tone = ButtonTone.Secondary, height = 44.dp, modifier = Modifier.weight(1f), haptic = Haptic.TICK)
                PonyButton("Undo", { confirming = false; done = true; onUndo() }, tone = ButtonTone.Danger, height = 44.dp, modifier = Modifier.weight(1f), icon = PonyIcons.Undo, haptic = Haptic.CONFIRM)
            }
        }
        else -> PonyButton(undo.label, { confirming = true }, tone = ButtonTone.Secondary, height = 44.dp, icon = PonyIcons.Undo, haptic = Haptic.TAP)
    }
}

@Composable
private fun RecapShot(label: String, image: ImageBitmap?, modifier: Modifier) {
    val colors = Pony.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = Pony.type.caption, color = colors.inkFaint)
        val shape = SquircleShape(14.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp, max = 180.dp)
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.hairline, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "$label screenshot",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("—", style = Pony.type.body, color = colors.inkFaint)
            }
        }
    }
}

@Composable
private fun rememberJpeg(bytes: ByteArray?): ImageBitmap? = remember(bytes) {
    bytes?.takeIf { it.isNotEmpty() }?.let { data ->
        runCatching { android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }.getOrNull()
    }
}
