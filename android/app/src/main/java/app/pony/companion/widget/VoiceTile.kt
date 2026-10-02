package app.pony.companion.widget

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.pony.companion.R
import app.pony.companion.voice.VoiceLaunch

/**
 * Quick Settings tile that starts a voice ask without opening the app. A tap
 * brings up listening (unlocking first if the phone is locked) and leaves the
 * shade.
 */
class VoiceTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        val launch = { startVoice() }
        if (isLocked) unlockAndRun(launch) else launch()
    }

    private fun startVoice() {
        val pending = VoiceLaunch.pending(this, VoiceLaunch.SOURCE_TILE)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(VoiceLaunch.intent(this, VoiceLaunch.SOURCE_TILE))
        }
    }
}
