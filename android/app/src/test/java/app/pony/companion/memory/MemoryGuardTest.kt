package app.pony.companion.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGuardTest {
    @Test
    fun refusesSecretsByTheirKey() {
        assertFalse(MemoryGuard.check("password", "hunter2").allowed)
        assertFalse(MemoryGuard.check("wifi password", "sunshine").allowed)
        assertFalse(MemoryGuard.check("card number", "visa ending 42").allowed)
        assertFalse(MemoryGuard.check("2fa secret", "whatever").allowed)
        assertFalse(MemoryGuard.check("API key", "value").allowed)
    }

    @Test
    fun refusesCodesAndCardNumbersByTheirShape() {
        assertFalse(MemoryGuard.check("favorite number", "483920").allowed)
        assertFalse(MemoryGuard.check("cvv", "123").allowed)
        assertFalse(MemoryGuard.check("saved", "4111 1111 1111 1111").allowed)
        assertFalse(MemoryGuard.check("pin", "4821").allowed)
    }

    @Test
    fun refusesThingsThatLookLikeApiKeysOrTokens() {
        assertFalse(MemoryGuard.check("note", "sk-pony-abc123def456").allowed)
        assertFalse(MemoryGuard.check("note", "AIzaSyA12345678901234567890").allowed)
        assertFalse(MemoryGuard.check("thing", "a1b2c3d4e5f6g7h8i9j0k1l2").allowed)
    }

    @Test
    fun keepsOrdinaryPreferencesAndFacts() {
        assertTrue(MemoryGuard.check("name", "Alex").allowed)
        assertTrue(MemoryGuard.check("home city", "Portland").allowed)
        assertTrue(MemoryGuard.check("coffee order", "oat flat white").allowed)
        assertTrue(MemoryGuard.check("favorite team", "the Cats").allowed)
        assertTrue(MemoryGuard.check("apartment", "flat 12").allowed)
    }

    @Test
    fun refusesEmptyInput() {
        assertFalse(MemoryGuard.check("", "value").allowed)
        assertFalse(MemoryGuard.check("key", "   ").allowed)
    }

    @Test
    fun givesAReasonWhenItRefuses() {
        val decision = MemoryGuard.check("password", "hunter2")
        assertEquals(false, decision.allowed)
        assertTrue(decision.reason?.isNotBlank() == true)
    }
}
