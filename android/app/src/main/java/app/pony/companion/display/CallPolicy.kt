package app.pony.companion.display

/**
 * Calls never pause Pony. The only rule is that Pony never touches the call
 * screen: while it is in front on the main display, taps and typing there
 * wait for it to leave. Everything else keeps running.
 */
object CallPolicy {
    const val MODE_NORMAL = 0
    const val MODE_RINGTONE = 1
    const val MODE_IN_CALL = 2
    const val MODE_IN_COMMUNICATION = 3
    const val MODE_CALL_SCREENING = 4
    const val MODE_CALL_REDIRECT = 5
    const val MODE_COMMUNICATION_REDIRECT = 6

    /** How long a call notification counts as evidence of a VoIP call. */
    const val CALL_NOTICE_WINDOW_MS = 4 * 60 * 60 * 1000L

    val CALL_UI_PACKAGES = setOf(
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.android.server.telecom",
        "com.android.phone",
    )

    val DIALER_PACKAGES = setOf(
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
    )

    private val CALL_ACTIVITY = Regex("(voip|incall|in_call|callactivity|callscreen|ongoingcall|videocall|voicecall)", RegexOption.IGNORE_CASE)

    enum class Kind { NONE, RINGING, CARRIER, VOIP }

    data class Signals(
        val audioMode: Int,
        val telecomInCall: Boolean? = null,
        val callNoticePackage: String? = null,
        val callNoticeAt: Long = 0L,
        val foregroundPackage: String? = null,
        val foregroundClass: String? = null,
        val now: Long = 0L,
    )

    data class Status(val kind: Kind, val app: String? = null) {
        val active: Boolean get() = kind != Kind.NONE
    }

    fun status(signals: Signals): Status {
        val notice = signals.callNoticePackage?.takeIf { signals.now - signals.callNoticeAt < CALL_NOTICE_WINDOW_MS }
        return when {
            signals.audioMode == MODE_RINGTONE || signals.audioMode == MODE_CALL_SCREENING -> Status(Kind.RINGING, notice)
            signals.audioMode == MODE_IN_CALL || signals.audioMode == MODE_CALL_REDIRECT -> Status(Kind.CARRIER, notice)
            signals.audioMode == MODE_IN_COMMUNICATION || signals.audioMode == MODE_COMMUNICATION_REDIRECT -> {
                val callScreen = looksLikeCallScreen(signals.foregroundPackage, signals.foregroundClass)
                when {
                    notice != null -> Status(Kind.VOIP, notice)
                    signals.telecomInCall == true -> Status(Kind.VOIP, signals.foregroundPackage)
                    callScreen -> Status(Kind.VOIP, signals.foregroundPackage)
                    else -> Status(Kind.NONE)
                }
            }
            signals.telecomInCall == true -> Status(Kind.CARRIER, notice)
            else -> Status(Kind.NONE)
        }
    }

    /** Packages whose windows Pony must not touch right now. */
    fun protectedPackages(status: Status, defaultDialer: String?): Set<String> {
        val out = CALL_UI_PACKAGES.toMutableSet()
        if (status.active) {
            out += DIALER_PACKAGES
            defaultDialer?.let { out += it }
            status.app?.let { out += it }
        }
        return out
    }

    /**
     * True when the call screen is the window in front of the owner. A call app's
     * other screens (a WhatsApp chat during a WhatsApp call) are not the call screen.
     */
    fun callScreenInFront(status: Status, foregroundPackage: String?, foregroundClass: String?, defaultDialer: String?): Boolean {
        val pkg = foregroundPackage ?: return false
        if (pkg in CALL_UI_PACKAGES) return true
        if (!status.active) return false
        if (pkg in DIALER_PACKAGES || pkg == defaultDialer) return true
        return pkg == status.app && looksLikeCallScreen(pkg, foregroundClass)
    }

    fun looksLikeCallScreen(pkg: String?, activityClass: String?): Boolean {
        if (pkg != null && pkg in CALL_UI_PACKAGES) return true
        val cls = activityClass ?: return false
        return CALL_ACTIVITY.containsMatchIn(cls.substringAfterLast('.'))
    }
}
