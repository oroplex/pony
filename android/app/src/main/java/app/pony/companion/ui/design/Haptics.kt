package app.pony.companion.ui.design

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

enum class Haptic { NONE, TAP, SELECT, TOGGLE_ON, TOGGLE_OFF, CONFIRM, REJECT, SUCCESS, LISTEN, STOP, TICK, LONG }

/**
 * Pony's haptic vocabulary. Everyday feedback goes through the view so it
 * follows the owner's haptic setting; signature moments (success, listening,
 * stop) use short vibration compositions where the phone supports them.
 */
class Haptics(private val view: View, private val context: Context) {
    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
        }.getOrNull()
    }

    fun perform(kind: Haptic) {
        when (kind) {
            Haptic.NONE -> Unit
            Haptic.TAP -> view(HapticFeedbackConstants.KEYBOARD_TAP)
            Haptic.TICK -> view(HapticFeedbackConstants.CLOCK_TICK)
            Haptic.SELECT -> view(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
            Haptic.TOGGLE_ON -> view(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CONTEXT_CLICK)
            Haptic.TOGGLE_OFF -> view(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CONTEXT_CLICK)
            Haptic.CONFIRM -> view(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
            Haptic.REJECT -> view(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
            Haptic.LONG -> view(HapticFeedbackConstants.LONG_PRESS)
            Haptic.SUCCESS -> compose(
                listOf(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE to 0.55f, VibrationEffect.Composition.PRIMITIVE_CLICK to 0.9f),
                fallback = Haptic.CONFIRM,
            )
            Haptic.LISTEN -> compose(listOf(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE to 0.45f), fallback = Haptic.TICK)
            Haptic.STOP -> compose(listOf(VibrationEffect.Composition.PRIMITIVE_THUD to 0.8f), fallback = Haptic.LONG)
        }
    }

    private fun view(constant: Int) {
        view.performHapticFeedback(constant)
    }

    private fun compose(primitives: List<Pair<Int, Float>>, fallback: Haptic) {
        val device = vibrator
        val enabled = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1
        }.getOrDefault(true)
        if (!enabled) return
        if (Build.VERSION.SDK_INT < 30 || device == null || !device.hasVibrator() ||
            !device.areAllPrimitivesSupported(*primitives.map { it.first }.toIntArray())
        ) {
            perform(fallback)
            return
        }
        runCatching {
            val composition = VibrationEffect.startComposition()
            primitives.forEach { (primitive, scale) -> composition.addPrimitive(primitive, scale) }
            device.vibrate(composition.compose())
        }.onFailure { perform(fallback) }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view, context) { Haptics(view, context) }
}
