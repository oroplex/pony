package app.pony.companion.a11y

import kotlin.math.abs

/**
 * Builds the stroke geometry for Pony's gestures as plain data — points and
 * durations only — so the paths are unit tested on the JVM without an Android
 * `GestureDescription`. The accessibility service turns these specs into real
 * strokes. A long press is one slow stroke that stays put; a drag presses, moves,
 * and releases as one stroke; a pinch is two strokes whose endpoints converge
 * (zoom out) or spread apart (zoom in).
 */
object GestureBuilder {
    data class Point(val x: Float, val y: Float)

    /** One stroke: its path of points, when it begins, and how long it runs. */
    data class Stroke(val points: List<Point>, val startAt: Long, val durationMs: Long)

    const val MIN_MS = 1L
    const val MAX_MS = 20_000L
    const val LONG_PRESS_MS = 600L
    const val DRAG_MS = 600L
    const val PINCH_MS = 300L

    /** A press that holds at one point for [durationMs] (a long press by default). */
    fun longPress(x: Float, y: Float, durationMs: Long = LONG_PRESS_MS): List<Stroke> {
        return listOf(Stroke(listOf(Point(x, y)), 0, clamp(durationMs)))
    }

    /**
     * Press at the start, move to the end, and release as one continuous stroke —
     * a drag-and-drop to reorder or move an item, not a quick flick.
     */
    fun drag(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = DRAG_MS): List<Stroke> {
        return listOf(Stroke(listOf(Point(x1, y1), Point(x2, y2)), 0, clamp(durationMs)))
    }

    /**
     * Two fingers above and below ([x], [y]) moving along the vertical axis: from
     * [fromDistance] apart to [toDistance] apart. Coming together pinches to zoom
     * out; spreading apart zooms in. Distances are pixels between the two fingers.
     */
    fun pinch(
        x: Float,
        y: Float,
        fromDistance: Float,
        toDistance: Float,
        durationMs: Long = PINCH_MS,
    ): List<Stroke> {
        val from = abs(fromDistance) / 2f
        val to = abs(toDistance) / 2f
        val dur = clamp(durationMs)
        val top = Stroke(listOf(Point(x, y - from), Point(x, y - to)), 0, dur)
        val bottom = Stroke(listOf(Point(x, y + from), Point(x, y + to)), 0, dur)
        return listOf(top, bottom)
    }

    /** The longest point in time any stroke in [strokes] reaches, for sizing a wait. */
    fun spanMs(strokes: List<Stroke>): Long = strokes.maxOfOrNull { it.startAt + it.durationMs } ?: 0L

    private fun clamp(ms: Long): Long = ms.coerceIn(MIN_MS, MAX_MS)
}
