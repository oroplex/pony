package app.pony.companion.recap

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UndoLogTest {
    private val draft = UndoableAction.ClearDraft(10, "com.example.chat")
    private val later = UndoableAction.ClearDraft(25, "com.example.mail")

    @After
    fun tearDown() = UndoLog.clear()

    @Test
    fun offersWhatItRecordedForTheCurrentTask() {
        UndoLog.start("t1")
        UndoLog.record("t1", draft)
        assertEquals(draft, UndoLog.offer("t1"))
    }

    @Test
    fun ignoresRecordsAndOffersForOtherTasks() {
        UndoLog.start("t1")
        UndoLog.record("t2", draft)
        assertNull(UndoLog.offer("t1"))
        assertNull(UndoLog.offer("t2"))
    }

    @Test
    fun keepsOnlyTheLatestReversibleAction() {
        UndoLog.start("t1")
        UndoLog.record("t1", draft)
        UndoLog.record("t1", later)
        assertEquals(later, UndoLog.offer("t1"))
    }

    @Test
    fun committingWithholdsUndoAndIgnoresLaterRecords() {
        UndoLog.start("t1")
        UndoLog.record("t1", draft)
        UndoLog.commit("t1")
        assertNull(UndoLog.offer("t1"))
        UndoLog.record("t1", later)
        assertNull(UndoLog.offer("t1"))
    }

    @Test
    fun aNewTaskStartsCleanEvenAfterACommit() {
        UndoLog.start("t1")
        UndoLog.commit("t1")
        UndoLog.start("t2")
        UndoLog.record("t2", draft)
        assertEquals(draft, UndoLog.offer("t2"))
    }

    @Test
    fun consumingClearsTheOffer() {
        UndoLog.start("t1")
        UndoLog.record("t1", draft)
        UndoLog.consume("t1")
        assertNull(UndoLog.offer("t1"))
    }
}
