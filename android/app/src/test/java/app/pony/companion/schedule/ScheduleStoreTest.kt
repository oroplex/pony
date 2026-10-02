package app.pony.companion.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek

class ScheduleStoreTest {
    @Test
    fun addsATaskAndReadsItBack() {
        val store = store()
        val task = store.add("read my calendar", Schedule(7, 0))
        assertEquals("read my calendar", store.get(task.id)?.text)
        assertEquals(7, store.get(task.id)?.schedule?.hour)
        assertTrue(store.get(task.id)?.enabled == true)
    }

    @Test
    fun theDaysAndOnceInstantSurviveAReopen() {
        val file = tempFile()
        val weekday = Schedule(8, 30, Schedule.WEEKDAYS)
        val once = Schedule(18, 0, onceAt = 1_900_000_000_000L)
        ScheduleStore(file).apply {
            add("stand up", weekday)
            add("send report", once)
        }
        val reopened = ScheduleStore(file).all()
        assertEquals(2, reopened.size)
        assertEquals(Schedule.WEEKDAYS, reopened.first { it.text == "stand up" }.schedule.days)
        val report = reopened.first { it.text == "send report" }.schedule
        assertEquals(1_900_000_000_000L, report.onceAt)
        assertTrue(report.days.isEmpty())
        assertFalse(report.repeats)
    }

    @Test
    fun togglingEnabledPersists() {
        val store = store()
        val task = store.add("water plants", Schedule(9, 0, setOf(DayOfWeek.MONDAY)))
        store.setEnabled(task.id, false)
        assertFalse(store.get(task.id)?.enabled == true)
        store.setEnabled(task.id, true)
        assertTrue(store.get(task.id)?.enabled == true)
        assertNull(store.setEnabled("missing", false))
    }

    @Test
    fun deleteRemovesOneTask() {
        val store = store()
        val keep = store.add("keep this", Schedule(7, 0))
        val drop = store.add("drop this", Schedule(8, 0))
        assertTrue(store.delete(drop.id))
        assertNull(store.get(drop.id))
        assertEquals("keep this", store.get(keep.id)?.text)
        assertFalse(store.delete(drop.id))
    }

    @Test
    fun clearEmptiesEverything() {
        val store = store()
        store.add("one", Schedule(7, 0))
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun theStoreIsBoundedToItsLimit() {
        var clock = 0L
        var counter = 0
        val store = ScheduleStore(tempFile(), now = { clock++ }, newId = { "id_${counter++}" })
        repeat(ScheduleStore.MAX_ENTRIES + 5) { i -> store.add("task $i", Schedule(7, 0)) }
        assertEquals(ScheduleStore.MAX_ENTRIES, store.all().size)
        // The newest survive; the earliest are dropped.
        assertNull(store.get("id_0"))
        assertEquals("task ${ScheduleStore.MAX_ENTRIES + 4}", store.get("id_${ScheduleStore.MAX_ENTRIES + 4}")?.text)
    }

    private fun store(): ScheduleStore {
        var counter = 0
        return ScheduleStore(tempFile(), newId = { "id_${counter++}" })
    }

    private fun tempFile(): File = File.createTempFile("pony-schedule", ".json").apply { delete() }
}
