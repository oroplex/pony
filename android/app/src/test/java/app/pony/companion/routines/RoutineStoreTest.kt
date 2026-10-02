package app.pony.companion.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutineStoreTest {
    @Test
    fun savesARoutineAndReadsItBack() {
        val store = store()
        val routine = store.save("my usual lunch", "order my usual from the lunch place")!!
        assertEquals("my usual lunch", store.get(routine.id)?.name)
        assertEquals("order my usual from the lunch place", store.get(routine.id)?.text)
        assertEquals(0, store.get(routine.id)?.runCount)
    }

    @Test
    fun blankNameOrBodyIsRefused() {
        val store = store()
        assertNull(store.save("   ", "do something"))
        assertNull(store.save("name", "   "))
    }

    @Test
    fun savingTheSameNameOverwritesInPlace() {
        val store = store()
        val first = store.save("my usual lunch", "order a burrito")!!
        val second = store.save("My Usual Lunch", "order a salad")!!
        assertEquals(1, store.all().size)
        assertEquals(first.id, second.id)
        assertEquals("order a salad", store.get(first.id)?.text)
    }

    @Test
    fun matchFindsARoutineByNameOrRunName() {
        val store = store()
        store.save("my usual lunch", "order lunch")
        assertEquals("my usual lunch", store.match("my usual lunch")?.name)
        assertEquals("my usual lunch", store.match("run my usual lunch")?.name)
        assertNull(store.match("what's the weather"))
    }

    @Test
    fun markRunCountsAndTimestamps() {
        var clock = 100L
        val store = RoutineStore(tempFile(), now = { clock }, newId = { "id" })
        val routine = store.save("lunch", "order lunch")!!
        clock = 200L
        val ran = store.markRun(routine.id)!!
        assertEquals(1, ran.runCount)
        assertEquals(200L, ran.lastRunAt)
        clock = 300L
        assertEquals(2, store.markRun(routine.id)?.runCount)
    }

    @Test
    fun renameRefusesACollisionButAllowsAFreshName() {
        val store = store()
        val lunch = store.save("lunch", "order lunch")!!
        store.save("dinner", "order dinner")!!
        assertNull(store.rename(lunch.id, "dinner"))
        assertEquals("brunch", store.rename(lunch.id, "brunch")?.name)
        assertEquals("brunch", store.get(lunch.id)?.name)
    }

    @Test
    fun deleteRemovesOneRoutine() {
        val store = store()
        val keep = store.save("keep", "keep this")!!
        val drop = store.save("drop", "drop this")!!
        assertTrue(store.delete(drop.id))
        assertNull(store.get(drop.id))
        assertEquals("keep this", store.get(keep.id)?.text)
        assertFalse(store.delete(drop.id))
    }

    @Test
    fun clearEmptiesEverything() {
        val store = store()
        store.save("one", "do one")
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun routinesSurviveAReopen() {
        val file = tempFile()
        RoutineStore(file).apply {
            save("my usual lunch", "order lunch")
            save("morning", "read my calendar")
        }
        val reopened = RoutineStore(file).all()
        assertEquals(2, reopened.size)
        assertEquals("order lunch", reopened.first { it.name == "my usual lunch" }.text)
    }

    @Test
    fun theStoreIsBoundedToItsLimit() {
        var clock = 0L
        var counter = 0
        val store = RoutineStore(tempFile(), now = { clock++ }, newId = { "id_${counter++}" })
        repeat(RoutineStore.MAX_ENTRIES + 5) { i -> store.save("routine $i", "do $i") }
        assertEquals(RoutineStore.MAX_ENTRIES, store.all().size)
        assertNull(store.get("id_0"))
        assertEquals("do ${RoutineStore.MAX_ENTRIES + 4}", store.get("id_${RoutineStore.MAX_ENTRIES + 4}")?.text)
    }

    private fun store(): RoutineStore {
        var counter = 0
        return RoutineStore(tempFile(), newId = { "id_${counter++}" })
    }

    private fun tempFile(): File = File.createTempFile("pony-routines", ".json").apply { delete() }
}
