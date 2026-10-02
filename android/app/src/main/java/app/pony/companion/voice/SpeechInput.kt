package app.pony.companion.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Why listening failed, in words the owner can act on. */
class SpeechProblem(message: String, val kind: Kind) : Exception(message) {
    enum class Kind { NO_MIC_PERMISSION, NOTHING_HEARD, UNAVAILABLE, DOWNLOADING, BUSY, STOPPED, OTHER }
}

object SpeechInput {
    private val main = Handler(Looper.getMainLooper())
    private val current = AtomicReference<SpeechRecognizer?>(null)

    fun cancel() {
        main.post {
            current.getAndSet(null)?.let { recognizer ->
                runCatching { recognizer.cancel() }
                runCatching { recognizer.destroy() }
            }
        }
    }

    fun micGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Listens once. [onPartial] receives words as they are recognized and
     * [onLevel] a 0–1 voice level, both on the main thread.
     */
    fun listen(
        context: Context,
        onPartial: (String) -> Unit = {},
        onLevel: (Float) -> Unit = {},
        onReady: () -> Unit = {},
        stop: () -> Boolean,
    ): Result<String> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return Result.failure(IllegalStateException("speech must not run on the main thread"))
        }
        val app = context.applicationContext
        if (!micGranted(app)) {
            return Result.failure(SpeechProblem("Pony needs the microphone to hear you.", SpeechProblem.Kind.NO_MIC_PERMISSION))
        }
        prepareOnDevice(app)?.let { return Result.failure(it) }
        val started = CountDownLatch(1)
        val done = CountDownLatch(1)
        var heard: String? = null
        var partial = ""
        var failure: SpeechProblem? = null
        main.post {
            val recognizer = create(app)
            if (recognizer == null) {
                failure = SpeechProblem(
                    "Speech recognition isn't available on this phone. Install or enable Speech Recognition and Synthesis from Google.",
                    SpeechProblem.Kind.UNAVAILABLE,
                )
                started.countDown()
                done.countDown()
                return@post
            }
            current.set(recognizer)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onReady()
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = onLevel(0f)
                override fun onPartialResults(partialResults: Bundle?) {
                    val words = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!words.isNullOrBlank()) {
                        partial = words
                        onPartial(words)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onError(error: Int) {
                    failure = problem(error)
                    done.countDown()
                }

                override fun onResults(results: Bundle?) {
                    heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    done.countDown()
                }
            })
            try {
                recognizer.startListening(intent())
            } catch (err: Exception) {
                failure = SpeechProblem(err.message ?: "Speech didn't start.", SpeechProblem.Kind.OTHER)
                done.countDown()
            }
            started.countDown()
        }
        if (!started.await(3, TimeUnit.SECONDS)) {
            return Result.failure(SpeechProblem("Speech didn't start. Try again.", SpeechProblem.Kind.OTHER))
        }
        val deadline = System.currentTimeMillis() + 20_000
        while (!done.await(150, TimeUnit.MILLISECONDS)) {
            if (stop()) {
                cancel()
                return Result.failure(SpeechProblem("Stopped.", SpeechProblem.Kind.STOPPED))
            }
            if (System.currentTimeMillis() > deadline) {
                cancel()
                return if (partial.isNotBlank()) Result.success(partial.trim()) else Result.failure(nothingHeard())
            }
        }
        cancel()
        val text = heard?.trim().orEmpty().ifEmpty { partial.trim() }
        if (text.isNotEmpty() && (failure == null || failure?.kind == SpeechProblem.Kind.NOTHING_HEARD)) return Result.success(text)
        return Result.failure(failure ?: nothingHeard())
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_600L)
    }

    /**
     * On Android 13+, checks that this language's on-device model is installed
     * and starts the download if it isn't. Returns a problem to show, or null to listen.
     */
    private fun prepareOnDevice(context: Context): SpeechProblem? {
        if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return null
        val latch = CountDownLatch(1)
        var support: RecognitionSupport? = null
        main.post {
            val recognizer = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
            if (recognizer == null) {
                latch.countDown()
                return@post
            }
            runCatching {
                recognizer.checkRecognitionSupport(intent(), context.mainExecutor, object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        support = recognitionSupport
                        recognizer.destroy()
                        latch.countDown()
                    }

                    override fun onError(error: Int) {
                        recognizer.destroy()
                        latch.countDown()
                    }
                })
            }.onFailure {
                recognizer.destroy()
                latch.countDown()
            }
        }
        if (!latch.await(2, TimeUnit.SECONDS)) return null
        val result = support ?: return null
        val tag = Locale.getDefault().toLanguageTag()
        val lang = Locale.getDefault().language
        fun has(list: List<String>) = list.any { it.equals(tag, true) || it.substringBefore('-').equals(lang, true) }
        if (has(result.installedOnDeviceLanguages)) return null
        if (has(result.pendingOnDeviceLanguages)) {
            return SpeechProblem("The on-device speech model is still downloading. Try again in a minute.", SpeechProblem.Kind.DOWNLOADING)
        }
        if (has(result.supportedOnDeviceLanguages)) {
            main.post {
                runCatching {
                    val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    recognizer.triggerModelDownload(intent())
                    main.postDelayed({ recognizer.destroy() }, 2_000)
                }
            }
            return SpeechProblem("Pony is downloading on-device speech for your language. Try again in a minute.", SpeechProblem.Kind.DOWNLOADING)
        }
        return null
    }

    private fun create(context: Context): SpeechRecognizer? {
        return try {
            when {
                Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                SpeechRecognizer.isRecognitionAvailable(context) -> {
                    val other = otherRecognizer(context)
                    if (other != null) SpeechRecognizer.createSpeechRecognizer(context, other) else SpeechRecognizer.createSpeechRecognizer(context)
                }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun otherRecognizer(context: Context): ComponentName? {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val services = context.packageManager.queryIntentServices(intent, 0)
        val match = services.firstOrNull { it.serviceInfo?.packageName != context.packageName } ?: return null
        return ComponentName(match.serviceInfo.packageName, match.serviceInfo.name)
    }

    private fun nothingHeard() = SpeechProblem("Pony didn't hear anything. Tap the mic and speak after the tone.", SpeechProblem.Kind.NOTHING_HEARD)

    private fun problem(code: Int): SpeechProblem = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> nothingHeard()
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            SpeechProblem("Pony needs the microphone to hear you.", SpeechProblem.Kind.NO_MIC_PERMISSION)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            SpeechProblem("Another app is using speech right now. Try again in a moment.", SpeechProblem.Kind.BUSY)
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            SpeechProblem("On-device speech isn't ready, and Pony won't send your voice to a server.", SpeechProblem.Kind.UNAVAILABLE)
        12, 13 -> SpeechProblem("On-device speech doesn't support this language yet.", SpeechProblem.Kind.UNAVAILABLE)
        else -> SpeechProblem("Speech stopped unexpectedly (code $code). Try again.", SpeechProblem.Kind.OTHER)
    }
}
