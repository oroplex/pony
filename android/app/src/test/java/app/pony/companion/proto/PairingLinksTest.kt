package app.pony.companion.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingLinksTest {
    @Test
    fun decodesAPairLink() {
        val token = "ab".repeat(32)
        val raw =
            "pony://pair?v=1&relay=ws%3A%2F%2F192.168.1.20%3A8787&token=$token&pk=z_0qekXbJeYuSG-eeLL65tW9xFgQxAHZFosjY6MRlSk"
        assertTrue(PairingLinks.isPairLink(raw))
        val fields = PairingLinks.fields(raw)
        assertEquals("1", fields.v)
        assertEquals("ws://192.168.1.20:8787", fields.relay)
        assertEquals(token, fields.token)
        assertEquals("z_0qekXbJeYuSG-eeLL65tW9xFgQxAHZFosjY6MRlSk", fields.pk)
    }

    @Test
    fun acceptsATrailingSlashOnTheHost() {
        val token = "cd".repeat(32)
        val fields = PairingLinks.fields("pony://pair/?v=1&relay=ws%3A%2F%2Fx&token=$token&pk=abc")
        assertEquals("ws://x", fields.relay)
    }

    @Test
    fun rejectsALinkMissingTheToken() {
        assertThrows(IllegalArgumentException::class.java) {
            PairingLinks.fields("pony://pair?v=1&relay=ws://x&pk=abc")
        }
    }

    @Test
    fun grokTemplateClientStaysOffTheRequiredFields() {
        val token = "ef".repeat(32)
        val raw =
            "pony://pair?v=1&relay=wss%3A%2F%2Frelay.pony.karlmagendavid.com&token=$token&pk=abc&client=grokbot"
        assertEquals("grokbot", PairingLinks.clientParam(raw))
        val fields = PairingLinks.fields(raw)
        assertEquals("wss://relay.pony.karlmagendavid.com", fields.relay)
        assertEquals(token, fields.token)
    }

    @Test
    fun clientOnlyLinkDoesNotParseAsAPayload() {
        assertEquals("grokbot", PairingLinks.clientParam("pony://pair?client=grokbot"))
        assertThrows(IllegalArgumentException::class.java) {
            PairingLinks.fields("pony://pair?client=grokbot")
        }
    }

    @Test
    fun rejectsOtherUrls() {
        assertFalse(PairingLinks.isPairLink("https://example.com/pair?token=1"))
        assertFalse(PairingLinks.isPairLink("{ \"v\": 1 }"))
    }

    @Test
    fun decodesTheHttpsTapLinkFromTheFragment() {
        val token = "ab".repeat(32)
        val raw =
            "https://download.pony.karlmagendavid.com/pair#v=1&relay=wss%3A%2F%2Frelay.pony.karlmagendavid.com&token=$token&pk=z_0qekXbJeYuSG-eeLL65tW9xFgQxAHZFosjY6MRlSk"
        // The https link must not widen the narrow pony:// check…
        assertFalse(PairingLinks.isPairLink(raw))
        // …but it is still a pairing link Pony will open.
        assertTrue(PairingLinks.isHttpPairLink(raw))
        assertTrue(PairingLinks.looksLikePairing(raw))
        val fields = PairingLinks.fields(raw)
        assertEquals("1", fields.v)
        assertEquals("wss://relay.pony.karlmagendavid.com", fields.relay)
        assertEquals(token, fields.token)
        assertEquals("z_0qekXbJeYuSG-eeLL65tW9xFgQxAHZFosjY6MRlSk", fields.pk)
    }

    @Test
    fun decodesAnHttpsPairLinkFromTheQuery() {
        val token = "cd".repeat(32)
        val raw = "https://download.pony.karlmagendavid.com/pair?v=1&relay=ws%3A%2F%2Fx&token=$token&pk=abc"
        assertTrue(PairingLinks.isHttpPairLink(raw))
        val fields = PairingLinks.fields(raw)
        assertEquals("ws://x", fields.relay)
        assertEquals(token, fields.token)
        assertEquals("abc", fields.pk)
    }

    @Test
    fun flagsAnExpiredTapLink() {
        val token = "ef".repeat(32)
        val raw = "https://download.pony.karlmagendavid.com/pair#v=1&relay=wss%3A%2F%2Fx&token=$token&pk=abc&exp=1000"
        val fields = PairingLinks.fields(raw)
        assertEquals(1000L, fields.exp)
        assertTrue(PairingLinks.isExpired(fields))
    }

    @Test
    fun acceptsAFutureExpiryInSecondsOrMillis() {
        val token = "ab".repeat(32)
        val futureSecs = System.currentTimeMillis() / 1000 + 600
        val secs = "https://d/pair#v=1&relay=wss%3A%2F%2Fx&token=$token&pk=abc&exp=$futureSecs"
        assertFalse(PairingLinks.isExpired(PairingLinks.fields(secs)))
        val futureMs = System.currentTimeMillis() + 600_000
        val millis = "https://d/pair#v=1&relay=wss%3A%2F%2Fx&token=$token&pk=abc&exp=$futureMs"
        assertFalse(PairingLinks.isExpired(PairingLinks.fields(millis)))
    }

    @Test
    fun leavesExpNullWhenAbsent() {
        val token = "ab".repeat(32)
        val fields = PairingLinks.fields("pony://pair?v=1&relay=ws%3A%2F%2Fx&token=$token&pk=abc")
        assertNull(fields.exp)
        assertFalse(PairingLinks.isExpired(fields))
    }

    @Test
    fun garbageAndNonPairPathsAreNotPairingLinks() {
        assertFalse(PairingLinks.looksLikePairing("totally not a link"))
        assertFalse(PairingLinks.looksLikePairing("https://example.com/not-pair#token=1"))
        assertThrows(IllegalArgumentException::class.java) {
            PairingLinks.fields("https://example.com/pair?token=1")
        }
    }

    @Test
    fun readsTheClientFromAnHttpsFragment() {
        val token = "ab".repeat(32)
        val raw = "https://d/pair#v=1&relay=wss%3A%2F%2Fx&token=$token&pk=abc&client=grokbot"
        assertEquals("grokbot", PairingLinks.clientParam(raw))
    }
}
