package app.pony.companion.voice

import android.content.Context
import app.pony.companion.brain.GrokBot
import app.pony.companion.session.SessionLength

object VoicePrefs {
    private const val PREFS = "pony"
    private const val VOICE = "voice_enabled"
    private const val WAKE = "wake_word"
    private const val CHARGE = "wake_charging_only"
    private const val MODE = "brain_mode"
    private const val RATE = "tts_rate"
    private const val VOICE_NAME = "tts_voice"
    private const val CLOUD_VOICE = "cloud_voice"
    private const val STATUS = "wake_status"
    private const val BACKGROUND = "background_mode"
    private const val SHIZUKU = "shizuku_enhanced"
    private const val CLIENT = "preferred_client"
    private const val CONNECTION = "connection_mode"
    private const val PRIVATE_RELAY = "private_relay"
    private const val KNOWN_RELAYS = "known_relays"
    private const val THEME = "theme_mode"
    private const val SESSION_LENGTH = "session_length"
    private const val AUTO_RECONNECT = "auto_reconnect"
    private const val MAIN_SCREEN = "main_screen_fallback"
    private const val KEEP_SHOTS = "history_screenshots"
    private const val TRIED = "tried_basics"
    private const val SPOKEN_REPLIES = "spoken_replies"

    const val CONNECTED = "connected"
    const val BUILTIN = "builtin"

    const val FALLBACK_ASK = "ask"
    const val FALLBACK_ALLOW = "allow"
    const val FALLBACK_NEVER = "never"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The assistant gesture and typed tasks. Hey Pony is separate and starts off. */
    fun voiceEnabled(context: Context): Boolean = prefs(context).getBoolean(VOICE, true)

    fun setVoiceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(VOICE, enabled).apply()
    }

    fun wakeWord(context: Context): Boolean = prefs(context).getBoolean(WAKE, false)

    fun setWakeWord(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(WAKE, enabled).apply()
    }

    /** Default on. "Charging" includes a full battery that is still plugged in. */
    fun chargingOnly(context: Context): Boolean = prefs(context).getBoolean(CHARGE, true)

    fun setChargingOnly(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(CHARGE, enabled).apply()
    }

    fun brainMode(context: Context): String = prefs(context).getString(MODE, CONNECTED) ?: CONNECTED

    fun setBrainMode(context: Context, mode: String) {
        prefs(context).edit().putString(MODE, mode).apply()
    }

    fun speechRate(context: Context): Float = prefs(context).getFloat(RATE, 1f)

    fun setSpeechRate(context: Context, rate: Float) {
        prefs(context).edit().putFloat(RATE, rate.coerceIn(0.7f, 1.4f)).apply()
    }

    fun voiceName(context: Context): String = prefs(context).getString(VOICE_NAME, "") ?: ""

    fun setVoiceName(context: Context, name: String) {
        prefs(context).edit().putString(VOICE_NAME, name).apply()
    }

    /** Off by default: Pony only offers on-device voices. On allows the higher-quality network voices into the list. */
    fun cloudVoice(context: Context): Boolean = prefs(context).getBoolean(CLOUD_VOICE, false)

    fun setCloudVoice(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(CLOUD_VOICE, enabled).apply()
    }

    /** Pony reads results aloud. Off by default for typed asks; voice asks always answer out loud. */
    fun spokenReplies(context: Context): Boolean = prefs(context).getBoolean(SPOKEN_REPLIES, false)

    fun setSpokenReplies(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(SPOKEN_REPLIES, enabled).apply()
    }

    fun status(context: Context): String = prefs(context).getString(STATUS, "") ?: ""

    fun setStatus(context: Context, text: String) {
        prefs(context).edit().putString(STATUS, text).apply()
    }

    /** On by default. Pony tries to keep actions off the owner's screen. */
    fun backgroundMode(context: Context): Boolean = prefs(context).getBoolean(BACKGROUND, true)

    fun setBackgroundMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(BACKGROUND, enabled).apply()
    }

    /** Off until the owner grants Shizuku. That is the real second display. */
    fun shizukuEnhanced(context: Context): Boolean = prefs(context).getBoolean(SHIZUKU, false)

    fun setShizukuEnhanced(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(SHIZUKU, enabled).apply()
    }

    fun preferredClient(context: Context): String =
        prefs(context).getString(CLIENT, GrokBot.NAME) ?: GrokBot.NAME

    fun setPreferredClient(context: Context, name: String) {
        prefs(context).edit().putString(CLIENT, name).apply()
    }

    fun connectionCloud(context: Context): Boolean =
        prefs(context).getString(CONNECTION, "cloud") != "private"

    fun setConnectionCloud(context: Context, cloud: Boolean) {
        prefs(context).edit().putString(CONNECTION, if (cloud) "cloud" else "private").apply()
    }

    fun privateRelay(context: Context): String = prefs(context).getString(PRIVATE_RELAY, "") ?: ""

    fun setPrivateRelay(context: Context, url: String) {
        prefs(context).edit().putString(PRIVATE_RELAY, url).apply()
        if (url.isNotBlank()) rememberRelay(context, url)
    }

    /** Every relay the owner has paired through. Pairing links for any of them are accepted. */
    fun knownRelays(context: Context): Set<String> = prefs(context).getStringSet(KNOWN_RELAYS, emptySet()).orEmpty()

    fun rememberRelay(context: Context, url: String) {
        val next = knownRelays(context) + url
        prefs(context).edit().putStringSet(KNOWN_RELAYS, next.toSet()).apply()
    }

    fun themeMode(context: Context): String = prefs(context).getString(THEME, "system") ?: "system"

    fun setThemeMode(context: Context, wire: String) {
        prefs(context).edit().putString(THEME, wire).apply()
    }

    fun sessionLength(context: Context): SessionLength = SessionLength.of(prefs(context).getString(SESSION_LENGTH, null))

    fun setSessionLength(context: Context, length: SessionLength) {
        prefs(context).edit().putString(SESSION_LENGTH, length.wire).apply()
    }

    /** On by default: drops, network switches, and reboots rejoin the same session. */
    fun autoReconnect(context: Context): Boolean = prefs(context).getBoolean(AUTO_RECONNECT, true)

    fun setAutoReconnect(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(AUTO_RECONNECT, enabled).apply()
    }

    /** What to do when an app can't open out of sight: ask the owner, allow, or never use the main screen. */
    fun mainScreenFallback(context: Context): String = prefs(context).getString(MAIN_SCREEN, FALLBACK_ASK) ?: FALLBACK_ASK

    fun setMainScreenFallback(context: Context, value: String) {
        prefs(context).edit().putString(MAIN_SCREEN, value).apply()
    }

    fun keepScreenshots(context: Context): Boolean = prefs(context).getBoolean(KEEP_SHOTS, true)

    fun setKeepScreenshots(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEEP_SHOTS, enabled).apply()
    }

    fun triedBasics(context: Context): Boolean = prefs(context).getBoolean(TRIED, false)

    fun setTriedBasics(context: Context) {
        prefs(context).edit().putBoolean(TRIED, true).apply()
    }
}
