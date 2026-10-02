package app.pony.companion.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RelayUrlTest {
    @Test
    fun normalizesHttpAndExistingWsPaths() {
        assertEquals("ws://127.0.0.1:8787/ws", RelayClient.normalizeWs("http://127.0.0.1:8787"))
        assertEquals("wss://relay.example/ws", RelayClient.normalizeWs("https://relay.example/"))
        assertEquals("ws://10.0.2.2:8787/ws", RelayClient.normalizeWs("ws://10.0.2.2:8787/ws"))
    }

    @Test
    fun tailscaleAddressesDialTheCleartextAlias() {
        assertEquals(
            "ws://pony-tailscale.invalid:8787/ws",
            RelayClient.dialUrl("http://100.64.8.8:8787"),
        )
        assertEquals(
            "wss://relay.pony.karlmagendavid.com/ws",
            RelayClient.dialUrl("https://relay.pony.karlmagendavid.com"),
        )
        assertEquals(
            "ws://my-pc.tailnet.ts.net:8787/ws",
            RelayClient.dialUrl("ws://my-pc.tailnet.ts.net:8787"),
        )
    }

    @Test
    fun lanAddressesDialTheLanAlias() {
        assertEquals("ws://pony-lan.invalid:8787/ws", RelayClient.dialUrl("http://192.168.1.20:8787"))
        assertEquals("ws://pony-lan.invalid:8787/ws", RelayClient.dialUrl("ws://10.0.0.5:8787/ws"))
        assertNull(RelayClient.aliasFor("8.8.8.8"))
        assertNull(RelayClient.aliasFor("127.0.0.1"))
    }

    @Test
    fun theNetworkSecurityConfigAllowsBothAliasesAndNothingPublic() {
        val config = listOf(
            File("src/main/res/xml/network_security_config.xml"),
            File("app/src/main/res/xml/network_security_config.xml"),
        ).first { it.exists() }.readText()
        assertTrue(config.contains("cleartextTrafficPermitted=\"false\""))
        assertTrue(config.contains(RelayClient.TAILSCALE_DIAL_HOST))
        assertTrue(config.contains(RelayClient.LAN_DIAL_HOST))
        val allowed = Regex("<domain[^>]*>([^<]+)</domain>").findAll(config).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("localhost", "127.0.0.1", "ts.net", "pony-tailscale.invalid", "pony-lan.invalid"), allowed)
    }
}
