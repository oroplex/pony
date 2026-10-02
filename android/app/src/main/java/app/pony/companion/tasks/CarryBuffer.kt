package app.pony.companion.tasks

import app.pony.companion.memory.MemoryGuard

/**
 * A small scratchpad the agent fills in one app and reads in another — copy a
 * confirmation number, an address, or a price here, open the next app, and use
 * it there. It's working memory for the task in front of Pony, so it lives in
 * the process, not on disk: nothing here is a saved fact.
 *
 * Bounded so a loop of copies can't grow without end, and it refuses to hold a
 * secret, reusing [MemoryGuard] — a password or one-time code must never sit in
 * a buffer that the next screen might read back.
 *
 * Pure (the only impurity is an injectable [now]) so stash/recall/eviction are
 * unit-tested on the JVM.
 */
class CarryBuffer(
    private val maxEntries: Int = 12,
    private val maxLength: Int = 2_000,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    data class Note(val label: String, val text: String, val at: Long)

    data class Result(val ok: Boolean, val note: Note? = null, val reason: String? = null)

    // Insertion-ordered: the first key is always the oldest, which is what we evict.
    private val notes = LinkedHashMap<String, Note>()

    @Synchronized
    fun stash(label: String, text: String): Result {
        val key = normalize(label)
        val value = text.trim()
        if (key.isEmpty()) return Result(false, reason = "give it a short label")
        if (value.isEmpty()) return Result(false, reason = "nothing to copy")
        val guard = MemoryGuard.check(key, value)
        if (!guard.allowed) return Result(false, reason = guard.reason ?: "that looks private")
        val note = Note(key, value.take(maxLength), now())
        // Re-stashing a label refreshes it to newest; drop the old slot first so
        // insertion order (and therefore eviction order) stays honest.
        notes.remove(key)
        notes[key] = note
        while (notes.size > maxEntries) {
            notes.remove(notes.keys.first())
        }
        return Result(true, note = note)
    }

    @Synchronized
    fun recall(label: String): String? = notes[normalize(label)]?.text

    @Synchronized
    fun labels(): List<String> = notes.values.sortedBy { it.at }.map { it.label }

    @Synchronized
    fun all(): List<Note> = notes.values.sortedBy { it.at }

    @Synchronized
    fun clear() = notes.clear()

    private fun normalize(label: String): String = label.trim().lowercase()

    companion object {
        /** The buffer the running task carries from one app to the next. */
        val shared = CarryBuffer()
    }
}
