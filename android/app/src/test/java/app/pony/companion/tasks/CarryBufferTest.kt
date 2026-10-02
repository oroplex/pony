package app.pony.companion.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarryBufferTest {
    private var clock = 0L

    private fun buffer(maxEntries: Int = 12, maxLength: Int = 2_000) =
        CarryBuffer(maxEntries = maxEntries, maxLength = maxLength, now = { clock })

    @Test
    fun stashesAndRecallsByLabel() {
        val buffer = buffer()
        assertTrue(buffer.stash("order number", "XK28DQ").ok)
        assertEquals("XK28DQ", buffer.recall("order number"))
    }

    @Test
    fun recallIgnoresCaseAndSurroundingSpace() {
        val buffer = buffer()
        buffer.stash("  Order Number  ", "XK28DQ")
        assertEquals("XK28DQ", buffer.recall("order number"))
        assertEquals("XK28DQ", buffer.recall("ORDER NUMBER"))
    }

    @Test
    fun trimsTheStashedValue() {
        val buffer = buffer()
        buffer.stash("address", "  742 Evergreen Terrace  ")
        assertEquals("742 Evergreen Terrace", buffer.recall("address"))
    }

    @Test
    fun recallsNothingForAnUnknownLabel() {
        assertNull(buffer().recall("missing"))
    }

    @Test
    fun evictsTheOldestWhenFull() {
        val buffer = buffer(maxEntries = 3)
        buffer.stash("a", "one"); clock++
        buffer.stash("b", "two"); clock++
        buffer.stash("c", "three"); clock++
        buffer.stash("d", "four")
        assertNull(buffer.recall("a"))
        assertEquals("four", buffer.recall("d"))
        assertEquals(listOf("b", "c", "d"), buffer.labels())
    }

    @Test
    fun reStashingALabelKeepsItFromBeingEvicted() {
        val buffer = buffer(maxEntries = 2)
        buffer.stash("a", "one"); clock++
        buffer.stash("b", "two"); clock++
        buffer.stash("a", "one-again"); clock++
        buffer.stash("c", "three")
        assertEquals("one-again", buffer.recall("a"))
        assertNull(buffer.recall("b"))
        assertEquals("three", buffer.recall("c"))
    }

    @Test
    fun capsTheValueLength() {
        val buffer = buffer(maxLength = 10)
        buffer.stash("blob", "0123456789abcdef")
        assertEquals("0123456789", buffer.recall("blob"))
    }

    @Test
    fun refusesAnEmptyLabelOrText() {
        val buffer = buffer()
        assertFalse(buffer.stash("", "value").ok)
        assertFalse(buffer.stash("label", "   ").ok)
    }

    @Test
    fun refusesSecretsReusingMemoryGuard() {
        val buffer = buffer()
        assertFalse(buffer.stash("password", "hunter2").ok)
        assertFalse(buffer.stash("code", "483920").ok)
        assertFalse(buffer.stash("token", "sk-abcdef0123456789xyz").ok)
        assertNull(buffer.recall("password"))
    }

    @Test
    fun keepsOrdinaryCrossAppValues() {
        val buffer = buffer()
        assertTrue(buffer.stash("confirmation", "XK28DQ").ok)
        assertTrue(buffer.stash("address", "742 Evergreen Terrace").ok)
        assertTrue(buffer.stash("total", "$42.50").ok)
    }

    @Test
    fun labelsAreOrderedOldestFirst() {
        val buffer = buffer()
        buffer.stash("first", "1"); clock++
        buffer.stash("second", "2"); clock++
        buffer.stash("third", "3")
        assertEquals(listOf("first", "second", "third"), buffer.labels())
    }

    @Test
    fun clearEmptiesTheBuffer() {
        val buffer = buffer()
        buffer.stash("a", "one")
        buffer.clear()
        assertNull(buffer.recall("a"))
        assertTrue(buffer.labels().isEmpty())
    }
}
