package app.pony.companion.voice

import java.util.Locale

/**
 * Orders the phone's text-to-speech voices for the picker: the owner's language
 * first, then English, then the rest — and within a language the most natural
 * (neural / enhanced) voices ahead of the older robotic ones. Voices that need
 * the network show up only when the owner has turned cloud voices on. Pure, so
 * the ranking and labels are unit-tested on the JVM.
 */
object VoiceCatalog {
    // Android's Voice.getQuality() constants, mirrored as plain ints so this stays testable.
    const val QUALITY_VERY_HIGH = 500
    const val QUALITY_HIGH = 400
    const val QUALITY_NORMAL = 300
    const val QUALITY_LOW = 200

    /** A pure view of one TTS voice — enough to rank, gate, and label it without the Android class. */
    data class Option(
        val name: String,
        val language: String = "",
        val quality: Int = QUALITY_NORMAL,
        val networkRequired: Boolean = false,
    )

    fun ordered(options: List<Option>, language: String, allowCloud: Boolean = true): List<Option> =
        options
            .distinctBy { it.name }
            .filter { allowCloud || !it.networkRequired }
            .sortedWith(
                compareBy<Option> { rank("${it.language} ${it.name}", language) }
                    .thenByDescending { it.quality }
                    .thenBy { it.name.lowercase() },
            )
            .take(12)

    /** Convenience for a bare list of names, where quality and network aren't known. */
    fun ordered(names: List<String>, language: String): List<String> =
        ordered(names.map { Option(it) }, language, allowCloud = true).map { it.name }

    fun rank(name: String, language: String): Int {
        val n = name.lowercase()
        val lang = language.lowercase()
        val local = lang.isNotBlank() && (n.contains(lang) || n.contains(lang.replace('-', '_')))
        val english = n.contains("en-us") || n.contains("en_us") || n.contains("en-") || n.contains("en_")
        return when {
            local && english -> 0
            local -> 1
            english -> 2
            else -> 3
        }
    }

    /** A short quality tier, most natural first. */
    fun tier(quality: Int): String = when {
        quality >= QUALITY_VERY_HIGH -> "Neural"
        quality >= QUALITY_HIGH -> "Enhanced"
        quality >= QUALITY_NORMAL -> "Standard"
        else -> "Basic"
    }

    /** The voice's language, spelled out, e.g. "English (United States)". Falls back to a tidied name. */
    fun place(option: Option): String =
        option.language.takeIf { it.isNotBlank() }
            ?.let { runCatching { Locale.forLanguageTag(it).getDisplayName(Locale.ENGLISH) }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: prettyName(option.name)

    /** A readable one-line label, e.g. "English (United States) · Enhanced". */
    fun label(option: Option): String = "${place(option)} · ${tier(option.quality)}"

    private fun prettyName(name: String): String =
        name.substringBefore("-x-").replace('_', ' ').replace('-', ' ').trim()
            .ifBlank { "Voice" }
            .replaceFirstChar { it.uppercase() }
}
