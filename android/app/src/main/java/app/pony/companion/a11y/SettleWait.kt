package app.pony.companion.a11y

import kotlin.math.abs

/**
 * Decides when the screen has stopped changing, so Pony can wait for a page to
 * load or an animation to finish instead of sleeping a fixed guess. The call is
 * settled once the accessibility stream has been quiet for a moment *and* two
 * screenshots look the same; it keeps waiting while either is still moving, and
 * gives up at the timeout. Everything here is plain arithmetic so it's unit
 * tested on the JVM — the service feeds it a11y-quiet time and a cheap
 * down-sampled luma diff between frames.
 */
object SettleWait {
    enum class Verdict { SETTLED, WAIT, TIMED_OUT }

    /**
     * One look at the screen: how long since the wait began, how long the
     * accessibility stream has been silent, and how much the frame changed since
     * the last one (0 = identical, 1 = every cell moved).
     */
    data class Sample(val elapsedMs: Long, val quietForMs: Long, val diffRatio: Double)

    /** A frame this still counts as unchanged (2% of cells moved). */
    const val DIFF_SETTLED = 0.02

    /** The accessibility stream must be silent at least this long to call it quiet. */
    const val QUIET_MS = 350L

    /** Never declare settled before this, so a slow first paint isn't missed. */
    const val MIN_WAIT_MS = 150L

    const val DEFAULT_TIMEOUT_MS = 4_000L

    fun decide(sample: Sample, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Verdict {
        val still = sample.quietForMs >= QUIET_MS && sample.diffRatio <= DIFF_SETTLED
        if (still && sample.elapsedMs >= MIN_WAIT_MS) return Verdict.SETTLED
        if (sample.elapsedMs >= timeoutMs) return Verdict.TIMED_OUT
        return Verdict.WAIT
    }

    /** Replays a run of samples and returns the first verdict that isn't "keep waiting". */
    fun settle(samples: List<Sample>, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Verdict {
        for (sample in samples) {
            val verdict = decide(sample, timeoutMs)
            if (verdict != Verdict.WAIT) return verdict
        }
        return Verdict.WAIT
    }

    /**
     * Fraction of down-sampled luma cells that moved by more than [tolerance]
     * (0..255). Frames that don't line up (different sizes, or empty) count as a
     * full change so a bad capture never looks settled.
     */
    fun diffRatio(a: IntArray, b: IntArray, tolerance: Int = 12): Double {
        if (a.isEmpty() || a.size != b.size) return 1.0
        var changed = 0
        for (i in a.indices) {
            if (abs(a[i] - b[i]) > tolerance) changed++
        }
        return changed.toDouble() / a.size
    }
}
