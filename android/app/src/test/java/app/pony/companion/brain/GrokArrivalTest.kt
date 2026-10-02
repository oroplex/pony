package app.pony.companion.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrokArrivalTest {
    @Test
    fun templateSelectsGrokAndTheCloudRelay() {
        assertTrue(GrokArrival.matches("grokbot"))
        assertTrue(GrokArrival.matches("Grok Bot"))
        assertTrue(GrokArrival.matches("grok-bot"))
        assertTrue(GrokArrival.useCloudRelay(null))
        assertTrue(GrokArrival.useCloudRelay(""))
        assertTrue(GrokArrival.useCloudRelay("wss://relay.pony.karlmagendavid.com"))
        assertFalse(GrokArrival.matches("claude"))
    }

    @Test
    fun aTailscaleRelayInTheLinkIsKept() {
        assertFalse(GrokArrival.useCloudRelay("http://100.64.8.8:8787"))
        assertFalse(GrokArrival.useCloudRelay("ws://my-pc.tailnet.ts.net:8787"))
    }
}
