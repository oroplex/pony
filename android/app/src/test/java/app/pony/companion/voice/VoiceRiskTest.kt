package app.pony.companion.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRiskTest {
    @Test
    fun riskyLabelsNeedASpokenYes() {
        assertEquals("Send this?", VoiceRisk.promptFor("tap", "Send"))
        assertEquals("Pay?", VoiceRisk.promptFor("tap", "Pay now"))
        assertEquals("Buy this?", VoiceRisk.promptFor("tap", "Place order"))
        assertEquals("Delete this?", VoiceRisk.promptFor("tap", "Delete"))
        assertEquals("Place this call?", VoiceRisk.promptFor("tap", "Call"))
        assertEquals("Transfer this?", VoiceRisk.promptFor("tap", "Transfer"))
        assertEquals("Change security settings?", VoiceRisk.promptFor("tap", "Change password"))
    }

    @Test
    fun ordinaryLabelsAndNonTapActionsPass() {
        assertNull(VoiceRisk.promptFor("tap", "What are you looking for?"))
        assertNull(VoiceRisk.promptFor("tap", "Recall"))
        assertNull(VoiceRisk.promptFor("tap", "Display"))
        assertNull(VoiceRisk.promptFor("type", "Send"))
        assertEquals("Delete this?", VoiceRisk.promptFor("swipe", "Delete"))
    }

    @Test
    fun stopAndYesAreExplicit() {
        assertTrue(VoiceWords.isStop("Stop"))
        assertTrue(VoiceWords.isStop("stop pony"))
        assertTrue(VoiceWords.isYes("Yes"))
        assertTrue(VoiceWords.isYes("do it"))
        assertTrue(VoiceWords.isNo("nope"))
        assertTrue(!VoiceWords.isYes("yesterday"))
        assertTrue(!VoiceWords.isStop("unstoppable"))
    }
}
