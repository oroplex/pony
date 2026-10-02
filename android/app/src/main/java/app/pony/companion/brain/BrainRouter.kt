package app.pony.companion.brain

/**
 * Who handles an ask. Grok Bot is the owner's default, but an ask must never
 * sit silently: when Grok Bot can't take it, a key brain on this phone or
 * Pony Basics does, and otherwise the owner gets a one-tap fix.
 */
object BrainRouter {
    const val HANDOFF_MS = 8_000L

    enum class Mode { GROK, PHONE }

    enum class Fallback { PHONE, BASICS }

    enum class Why { CHOSEN, GROK_OFFLINE, GROK_NOT_LISTENING, NO_KEY }

    data class Input(
        val mode: Mode,
        val grokConnected: Boolean,
        val grokListening: Boolean,
        val phoneBrain: String?,
        val basics: Boolean,
    )

    sealed class Route {
        /** Grok Bot is waiting in `wait_for_request`; it gets the ask now. */
        data object Deliver : Route()

        /** Grok Bot is paired but not waiting. Queue it; hand it over after [waitMs] if nobody takes it. */
        data class Queue(val fallback: Fallback?, val waitMs: Long) : Route()

        /** Nothing can run it now. Keep it for Grok Bot and offer pairing or a key. */
        data class Hold(val grokConnected: Boolean) : Route()

        data class Phone(val brain: String, val why: Why) : Route()

        data class Basics(val why: Why) : Route()

        /** On-phone mode without a key, and Basics can't do this one. */
        data object NeedsKey : Route()
    }

    fun route(input: Input): Route = when (input.mode) {
        Mode.GROK -> grok(input)
        Mode.PHONE -> phone(input)
    }

    private fun grok(input: Input): Route {
        if (input.grokConnected && input.grokListening) return Route.Deliver
        val fallback = when {
            input.phoneBrain != null -> Fallback.PHONE
            input.basics -> Fallback.BASICS
            else -> null
        }
        if (input.grokConnected) return Route.Queue(fallback, HANDOFF_MS)
        return when (fallback) {
            Fallback.PHONE -> Route.Phone(input.phoneBrain!!, Why.GROK_OFFLINE)
            Fallback.BASICS -> Route.Basics(Why.GROK_OFFLINE)
            null -> Route.Hold(grokConnected = false)
        }
    }

    private fun phone(input: Input): Route {
        if (input.phoneBrain != null) return Route.Phone(input.phoneBrain, Why.CHOSEN)
        if (input.basics) return Route.Basics(Why.NO_KEY)
        if (input.grokConnected && input.grokListening) return Route.Deliver
        return Route.NeedsKey
    }
}
