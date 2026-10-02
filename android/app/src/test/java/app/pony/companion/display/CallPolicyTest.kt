package app.pony.companion.display

import app.pony.companion.display.CallPolicy.Kind
import app.pony.companion.display.CallPolicy.Signals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallPolicyTest {
    private val now = 10_000_000L

    @Test
    fun inCommunicationAloneIsNotACall() {
        val voiceNote = Signals(audioMode = CallPolicy.MODE_IN_COMMUNICATION, foregroundPackage = "com.whatsapp", foregroundClass = "com.whatsapp.Conversation", now = now)
        assertEquals(Kind.NONE, CallPolicy.status(voiceNote).kind)
        val meeting = Signals(audioMode = CallPolicy.MODE_IN_COMMUNICATION, foregroundPackage = "us.zoom.videomeetings", now = now)
        assertFalse(CallPolicy.status(meeting).active)
    }

    @Test
    fun whatsAppCallsAreFoundFromTheCallNotificationOrTheCallScreen() {
        val byNotice = Signals(
            audioMode = CallPolicy.MODE_IN_COMMUNICATION,
            callNoticePackage = "com.whatsapp",
            callNoticeAt = now - 60_000,
            foregroundPackage = "com.sec.android.app.popupcalculator",
            now = now,
        )
        assertEquals(CallPolicy.Status(Kind.VOIP, "com.whatsapp"), CallPolicy.status(byNotice))
        val byScreen = Signals(
            audioMode = CallPolicy.MODE_IN_COMMUNICATION,
            foregroundPackage = "com.whatsapp",
            foregroundClass = "com.whatsapp.voipcalling.VoipActivityV2",
            now = now,
        )
        assertEquals(Kind.VOIP, CallPolicy.status(byScreen).kind)
    }

    @Test
    fun carrierCallsAndRingingAreCalls() {
        assertEquals(Kind.CARRIER, CallPolicy.status(Signals(CallPolicy.MODE_IN_CALL, now = now)).kind)
        assertEquals(Kind.RINGING, CallPolicy.status(Signals(CallPolicy.MODE_RINGTONE, now = now)).kind)
        assertEquals(Kind.CARRIER, CallPolicy.status(Signals(CallPolicy.MODE_NORMAL, telecomInCall = true, now = now)).kind)
        assertEquals(Kind.NONE, CallPolicy.status(Signals(CallPolicy.MODE_NORMAL, now = now)).kind)
    }

    @Test
    fun onlyTheCallScreenInFrontIsProtected() {
        val whatsapp = CallPolicy.Status(Kind.VOIP, "com.whatsapp")
        assertTrue(CallPolicy.callScreenInFront(whatsapp, "com.whatsapp", "com.whatsapp.voipcalling.VoipActivityV2", null))
        assertFalse(CallPolicy.callScreenInFront(whatsapp, "com.whatsapp", "com.whatsapp.Conversation", null))
        assertFalse(CallPolicy.callScreenInFront(whatsapp, "com.sec.android.app.popupcalculator", "Calculator", null))
        val carrier = CallPolicy.Status(Kind.CARRIER)
        assertTrue(CallPolicy.callScreenInFront(carrier, "com.samsung.android.incallui", "InCallActivity", null))
        assertTrue(CallPolicy.callScreenInFront(carrier, "com.samsung.android.dialer", "DialtactsActivity", "com.samsung.android.dialer"))
        assertFalse(CallPolicy.callScreenInFront(CallPolicy.Status(Kind.NONE), "com.samsung.android.dialer", "DialtactsActivity", "com.samsung.android.dialer"))
        assertTrue(CallPolicy.callScreenInFront(CallPolicy.Status(Kind.NONE), "com.samsung.android.incallui", "InCallActivity", null))
    }

    @Test
    fun protectedPackagesGrowOnlyDuringACall() {
        val idle = CallPolicy.protectedPackages(CallPolicy.Status(Kind.NONE), "com.samsung.android.dialer")
        assertFalse("com.samsung.android.dialer" in idle)
        assertTrue("com.samsung.android.incallui" in idle)
        val voip = CallPolicy.protectedPackages(CallPolicy.Status(Kind.VOIP, "com.whatsapp"), "com.samsung.android.dialer")
        assertTrue("com.whatsapp" in voip)
        assertTrue("com.samsung.android.dialer" in voip)
    }
}
