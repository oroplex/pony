package app.pony.companion.memory

/** Turns remembered entries into a short preamble every on-phone brain sees, so it uses the owner's prefs without asking again. */
object MemorySummary {
    const val MAX_CHARS = 600
    const val LEAD = "What you remember about the owner (use it; don't ask again): "

    fun of(entries: List<MemoryEntry>): String {
        if (entries.isEmpty()) return ""
        val body = entries.sortedBy { it.key }
            .joinToString("; ") { "${it.key.replace('_', ' ')} is ${it.value}" }
        val clipped = if (body.length <= MAX_CHARS) body else body.take(MAX_CHARS - 1).trimEnd() + "…"
        return LEAD + clipped + "."
    }
}
