package app.pony.companion.display

import android.content.Context
import android.media.AudioManager
import android.telecom.TelecomManager

/**
 * Knows whether a call is on the phone and whether its screen is in front.
 * It never pauses a task: callers use it only to keep Pony's hands off the
 * call screen. Phone-state permissions are not required; audio mode, call
 * notifications, and the foreground window cover carrier and VoIP calls.
 */
object CallGuard {
    @Volatile private var noticePackage: String? = null
    @Volatile private var noticeAt: Long = 0L
    @Volatile var foregroundPackage: String? = null
        private set
    @Volatile var foregroundClass: String? = null
        private set

    /** An ongoing call notification (CATEGORY_CALL or CallStyle) was posted by [packageName]. */
    fun noteCallNotification(packageName: String, now: Long = System.currentTimeMillis()) {
        noticePackage = packageName
        noticeAt = now
    }

    fun noteForeground(packageName: String?, className: String?) {
        if (packageName.isNullOrBlank()) return
        foregroundPackage = packageName
        foregroundClass = className
    }

    fun status(context: Context): CallPolicy.Status {
        val audio = context.getSystemService(AudioManager::class.java)
        val mode = audio?.mode ?: AudioManager.MODE_NORMAL
        if (mode == AudioManager.MODE_NORMAL) {
            noticePackage = null
        }
        return CallPolicy.status(
            CallPolicy.Signals(
                audioMode = mode,
                telecomInCall = telecomInCall(context),
                callNoticePackage = noticePackage,
                callNoticeAt = noticeAt,
                foregroundPackage = foregroundPackage,
                foregroundClass = foregroundClass,
                now = System.currentTimeMillis(),
            ),
        )
    }

    fun inCall(context: Context): Boolean = status(context).active

    fun callScreenInFront(context: Context): Boolean =
        CallPolicy.callScreenInFront(status(context), foregroundPackage, foregroundClass, defaultDialer(context))

    fun protectedPackages(context: Context): Set<String> = CallPolicy.protectedPackages(status(context), defaultDialer(context))

    fun defaultDialer(context: Context): String? = runCatching {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    }.getOrNull()

    private fun telecomInCall(context: Context): Boolean? = try {
        context.getSystemService(TelecomManager::class.java)?.isInCall
    } catch (_: SecurityException) {
        null
    } catch (_: Throwable) {
        null
    }
}
