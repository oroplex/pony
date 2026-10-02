package app.pony.companion.session

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Exponential reconnect delays with jitter: about 1 s, 2 s, 4 s … capped at [maxMs]. */
class Backoff(
    private val baseMs: Long = 1_000,
    private val maxMs: Long = 30_000,
    private val jitter: Double = 0.2,
    private val random: () -> Double = { Random.nextDouble() },
) {
    var attempts: Int = 0
        private set

    fun next(): Long {
        val raw = min(maxMs.toDouble(), baseMs * 2.0.pow(attempts.coerceAtMost(20)))
        attempts += 1
        val spread = raw * jitter
        val delay = raw - spread + random() * spread * 2
        return delay.toLong().coerceIn(baseMs / 2, maxMs + (maxMs * jitter).toLong())
    }

    fun reset() {
        attempts = 0
    }
}
