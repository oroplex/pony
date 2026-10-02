package app.pony.companion.voice

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.pony.companion.overlay.AssistantPanel
import app.pony.companion.ui.theme.PonyTheme

/**
 * A thin, see-through launch pad for the home-screen widget and the Quick
 * Settings tile. It comes to the front just long enough for the microphone to be
 * allowed, starts listening, and shows the small assistant card — never the full
 * app. It closes itself once the ask is handed off, or when Pony steps aside to
 * work in another app. If the mic isn't allowed yet, the card's "Allow
 * microphone" button takes over.
 */
class AskLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val openedAt = System.currentTimeMillis()
        setContent {
            PonyTheme {
                AssistantPanel(onClose = { finish() }, openedAt = openedAt)
            }
        }
        startListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startListening()
    }

    private fun startListening() {
        val source = VoiceLaunch.source(intent.getStringExtra(VoiceLaunch.EXTRA_SOURCE))
        if (!VoiceController.isListening()) VoiceController.listen(this, source)
    }
}
