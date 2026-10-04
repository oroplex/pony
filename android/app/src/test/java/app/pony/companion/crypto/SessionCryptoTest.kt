package app.pony.companion.crypto

import com.google.crypto.tink.subtle.X25519
import org.junit.Assert.assertEquals
import org.junit.Test

/** Must match shared/src/crypto.test.ts. The relay never sees these bytes. */
class SessionCryptoTest {
    @Test
    fun matchesNobleX25519HkdfAndChaCha() {
        val alicePriv = SessionCrypto.hexToBytes(
            "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a",
        )
        val bobPriv = SessionCrypto.hexToBytes(
            "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb",
        )
        val phone = SessionCrypto.derive(
            alicePriv,
            X25519.publicFromPrivate(bobPriv),
            "11".repeat(32),
            "phone",
        )
        val bot = SessionCrypto.derive(
            bobPriv,
            X25519.publicFromPrivate(alicePriv),
            "11".repeat(32),
            "bot",
        )
        assertEquals(
            "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742",
            hex(phone.shared),
        )
        assertEquals(phone.safetyCode, bot.safetyCode)
        assertEquals("521415", phone.safetyCode)
        assertEquals(
            "a9a52697f10acdce71d060e9856ce4ae60d9cf2ff980b99ef8356efc8bb712db",
            hex(phone.send),
        )
        assertEquals(hex(phone.send), hex(bot.recv))
        val frame = SessionCrypto.hexToBytes(
            "000102030405060708090a0b57a09489a72c3378ec0d8d0a285cf843d8e6d5c6a759091f6816e43040",
        )
        val plain = String(SessionCrypto.decrypt(bot.recv, frame))
        assertEquals("{\"op\":\"ping\"}", plain)
        val keyed = SessionCrypto.encrypt(phone.send, "{\"op\":\"tap\"}".toByteArray(), 3L)
        val opened = SessionCrypto.decryptFrame(bot.recv, keyed, 2)
        assertEquals(3L, opened.seq)
        assertEquals("{\"op\":\"tap\"}", String(opened.plaintext))
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
