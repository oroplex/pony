package app.pony.companion.ui

import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pony.companion.a11y.QrDecode
import app.pony.companion.ui.design.Haptic
import app.pony.companion.ui.design.LocalToaster
import app.pony.companion.ui.design.rememberHaptics
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
@Composable
fun QrScanner(onQr: (String) -> Unit, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val haptics = rememberHaptics()
    val toaster = LocalToaster.current
    // Toast "Code read" once per distinct code, so a code that scans but won't
    // parse doesn't buzz every second while it sits in the viewfinder.
    val lastToast = remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener(
                {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    val main = ContextCompat.getMainExecutor(ctx)
                    analysis.setAnalyzer(
                        executor,
                        QrAnalyzer { value ->
                            main.execute {
                                haptics.perform(Haptic.SUCCESS)
                                if (value != lastToast.value) {
                                    lastToast.value = value
                                    toaster.show("Code read")
                                }
                                onQr(value)
                            }
                        },
                    )
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
        onRelease = { view ->
            runCatching { ProcessCameraProvider.getInstance(view.context).get().unbindAll() }
        },
    )
}

private class QrAnalyzer(
    private val onQr: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private var lastText = ""
    private var lastAt = 0L
    private var misses = 0
    private var loggedAt = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val packed = QrDecode.pack(bytes, image.width, image.height, plane.rowStride, plane.pixelStride)
            val text = QrDecode.decode(packed, image.width, image.height, image.imageInfo.rotationDegrees)
            val now = SystemClock.elapsedRealtime()
            if (text != null) {
                if (text != lastText || now - lastAt > 1500) {
                    lastText = text
                    lastAt = now
                    onQr(text)
                }
            } else {
                // No code this frame is normal while aiming; count it and log at
                // most every couple of seconds so a camera that never decodes is
                // visible in logcat without flooding it.
                misses++
                if (now - loggedAt > 2000) {
                    loggedAt = now
                    Log.d(TAG, "no QR in frame (misses=$misses, ${image.width}x${image.height}, rot=${image.imageInfo.rotationDegrees})")
                }
            }
        } catch (err: Exception) {
            val now = SystemClock.elapsedRealtime()
            if (now - loggedAt > 2000) {
                loggedAt = now
                Log.d(TAG, "frame decode failed: ${err.message}")
            }
        } finally {
            image.close()
        }
    }

    private companion object {
        const val TAG = "PonyQr"
    }
}
