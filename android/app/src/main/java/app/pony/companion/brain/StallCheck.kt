package app.pony.companion.brain

/**
 * Spots when an action left the screen essentially unchanged, so the loop can
 * nudge the model off a dead end instead of letting it tap the same row over
 * and over. Comparison is on the UI tree's lines, which is cheap and steady
 * even when a screenshot differs by a cursor blink.
 */
object StallCheck {
    const val HINT =
        "That didn't change the screen. Don't repeat the same tap — press back and use the " +
            "Settings search box (type something like \"keyboard\", \"language\", or \"voice input\")."

    /** True when [after] is at least 95% the same as [before] by line overlap. */
    fun stalled(before: String?, after: String): Boolean {
        if (before.isNullOrBlank()) return false
        val a = lines(before)
        val b = lines(after)
        if (a.isEmpty() && b.isEmpty()) return true
        val union = (a + b).size.coerceAtLeast(1)
        val overlap = a.count { it in b }
        return overlap.toDouble() / union >= 0.95
    }

    private fun lines(tree: String): Set<String> =
        tree.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
