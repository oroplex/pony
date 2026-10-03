package app.pony.companion.recap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoPlannerTest {
    private val draft = UndoableAction.ClearDraft(chars = 12, app = "com.example.chat")

    @Test
    fun treatsSentAndPaidActionsAsCommitting() {
        for (reason in listOf("send", "payment", "payment_app", "money_screen", "purchase", "transfer", "post", "delete", "call")) {
            assertTrue(reason, UndoPlanner.committedBy(reason))
        }
    }

    @Test
    fun leavesReversibleChangesUncommitted() {
        assertFalse(UndoPlanner.committedBy("security"))
        assertFalse(UndoPlanner.committedBy("password_field"))
        assertFalse(UndoPlanner.committedBy("settings"))
        assertFalse(UndoPlanner.committedBy(null))
        assertFalse(UndoPlanner.committedBy("tap"))
    }

    @Test
    fun offersTheLatestReversibleActionWhenNothingWasCommitted() {
        assertSame(draft, UndoPlanner.offer(draft, committed = false))
    }

    @Test
    fun neverOffersUndoOnceTheTaskCommitted() {
        assertNull(UndoPlanner.offer(draft, committed = true))
    }

    @Test
    fun offersNothingWhenThereIsNoReversibleAction() {
        assertNull(UndoPlanner.offer(null, committed = false))
    }

    @Test
    fun describesTheDraftUndo() {
        assertEquals("Clear the draft", draft.label)
        assertEquals("Clear the text Pony typed?", draft.confirm)
        assertEquals("Typed a draft of 12 characters", draft.summary)
        assertEquals("Typed a draft of 1 character", UndoableAction.ClearDraft(1, null).summary)
    }
}
