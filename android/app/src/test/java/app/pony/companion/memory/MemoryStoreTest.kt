package app.pony.companion.memory

import app.pony.companion.brain.JceSecretBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MemoryStoreTest {
    @Test
    fun remembersAValueAndReadsItBack() {
        val store = MemoryStore(tempFile(), JceSecretBox.random())
        store.put("home city", "Portland")
        assertEquals("Portland", store.get("home_city")?.value)
        assertEquals("Portland", store.get("Home City")?.value)
    }

    @Test
    fun theValueIsNotPlaintextOnDisk() {
        val store = MemoryStore(tempFile(), JceSecretBox.random())
        store.put("coffee_order", "oat flat white")
        val disk = store.raw()
        assertFalse(disk.contains("oat flat white"))
        assertTrue(disk.contains("coffee_order"))
    }

    @Test
    fun aSecondWriteForAKeyReplacesTheFirst() {
        val store = MemoryStore(tempFile(), JceSecretBox.random())
        store.put("coffee_order", "flat white")
        store.put("coffee_order", "cortado")
        assertEquals("cortado", store.get("coffee_order")?.value)
        assertEquals(1, store.all().size)
    }

    @Test
    fun forgettingRemovesOneEntry() {
        val store = MemoryStore(tempFile(), JceSecretBox.random())
        store.put("name", "Alex")
        store.put("home_city", "Portland")
        assertTrue(store.delete("name"))
        assertNull(store.get("name"))
        assertEquals("Portland", store.get("home_city")?.value)
        assertFalse(store.delete("name"))
    }

    @Test
    fun clearEmptiesEverything() {
        val file = tempFile()
        val store = MemoryStore(file, JceSecretBox.random())
        store.put("name", "Alex")
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun entriesReopenAcrossInstancesWithTheSameBox() {
        val file = tempFile()
        val box = JceSecretBox.random()
        MemoryStore(file, box).put("name", "Alex")
        val reopened = MemoryStore(file, box)
        assertEquals("Alex", reopened.get("name")?.value)
    }

    @Test
    fun theStoreIsBoundedToItsLimit() {
        var clock = 0L
        val store = MemoryStore(tempFile(), JceSecretBox.random()) { clock++ }
        repeat(MemoryStore.MAX_ENTRIES + 10) { i -> store.put("fact_$i", "value $i") }
        assertEquals(MemoryStore.MAX_ENTRIES, store.all().size)
        // The newest writes survive; the earliest are dropped.
        assertNull(store.get("fact_0"))
        assertEquals("value ${MemoryStore.MAX_ENTRIES + 9}", store.get("fact_${MemoryStore.MAX_ENTRIES + 9}")?.value)
    }

    private fun tempFile(): File = File.createTempFile("pony-memory", ".json").apply { delete() }
}
