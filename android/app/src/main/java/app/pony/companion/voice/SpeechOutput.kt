package app.pony.companion.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object SpeechOutput {
    private val main = Handler(Looper.getMainLooper())
    private val engine = AtomicReference<TextToSpeech?>(null)
    private var utterance = 0

    fun stop() {
        main.post { engine.get()?.stop() }
    }

    fun speak(context: Context, text: String, rate: Float, voiceName: String): Boolean {
        if (text.isBlank()) return true
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        val app = context.applicationContext
        val ready = CountDownLatch(1)
        var failed = false
        main.post {
            val existing = engine.get()
            if (existing != null) {
                configure(existing, rate, voiceName)
                ready.countDown()
            } else {
                var created: TextToSpeech? = null
                created = TextToSpeech(app) { status ->
                    if (status == TextToSpeech.SUCCESS && created != null) {
                        engine.set(created)
                        configure(created!!, rate, voiceName)
                    } else {
                        failed = true
                    }
                    ready.countDown()
                }
            }
        }
        if (!ready.await(8, TimeUnit.SECONDS) || failed) return false
        val tts = engine.get() ?: return false
        val done = CountDownLatch(1)
        val id = "pony-${++utterance}"
        main.post {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) done.countDown()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id) done.countDown()
                }
            })
            val params = android.os.Bundle()
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        }
        return done.await(45, TimeUnit.SECONDS)
    }

    fun loadVoices(context: Context, done: (List<VoiceCatalog.Option>) -> Unit) {
        val allowCloud = VoicePrefs.cloudVoice(context)
        main.post {
            lateinit var created: TextToSpeech
            created = TextToSpeech(context.applicationContext) { status ->
                val options = if (status == TextToSpeech.SUCCESS) {
                    VoiceCatalog.ordered(
                        created.voices?.map { voice ->
                            VoiceCatalog.Option(
                                name = voice.name,
                                language = runCatching { voice.locale?.toLanguageTag() }.getOrNull().orEmpty(),
                                quality = voice.quality,
                                networkRequired = voice.isNetworkConnectionRequired,
                            )
                        }.orEmpty(),
                        Locale.getDefault().language,
                        allowCloud,
                    )
                } else {
                    emptyList()
                }
                created.shutdown()
                done(options)
            }
        }
    }

    private fun configure(tts: TextToSpeech, rate: Float, voiceName: String) {
        tts.language = Locale.getDefault()
        tts.setSpeechRate(rate.coerceIn(0.7f, 1.4f))
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        if (voiceName.isNotBlank()) {
            tts.voices?.firstOrNull { it.name == voiceName }?.let { tts.voice = it }
        }
    }
}
