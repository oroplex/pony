package app.pony.companion.voice

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.pony.companion.overlay.AssistantPanel
import app.pony.companion.overlay.OverlayOwner
import app.pony.companion.ui.theme.PonyTheme

class PonyVoiceInteractionService : VoiceInteractionService()

class PonyVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = PonyVoiceSession(this)
}

/**
 * The assistant gesture. It listens, hands the words to Pony, and closes
 * itself after the result, on Stop, on Back, or on a tap outside. A task
 * that is already running keeps going; only listening stops when it hides.
 */
class PonyVoiceSession(context: Context) : VoiceInteractionSession(context) {
    private var owner: OverlayOwner? = null
    private var openedAt = 0L

    override fun onCreate() {
        super.onCreate()
        window?.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setDimAmount(0f)
        }
    }

    override fun onCreateContentView(): View {
        val lifecycle = OverlayOwner().also { it.start() }
        owner = lifecycle
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setViewTreeViewModelStoreOwner(lifecycle)
            setContent {
                PonyTheme(dark = true) {
                    AssistantPanel(onClose = { hide() }, openedAt = openedAt)
                }
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        openedAt = System.currentTimeMillis()
        VoiceController.listen(context, "assistant")
    }

    override fun onBackPressed() {
        hide()
    }

    override fun onHide() {
        if (VoiceController.isListening()) VoiceController.cancelListening()
        super.onHide()
    }

    override fun onDestroy() {
        owner?.stop()
        owner = null
        super.onDestroy()
    }
}
