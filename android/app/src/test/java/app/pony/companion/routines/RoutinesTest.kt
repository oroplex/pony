package app.pony.companion.routines

import app.pony.companion.tasks.Brains
import app.pony.companion.tasks.TaskRecord
import app.pony.companion.tasks.TaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutinesTest {
    @Test
    fun normalizeLowercasesAndDropsPunctuation() {
        assertEquals("my usual lunch", Routines.normalize("  My, Usual—Lunch!  "))
    }

    @Test
    fun pickMatchesTheExactName() {
        val names = listOf("my usual lunch", "morning rundown")
        assertEquals("my usual lunch", Routines.pick("my usual lunch", names))
        assertEquals("morning rundown", Routines.pick("Morning rundown.", names))
    }

    @Test
    fun pickMatchesARunPhrase() {
        val names = listOf("my usual lunch")
        assertEquals("my usual lunch", Routines.pick("run my usual lunch", names))
        assertEquals("my usual lunch", Routines.pick("play my usual lunch", names))
        assertEquals("my usual lunch", Routines.pick("do my usual lunch", names))
    }

    @Test
    fun pickPrefersTheLongestNameSoAShortOneDoesNotShadow() {
        val names = listOf("lunch", "my usual lunch")
        assertEquals("my usual lunch", Routines.pick("my usual lunch", names))
        assertEquals("lunch", Routines.pick("lunch", names))
    }

    @Test
    fun pickReturnsNullForAnOrdinaryAsk() {
        val names = listOf("my usual lunch")
        assertNull(Routines.pick("what's the weather today", names))
        assertNull(Routines.pick("", names))
    }

    @Test
    fun parseSaveTakesTheNameAfterAPointer() {
        assertEquals("my usual lunch", Routines.parseSave("save that as my usual lunch"))
        assertEquals("My Morning", Routines.parseSave("Save this as My Morning"))
        assertEquals("lunch", Routines.parseSave("remember that as lunch"))
    }

    @Test
    fun parseSaveTakesTheNameAfterARoutineKeyword() {
        assertEquals("my usual lunch", Routines.parseSave("save it as a routine called my usual lunch"))
        assertEquals("lunch run", Routines.parseSave("save that routine as lunch run"))
    }

    @Test
    fun parseSaveIgnoresAnOrdinaryFileSave() {
        assertNull(Routines.parseSave("save this as draft.txt"))
        assertNull(Routines.parseSave("save the document as report.pdf"))
        assertNull(Routines.parseSave("turn the lights on"))
    }

    @Test
    fun parseSaveNeedsAnActualName() {
        assertNull(Routines.parseSave("save this as a routine"))
        assertNull(Routines.parseSave("save that as"))
    }

    @Test
    fun lastSavableSkipsImplicitAndRoutineCommands() {
        val tasks = listOf(
            task("1", "order my usual lunch", "typed", createdAt = 10),
            task("2", "save that as my usual lunch", Routines.SAVE_SOURCE, createdAt = 20),
            task("3", "my usual lunch", Routines.SOURCE, createdAt = 30),
            task("4", "Grok is using your phone", "assistant", createdAt = 40, implicit = true),
        )
        assertEquals("order my usual lunch", Routines.lastSavable(tasks)?.text)
    }

    @Test
    fun lastSavablePrefersTheNewestRealTask() {
        val tasks = listOf(
            task("1", "read my calendar", "typed", createdAt = 10),
            task("2", "text mom I'm on my way", "voice", createdAt = 50),
        )
        assertEquals("text mom I'm on my way", Routines.lastSavable(tasks)?.text)
    }

    @Test
    fun lastSavableIsNullWhenThereIsNothingToSave() {
        assertNull(Routines.lastSavable(emptyList()))
        assertNull(Routines.lastSavable(listOf(task("1", "", "typed", createdAt = 1))))
    }

    private fun task(id: String, text: String, source: String, createdAt: Long, implicit: Boolean = false) =
        TaskRecord(
            id = id,
            text = text,
            source = source,
            brain = Brains.PHONE,
            brainLabel = "On this phone",
            createdAt = createdAt,
            state = TaskState.Done,
            implicit = implicit,
        )
}
