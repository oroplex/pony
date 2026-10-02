package app.pony.companion.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Required so Pony can hold the assistant role. The system binds this service.
 * Listening for the owner uses [SpeechInput], which prefers the on-device recognizer
 * and does not call back into this service.
 */
class PonyRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
