package app.pony.companion.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextPolicyTest {
    @Test
    fun cloudAndLocalhostAndTailscaleAreAllowed() {
        assertTrue(CleartextPolicy.allows(CleartextPolicy.CLOUD_HTTP))
        assertTrue(CleartextPolicy.allows("wss://relay.pony.karlmagendavid.com/ws"))
        assertTrue(CleartextPolicy.allows("http://127.0.0.1:8787"))
        assertTrue(CleartextPolicy.allows("ws://localhost:8787"))
        assertTrue(CleartextPolicy.allows("http://my-pc.tailnet.ts.net:8787"))
        assertTrue(CleartextPolicy.allows("ws://100.64.0.1:8787"))
        assertTrue(CleartextPolicy.allows("http://100.127.255.255:8787"))
        assertTrue(CleartextPolicy.isCloud("wss://relay.pony.karlmagendavid.com"))
    }

    @Test
    fun privateLanRangesAreAllowedInCleartext() {
        assertTrue(CleartextPolicy.allows("http://192.168.1.20:8787"))
        assertTrue(CleartextPolicy.allows("ws://10.0.0.5:8787"))
        assertTrue(CleartextPolicy.allows("http://172.16.0.9:8787"))
        assertTrue(CleartextPolicy.allows("http://172.31.255.254:8787"))
        assertTrue(CleartextPolicy.isPrivate("http://10.1.2.3:8787"))
        assertFalse(CleartextPolicy.isPrivate(CleartextPolicy.CLOUD_HTTP))
    }

    @Test
    fun publicCleartextIsRefused() {
        assertFalse(CleartextPolicy.allows("http://100.63.255.255:8787"))
        assertFalse(CleartextPolicy.allows("http://100.128.0.1:8787"))
        assertFalse(CleartextPolicy.allows("http://172.15.0.1:8787"))
        assertFalse(CleartextPolicy.allows("http://172.32.0.1:8787"))
        assertFalse(CleartextPolicy.allows("http://192.169.1.1:8787"))
        assertFalse(CleartextPolicy.allows("http://8.8.8.8"))
        assertFalse(CleartextPolicy.allows("ws://1.1.1.1:8787"))
        assertFalse(CleartextPolicy.allows("http://example.com"))
        assertFalse(CleartextPolicy.allows("http://10.0.0.5.nip.io:8787"))
        assertFalse(CleartextPolicy.allows("http://192.168.0001.1:8787"))
        assertFalse(CleartextPolicy.allows("http://127.0.0.2:8787"))
        assertFalse(CleartextPolicy.allows("ftp://localhost"))
    }

    @Test
    fun canonicalKeepsTheChosenRelay() {
        assertEquals("http://100.70.1.2:8787", CleartextPolicy.canonical("100.70.1.2:8787"))
        assertEquals("ws://my-pc.tailnet.ts.net:8787", CleartextPolicy.canonical("ws://my-pc.tailnet.ts.net:8787/ws"))
        assertEquals(CleartextPolicy.CLOUD_HTTP, CleartextPolicy.canonical(CleartextPolicy.CLOUD_HTTP))
        assertTrue(CleartextPolicy.sameHost("http://100.70.1.2:8787", "ws://100.70.1.2:8787/ws"))
    }
}
