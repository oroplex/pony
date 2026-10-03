package app.pony.companion.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingPayloadTest {
    private val token = "ab".repeat(32)

    @Test
    fun parsesTheQrJsonPayload() {
        val payload = PairingPayload.parse(
            """{"v":1,"relay":"wss://relay.pony.karlmagendavid.com","token":"$token","pk":"abc"}""",
        )
        assertEquals("wss://relay.pony.karlmagendavid.com", payload.relay)
        assertEquals(token, payload.token)
        assertEquals("abc", payload.pk)
    }

    @Test
    fun lowercasesTheToken() {
        val payload = PairingPayload.parse(
            """{"v":1,"relay":"ws://x","token":"${token.uppercase()}","pk":"abc"}""",
        )
        assertEquals(token, payload.token)
    }

    @Test
    fun roundTripsThroughToJson() {
        val payload = PairingPayload(relay = "ws://x", token = token, pk = "abc")
        assertEquals(payload, PairingPayload.parse(payload.toJson()))
    }

    @Test
    fun acceptsV1AndV2AndRefusesAFutureVersion() {
        val v1 = PairingPayload.parse("""{"v":1,"relay":"ws://x","token":"$token","pk":"abc"}""")
        assertEquals(1, v1.v)
        val v2 = PairingPayload.parse("""{"v":2,"relay":"ws://x","token":"$token","pk":"abc"}""")
        assertEquals(2, v2.v)
        assertThrows(IllegalArgumentException::class.java) {
            PairingPayload.parse("""{"v":99,"relay":"ws://x","token":"$token","pk":"abc"}""")
        }
    }

    @Test
    fun rejectsATokenThatIsNotThirtyTwoBytesOfHex() {
        assertThrows(IllegalArgumentException::class.java) {
            PairingPayload.parse("""{"v":1,"relay":"ws://x","token":"nothex","pk":"abc"}""")
        }
    }

    @Test
    fun rejectsAPayloadMissingThePublicKey() {
        assertThrows(Exception::class.java) {
            PairingPayload.parse("""{"v":1,"relay":"ws://x","token":"$token"}""")
        }
    }
}
