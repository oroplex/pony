package app.pony.companion.routines

import app.pony.companion.tasks.TaskRecord

/**
 * The pure brains of saved routines: turning what the owner said into either a
 * "save this as a routine" request or the name of a routine to replay, with no
 * Android or disk in sight so it can be unit-tested.
 *
 * Matching is deliberately strict. Replaying needs the owner to say a routine's
 * name (or "run <name>"), and saving needs them to point at the last task or say
 * the word "routine" — so an ordinary ask like "save this photo as cover.jpg" is
 * never mistaken for a routine and slips straight through to the brain.
 */
object Routines {
    /** Source tag for a replayed routine, so replaying never re-matches itself. */
    const val SOURCE = "routine"

    /** Source tag for the little "Saved …" card a save puts up. */
    const val SAVE_SOURCE = "routine_save"

    const val MAX_NAME = 60

    private val REPLAY_VERBS = listOf("run", "start", "play", "do", "replay", "begin")

    private val SKIP_SOURCES = setOf(SOURCE, SAVE_SOURCE)

    /** How the owner opens a save, before the "as …". Exact, so a longer ask can't match. */
    private val SAVE_HEADS = setOf(
        "save this", "save that", "save it", "save this one", "save that one",
        "save this task", "save that task", "save the last task", "save the last one",
        "save the last thing", "save what you did", "save what you just did",
        "remember this", "remember that", "remember it", "remember this task",
        "remember that task", "remember the last task", "remember the last one",
        "make this", "make that",
    )

    private val ROUTINE_FILLERS = listOf(
        "a routine called", "a routine named", "the routine called", "the routine named",
        "a routine", "the routine", "routine called", "routine named", "routine",
    )

    /** Lowercased, punctuation dropped, whitespace collapsed — how names and asks are compared. */
    fun normalize(text: String): String =
        buildString {
            for (ch in text.lowercase()) append(if (ch.isLetterOrDigit() || ch == ' ') ch else ' ')
        }.trim().replace(Regex("\\s+"), " ")

    /**
     * The routine name the owner wants to replay, matched against [names], or
     * null. Saying the name exactly wins; otherwise "run <name>" (or start /
     * play / do / replay / begin, with an optional "my"). The longest matching
     * name is chosen so a routine called "lunch" never shadows "my usual lunch".
     */
    fun pick(utterance: String, names: List<String>): String? {
        val said = normalize(utterance)
        if (said.isEmpty()) return null
        val byLength = names.filter { normalize(it).isNotEmpty() }.sortedByDescending { normalize(it).length }
        byLength.firstOrNull { normalize(it) == said }?.let { return it }
        return byLength.firstOrNull { name ->
            val n = normalize(name)
            REPLAY_VERBS.any { verb -> said == "$verb $n" || said == "$verb my $n" }
        }
    }

    /**
     * The name in a "save this as …" request, or null when the owner wasn't
     * asking to save a routine. The clause before "as" has to be a bare pointer
     * at the last task (or the phrase has to say "routine"), and a pointer-only
     * save won't take a file-looking name, so a real "save X as Y.pdf" is left
     * for the brain.
     */
    fun parseSave(utterance: String): String? {
        val trimmed = utterance.trim()
        val lower = trimmed.lowercase()
        val asAt = lower.indexOf(" as ")
        if (asAt < 0) return null
        val head = normalize(lower.substring(0, asAt))
        val mentionsRoutine = lower.contains("routine")
        val pointerHead = head in SAVE_HEADS
        val routineHead = mentionsRoutine &&
            (head.startsWith("save") || head.startsWith("remember") || head.startsWith("make"))
        if (!pointerHead && !routineHead) return null
        val name = cleanName(trimmed.substring(asAt + 4)) ?: return null
        if (!mentionsRoutine && (name.contains('.') || name.contains('/'))) return null
        return name
    }

    /**
     * The most recent real task worth saving: not an implicit assistant card,
     * and not a save or replay command itself, so "save that as X" saves the
     * task before it, never the save.
     */
    fun lastSavable(tasks: List<TaskRecord>): TaskRecord? =
        tasks.sortedByDescending { it.createdAt }
            .firstOrNull { !it.implicit && it.text.isNotBlank() && it.source !in SKIP_SOURCES }

    private fun cleanName(raw: String): String? {
        var name = raw.trim().trim('"', '\'', '“', '”').trim()
        for (filler in ROUTINE_FILLERS) {
            val low = name.lowercase()
            if (low == filler) {
                name = ""
                break
            }
            if (low.startsWith("$filler ")) {
                name = name.substring(filler.length + 1).trim()
                break
            }
        }
        name = name.trim().trimEnd('.', '!', '?', ',').trim().trim('"', '\'', '“', '”').trim()
        return name.takeIf { it.isNotEmpty() && it.length <= MAX_NAME }
    }
}
