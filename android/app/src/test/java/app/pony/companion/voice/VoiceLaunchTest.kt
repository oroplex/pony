package app.pony.companion.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceLaunchTest {
    @Test
    fun keepsAKnownSource() {
        assertEquals("widget", VoiceLaunch.source("widget"))
        assertEquals("qs_tile", VoiceLaunch.source("qs_tile"))
    }

    @Test
    fun fallsBackToTheWidgetLabelForAnythingElse() {
        assertEquals("widget", VoiceLaunch.source(null))
        assertEquals("widget", VoiceLaunch.source(""))
        assertEquals("widget", VoiceLaunch.source("button"))
        assertEquals("widget", VoiceLaunch.source("wake_word"))
    }
}
