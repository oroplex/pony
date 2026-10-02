package app.pony.companion.brain

import app.pony.companion.brain.BrainRouter.Fallback
import app.pony.companion.brain.BrainRouter.Input
import app.pony.companion.brain.BrainRouter.Mode
import app.pony.companion.brain.BrainRouter.Route
import app.pony.companion.brain.BrainRouter.Why
import org.junit.Assert.assertEquals
import org.junit.Test

class BrainRouterTest {
    private fun grok(connected: Boolean, listening: Boolean, key: String? = null, basics: Boolean = false) =
        BrainRouter.route(Input(Mode.GROK, connected, listening, key, basics))

    @Test
    fun aListeningGrokBotGetsTheAskImmediately() {
        assertEquals(Route.Deliver, grok(connected = true, listening = true))
        assertEquals(Route.Deliver, grok(connected = true, listening = true, key = "Claude", basics = true))
    }

    @Test
    fun aPairedButIdleGrokBotIsQueuedWithAHandoff() {
        assertEquals(Route.Queue(Fallback.PHONE, BrainRouter.HANDOFF_MS), grok(true, false, key = "Claude"))
        assertEquals(Route.Queue(Fallback.BASICS, BrainRouter.HANDOFF_MS), grok(true, false, basics = true))
        assertEquals(Route.Queue(null, BrainRouter.HANDOFF_MS), grok(true, false))
    }

    @Test
    fun anUnpairedGrokBotFallsBackToAKeyThenBasicsThenAHold() {
        assertEquals(Route.Phone("Claude", Why.GROK_OFFLINE), grok(false, false, key = "Claude", basics = true))
        assertEquals(Route.Basics(Why.GROK_OFFLINE), grok(false, false, basics = true))
        assertEquals(Route.Hold(grokConnected = false), grok(false, false))
    }

    @Test
    fun onPhoneModeUsesTheKeyThenBasicsThenAListeningGrokBot() {
        fun phone(key: String?, basics: Boolean, connected: Boolean = false, listening: Boolean = false) =
            BrainRouter.route(Input(Mode.PHONE, connected, listening, key, basics))
        assertEquals(Route.Phone("Gemini", Why.CHOSEN), phone("Gemini", basics = true, connected = true, listening = true))
        assertEquals(Route.Basics(Why.NO_KEY), phone(null, basics = true))
        assertEquals(Route.Deliver, phone(null, basics = false, connected = true, listening = true))
        assertEquals(Route.NeedsKey, phone(null, basics = false, connected = true, listening = false))
    }
}
