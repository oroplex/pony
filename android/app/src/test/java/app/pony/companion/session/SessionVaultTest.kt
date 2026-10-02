package app.pony.companion.session

import app.pony.companion.brain.JceSecretBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionVaultTest {
    private val snapshot = SessionSnapshot(
        relay = "wss://relay.pony.karlmagendavid.com",
        token = "ab".repeat(32),
        botPk = "bot-public-key",
        phonePrivateKey = "PHONE-PRIVATE-KEY-SECRET",
        phonePublicKey = "phone-public",
        clientName = "Grok Bot",
        startedAt = 1_000,
        endsAt = 1_000 + 30 * 60_000,
        safetyCode = "482-193",
    )

    @Test
    fun theSessionIsSealedOnDiskAndRoundTrips() {
        val file = File.createTempFile("pony-session", ".json")
        val box = JceSecretBox.random()
        val vault = SessionVault(file, box)
        vault.save(snapshot)
        assertFalse(vault.raw().contains("PHONE-PRIVATE-KEY-SECRET"))
        assertFalse(vault.raw().contains(snapshot.token))
        assertEquals(snapshot, SessionVault(file, box).load())
        vault.clear()
        assertNull(vault.load())
    }

    @Test
    fun aDifferentKeyCannotOpenIt() {
        val file = File.createTempFile("pony-session", ".json")
        SessionVault(file, JceSecretBox.random()).save(snapshot)
        assertNull(SessionVault(file, JceSecretBox.random()).load())
    }

    @Test
    fun resumableOnlyInsideTheSessionWindow() {
        assertTrue(snapshot.resumable(now = 2_000))
        assertFalse(snapshot.resumable(now = snapshot.endsAt!! + 1))
        assertTrue(snapshot.copy(endsAt = null).resumable(now = Long.MAX_VALUE))
    }

    @Test
    fun sessionLengthsHaveStableWireNames() {
        assertEquals(SessionLength.HALF_HOUR, SessionLength.of(null))
        assertEquals(SessionLength.UNTIL_DISCONNECT, SessionLength.of("open"))
        assertNull(SessionLength.UNTIL_DISCONNECT.millis)
        assertEquals(2 * 60 * 60 * 1000L, SessionLength.of("2h").millis)
    }
}
