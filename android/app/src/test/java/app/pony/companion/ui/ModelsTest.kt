package app.pony.companion.ui

import app.pony.companion.brain.ProviderPreset
import app.pony.companion.brain.ProviderRecord
import app.pony.companion.session.Connection
import app.pony.companion.session.ReadinessState
import app.pony.companion.session.SessionUi
import app.pony.companion.ui.design.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ModelsTest {
    private val now = 1_000_000L

    @Test
    fun theBadgeSaysWhatGrokBotIsDoingAndOffersTheFix() {
        val listening = connectionBadge(SessionUi(connection = Connection.Connected), listening = true, grok = "Grok Bot", resumable = false, now = now)
        assertEquals("Grok Bot is listening", listening.title)
        assertTrue(listening.live)
        assertEquals(BadgeAction.NONE, listening.action)

        val idle = connectionBadge(SessionUi(connection = Connection.Connected), listening = false, grok = "Grok Bot", resumable = false, now = now)
        assertEquals("Connected to Grok Bot", idle.title)

        val away = connectionBadge(SessionUi(connection = Connection.Connected, peerAway = true), true, "Grok Bot", false, now)
        assertEquals(Tone.Warning, away.tone)

        val retrying = connectionBadge(SessionUi(connection = Connection.Reconnecting, nextRetryAt = now + 4_000), false, "Grok Bot", true, now)
        assertEquals(BadgeAction.RETRY, retrying.action)
        assertEquals("Trying again in 4s", retrying.detail)

        assertEquals(BadgeAction.RECONNECT, connectionBadge(SessionUi(), false, "Grok Bot", resumable = true, now = now).action)
        assertEquals(BadgeAction.CONNECT, connectionBadge(SessionUi(), false, "Grok Bot", resumable = false, now = now).action)
    }

    @Test
    fun aKeyBrainMakesTheHomePillReadInsteadOfBroken() {
        val ready = connectionBadge(SessionUi(connection = Connection.Idle), false, "Grok Bot", resumable = false, now = now, keyBrain = "Claude")
        assertEquals("Ready · Claude", ready.title)
        assertEquals(Tone.Success, ready.tone)
        assertEquals(BadgeAction.NONE, ready.action)

        val stillReadyOnError = connectionBadge(SessionUi(connection = Connection.Error), false, "Grok Bot", resumable = true, now = now, keyBrain = "Claude")
        assertEquals(Tone.Success, stillReadyOnError.tone)

        val neither = connectionBadge(SessionUi(connection = Connection.Idle), false, "Grok Bot", resumable = false, now = now, keyBrain = null)
        assertEquals(Tone.Warning, neither.tone)
        assertEquals(BadgeAction.CONNECT, neither.action)

        val link = assistantLink(SessionUi(connection = Connection.Idle), keyBrain = "Claude", resumable = false, grok = "Grok Bot")
        assertEquals("Link an assistant", link?.label)
        assertEquals(BadgeAction.CONNECT, link?.action)
        val resume = assistantLink(SessionUi(connection = Connection.Error), keyBrain = "Claude", resumable = true, grok = "Grok Bot")
        assertEquals(BadgeAction.RECONNECT, resume?.action)
        assertEquals(null, assistantLink(SessionUi(connection = Connection.Idle), keyBrain = null, resumable = false, grok = "Grok Bot"))
    }

    @Test
    fun thePickerListsEveryKeyBrainAndMarksTheOneInUse() {
        val claude = ProviderRecord("p1", "Claude", ProviderPreset.ANTHROPIC, "claude-opus-5-5", "https://api.anthropic.com", "7f2c", active = true)
        val gemini = ProviderRecord("p2", "Gemini", ProviderPreset.GEMINI, "gemini-2.5-flash", "https://x", "aa11", active = false)

        // Phone brain in use: the active saved brain reads as in use, the saved
        // one that isn't active reads as connected, the rest as not connected.
        val phone = brainChoices(listOf(claude, gemini), phoneBrainSelected = true)
        assertEquals(ProviderPreset.pickable, phone.map { it.preset })
        val claudeChoice = phone.first { it.preset == ProviderPreset.ANTHROPIC }
        assertTrue(claudeChoice.connected)
        assertTrue(claudeChoice.active)
        assertEquals("7f2c", claudeChoice.keyLast4)
        assertEquals("p1", claudeChoice.recordId)
        val geminiChoice = phone.first { it.preset == ProviderPreset.GEMINI }
        assertTrue(geminiChoice.connected)
        assertFalse(geminiChoice.active)
        val openAiChoice = phone.first { it.preset == ProviderPreset.OPENAI }
        assertFalse(openAiChoice.connected)
        assertEquals(null, openAiChoice.recordId)

        // When Grok Bot is the chosen brain, a saved key is a backup — connected,
        // never "in use".
        val grok = brainChoices(listOf(claude), phoneBrainSelected = false)
        val claudeBackup = grok.first { it.preset == ProviderPreset.ANTHROPIC }
        assertTrue(claudeBackup.connected)
        assertFalse(claudeBackup.active)
    }

    @Test
    fun theOwnerCanAlwaysDisconnectAPairedBotFromHome() {
        // While a bot is live the secondary row is a one-tap Disconnect — even
        // with no key brain, which is the exact "stuck paired" case.
        val listening = assistantLink(SessionUi(connection = Connection.Connected), keyBrain = null, resumable = false, grok = "Grok Bot")
        assertEquals("Disconnect Grok Bot", listening?.label)
        assertEquals(BadgeAction.DISCONNECT, listening?.action)

        val withKey = assistantLink(SessionUi(connection = Connection.Connected), keyBrain = "Claude", resumable = false, grok = "Grok Bot")
        assertEquals(BadgeAction.DISCONNECT, withKey?.action)

        // Mid-reconnect counts as live, so the owner can still bail out.
        val reconnecting = assistantLink(SessionUi(connection = Connection.Reconnecting), keyBrain = null, resumable = true, grok = "Grok Bot")
        assertEquals(BadgeAction.DISCONNECT, reconnecting?.action)

        // After disconnecting, the same row flips to a reconnect/pair path.
        val afterDisconnect = assistantLink(SessionUi(connection = Connection.Idle), keyBrain = "Claude", resumable = true, grok = "Grok Bot")
        assertEquals(BadgeAction.RECONNECT, afterDisconnect?.action)
    }

    @Test
    fun theAskPageNeverLeavesTheOwnerGuessing() {
        val offline = askStatus(phoneMode = false, SessionUi(), listening = false, keyBrain = null, grok = "Grok Bot")
        assertEquals("Grok Bot isn't connected", offline.title)
        assertEquals(listOf(AskFix.PAIR, AskFix.ADD_KEY), offline.fixes)

        val backup = askStatus(false, SessionUi(), false, "Claude", "Grok Bot")
        assertTrue(backup.body.contains("Claude on this phone"))

        val idle = askStatus(false, SessionUi(connection = Connection.Connected), listening = false, keyBrain = null, grok = "Grok Bot")
        assertEquals(Tone.Warning, idle.tone)
        assertTrue(AskFix.LISTEN_HELP in idle.fixes)

        val ready = askStatus(false, SessionUi(connection = Connection.Connected), listening = true, keyBrain = null, grok = "Grok Bot")
        assertEquals(Tone.Success, ready.tone)
        assertTrue(ready.fixes.isEmpty())

        val handoff = askStatus(false, SessionUi(connection = Connection.Connected), listening = false, keyBrain = "Claude", grok = "Grok Bot")
        assertTrue(handoff.body.contains("within a few seconds"))

        val noKey = askStatus(phoneMode = true, SessionUi(), listening = false, keyBrain = null, grok = "Grok Bot")
        assertEquals(listOf(AskFix.ADD_KEY, AskFix.USE_GROK), noKey.fixes)
    }

    @Test
    fun setupReflectsTheLiveState() {
        val none = setupItems(ReadinessState(), samsung = true)
        assertEquals(5, none.size)
        assertTrue(none.first { it.key == "control" }.required)
        assertTrue(none.none { it.done })
        assertTrue(none.first { it.key == "battery" }.body.contains("Samsung"))
        val on = setupItems(ReadinessState(accessibility = true, battery = true), samsung = false)
        assertTrue(on.first { it.key == "control" }.done)
        assertFalse(on.first { it.key == "battery" }.body.contains("Samsung"))
    }

    @Test
    fun suggestionsShowOffRealAppsThatAreInstalled() {
        assertTrue(SUGGESTIONS.size >= 16)
        assertTrue("every showcase ask is tied to a real app", SUGGESTIONS.all { it.app != null })

        assertTrue("never advertise apps the user lacks", pickSuggestions(isInstalled = { false }).isEmpty())

        val have = setOf("com.spotify.music", "com.target.ui", "com.teslamotors.tesla", "com.airbnb.android", "com.dd.doordash")
        val picked = pickSuggestions(isInstalled = { it in have }, count = 4)
        assertEquals(4, picked.size)
        assertTrue(picked.all { it.app in have })
        assertEquals(picked.size, picked.map { it.label }.toSet().size)

        assertEquals(1, pickSuggestions(isInstalled = { it == "com.spotify.music" }, count = 4).size)

        val seedA = pickSuggestions(isInstalled = { true }, count = 4, random = Random(7))
        val seedB = pickSuggestions(isInstalled = { true }, count = 4, random = Random(7))
        assertEquals(seedA.map { it.label }, seedB.map { it.label })
    }

    @Test
    fun timesReadNaturally() {
        assertEquals("Just now", relativeTime(now - 10_000, now))
        assertEquals("5 min ago", relativeTime(now - 5 * 60_000, now))
        assertEquals("Yesterday", relativeTime(now - 30 * 3_600_000L, now))
        assertEquals("4s", durationText(4_300))
        assertEquals("2m 5s", durationText(125_000))
        assertEquals("1 hr 12 min left", remainingText(now + 72 * 60_000, now))
    }
}
