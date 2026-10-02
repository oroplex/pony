package app.pony.companion.intent

/**
 * Resolves a friendly settings-screen name — or a guarded raw `android.settings.*`
 * action — to the action and data a Settings intent needs. Pure and JVM-testable:
 * it never touches an Android `Intent` or `Context`, so the allowlist is unit
 * tested directly. `open_settings` jumps straight to a screen (the keyboard list,
 * the language picker, an app's details) instead of tapping through menus that
 * swallow touches on protected screens.
 */
object SettingsIntents {
    /** A resolved settings screen: an Intent action and an optional `data` uri. */
    data class Target(val action: String, val data: String? = null)

    const val APP_DETAILS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    private const val PREFIX = "android.settings."

    /** Friendly name → settings action. Only these screens open by name. */
    private val named: Map<String, String> = linkedMapOf(
        "input_method" to "android.settings.INPUT_METHOD_SETTINGS",
        "keyboard" to "android.settings.INPUT_METHOD_SETTINGS",
        "keyboards" to "android.settings.INPUT_METHOD_SETTINGS",
        "languages" to "android.settings.LOCALE_SETTINGS",
        "language" to "android.settings.LOCALE_SETTINGS",
        "locale" to "android.settings.LOCALE_SETTINGS",
        "voice_input" to "android.settings.VOICE_INPUT_SETTINGS",
        "voice_typing" to "android.settings.VOICE_INPUT_SETTINGS",
        "accessibility" to "android.settings.ACCESSIBILITY_SETTINGS",
        "sound" to "android.settings.SOUND_SETTINGS",
        "display" to "android.settings.DISPLAY_SETTINGS",
        "battery" to "android.settings.BATTERY_SAVER_SETTINGS",
        "date" to "android.settings.DATE_SETTINGS",
        "time" to "android.settings.DATE_SETTINGS",
        "security" to "android.settings.SECURITY_SETTINGS",
        "wifi" to "android.settings.WIFI_SETTINGS",
        "bluetooth" to "android.settings.BLUETOOTH_SETTINGS",
        "location" to "android.settings.LOCATION_SOURCE_SETTINGS",
        "settings" to "android.settings.SETTINGS",
        "home" to "android.settings.SETTINGS",
    )

    /** Names that open an app's own details page; they need a package. */
    private val appDetailNames = setOf("app_details", "app", "app_info", "application_details")

    /** The friendly names Pony knows, for tool descriptions and tests. */
    val names: List<String> get() = (named.keys + appDetailNames).toList()

    /**
     * Resolve [name] — a friendly name or a raw `android.settings.*` action. An
     * app-details name needs [pkg]. Returns null when the name is unknown, when an
     * app-details screen is asked for without a package, or when a raw action is
     * not an `android.settings.*` one, so the phone refuses it instead of firing an
     * arbitrary intent.
     */
    fun resolve(name: String, pkg: String? = null): Target? {
        val raw = name.trim()
        if (raw.isEmpty()) return null
        val key = raw.lowercase().replace(' ', '_').replace('-', '_')
        if (key in appDetailNames || raw == APP_DETAILS) {
            return pkg?.takeIf { it.isNotBlank() }?.let { appDetails(it) }
        }
        named[key]?.let { return Target(it) }
        return rawAction(raw)
    }

    /** The app-details screen for [pkg], as the action plus a `package:` data uri. */
    fun appDetails(pkg: String): Target = Target(APP_DETAILS, "package:$pkg")

    /** A raw action is allowed only when it is a real `android.settings.*` screen. */
    private fun rawAction(action: String): Target? {
        if (!action.startsWith(PREFIX)) return null
        val tail = action.removePrefix(PREFIX)
        if (tail.isEmpty() || !tail.all { it == '_' || it.isLetterOrDigit() }) return null
        return Target(action)
    }
}
