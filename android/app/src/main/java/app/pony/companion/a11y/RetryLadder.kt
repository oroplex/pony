package app.pony.companion.a11y

/**
 * How Pony escalates a tap that doesn't land. A normal tap is a gesture at the
 * coordinates the model chose. When that changes nothing on the screen and sat on
 * nothing clickable, Pony retries through these strategies in order — first asking
 * the control under the point to click itself, then a fresh gesture, then
 * scrolling the target into view and clicking it, and finally searching for the
 * control by its label — before it reports a miss.
 */
enum class TapStrategy(val wire: String) {
    A11yClick("a11y_click"),
    Gesture("gesture"),
    ScrollIntoView("scroll"),
    SearchPath("search"),
}

object RetryLadder {
    /** At most this many retry strategies after the first gesture, so a stuck screen can't loop. */
    const val MAX_RETRIES = 4

    /** The retry order. The search path needs a label to match, so it's dropped without one. */
    fun plan(hasLabel: Boolean): List<TapStrategy> =
        listOf(TapStrategy.A11yClick, TapStrategy.Gesture, TapStrategy.ScrollIntoView, TapStrategy.SearchPath)
            .filter { it != TapStrategy.SearchPath || hasLabel }
            .take(MAX_RETRIES)

    /**
     * Whether the first gesture already took effect, so Pony must not retry. True
     * when the screen changed, or the point sat on a real control the gesture would
     * have driven — retrying there risks undoing a toggle it just flipped.
     */
    fun landedFirstTap(screenChanged: Boolean, onClickable: Boolean): Boolean = screenChanged || onClickable
}
