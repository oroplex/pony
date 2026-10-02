package app.pony.companion.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySummaryTest {
    @Test
    fun noMemoriesGiveNoPreamble() {
        assertEquals("", MemorySummary.of(emptyList()))
    }

    @Test
    fun entriesBecomeAReadableLineSortedByKey() {
        val summary = MemorySummary.of(
            listOf(
                MemoryEntry("home_city", "Portland", "owner", 2),
                MemoryEntry("name", "Alex", "owner", 1),
            ),
        )
        assertTrue(summary.startsWith(MemorySummary.LEAD))
        assertTrue(summary.contains("home city is Portland"))
        assertTrue(summary.contains("name is Alex"))
        // Sorted by key: home_city before name.
        assertTrue(summary.indexOf("home city") < summary.indexOf("name is"))
        assertTrue(summary.endsWith("."))
    }

    @Test
    fun aLongSummaryIsClipped() {
        val many = (0 until 100).map { MemoryEntry("fact_$it", "a rather wordy value number $it", "owner", it.toLong()) }
        val summary = MemorySummary.of(many)
        assertTrue(summary.length <= MemorySummary.LEAD.length + MemorySummary.MAX_CHARS + 1)
        assertTrue(summary.contains("…"))
    }
}
