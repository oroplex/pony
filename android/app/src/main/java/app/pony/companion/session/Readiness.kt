package app.pony.companion.session

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.pony.companion.a11y.AccessibilityStatus
import app.pony.companion.input.ImeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ReadinessState(
    val accessibility: Boolean = false,
    val notifications: Boolean = false,
    val microphone: Boolean = false,
    val battery: Boolean = false,
    val keyboard: Boolean = false,
    val assistant: Boolean = false,
    val camera: Boolean = false,
    val fullScreenNeeded: Boolean = false,
    val installUpdates: Boolean = false,
) {
    /** Pony control is the only switch Pony cannot work without. */
    val ready: Boolean get() = accessibility

    val recommendedDone: Int get() = listOf(accessibility, notifications, battery).count { it }
}

/**
 * Setup state that follows the phone as it changes: the accessibility
 * switch, permissions, battery exemption, and the keyboard. Home and the
 * checklist observe this, so nothing waits for the owner to reopen Pony.
 */
object Readiness {
    private val _state = MutableStateFlow(ReadinessState())
    val state: StateFlow<ReadinessState> = _state.asStateFlow()
    private var watching = false
    private val main = Handler(Looper.getMainLooper())

    fun watch(context: Context) {
        val app = context.applicationContext
        refresh(app)
        if (watching) return
        watching = true
        val manager = app.getSystemService(AccessibilityManager::class.java)
        manager?.addAccessibilityStateChangeListener { refresh(app) }
        if (Build.VERSION.SDK_INT >= 33) {
            manager?.addAccessibilityServicesStateChangeListener(app.mainExecutor) { refresh(app) }
        }
        val observer = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                refresh(app)
            }
        }
        runCatching {
            app.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                observer,
            )
            app.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),
                false,
                observer,
            )
        }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        fun granted(permission: String) = ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
        val next = ReadinessState(
            accessibility = AccessibilityStatus.enabled(app) || SessionRepository.snapshot().accessibilityOn,
            notifications = NotificationManagerCompat.from(app).areNotificationsEnabled(),
            microphone = granted(Manifest.permission.RECORD_AUDIO),
            battery = app.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(app.packageName) == true,
            keyboard = runCatching { ImeStatus.enabled(app) }.getOrDefault(false),
            assistant = runCatching { app.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true }.getOrDefault(false),
            camera = granted(Manifest.permission.CAMERA),
            fullScreenNeeded = Build.VERSION.SDK_INT >= 34 &&
                app.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == false,
            installUpdates = runCatching { app.packageManager.canRequestPackageInstalls() }.getOrDefault(false),
        )
        _state.value = next
    }

    fun refreshAccessibility(on: Boolean) {
        _state.update { it.copy(accessibility = on) }
    }
}
