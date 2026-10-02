package app.pony.companion.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskTrackerTest {
    @Test
    fun aTaskRecordsWhatWasAskedEachStepAndTheOutcome() {
        var now = 1_000L
        val store = TaskStore(tempRoot())
        val tracker = TaskTracker(store, clock = { now++ }, ids = { "task-1" })
        tracker.begin("open the calculator and add 2 plus 2", "typed", Brains.BASICS, Brains.BASICS_LABEL)
        tracker.step(StepKind.Open, "Opened Calculator")
        tracker.step(StepKind.Tap, "Tapped “2”")
        tracker.step(StepKind.Tap, "Tapped “+”")
        tracker.step(StepKind.Look, "Looked at the screen", shot = byteArrayOf(1, 2, 3))
        tracker.finish(TaskState.Done, "2 + 2 = 4")

        val saved = TaskTracker(store).history.value.single()
        assertEquals("open the calculator and add 2 plus 2", saved.text)
        assertEquals(TaskState.Done, saved.state)
        assertEquals("2 + 2 = 4", saved.outcome)
        assertEquals(listOf("Opened Calculator", "Tapped “2”", "Tapped “+”", "Looked at the screen"), saved.steps.map { it.label })
        val shot = saved.steps.last().shot
        assertNotNull(shot)
        assertTrue(store.shotFile("task-1", shot!!).readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertNotNull(saved.endedAt)
    }

    @Test
    fun repeatedScreenReadsFoldIntoOneStep() {
        val tracker = TaskTracker(null)
        tracker.begin("look around", "typed", Brains.GROK, "Grok Bot")
        repeat(4) { tracker.step(StepKind.Read, "Read the screen") }
        tracker.step(StepKind.Tap, "Tapped “OK”")
        val steps = tracker.current()!!.steps
        assertEquals(2, steps.size)
        assertEquals(4, steps.first().count)
    }

    @Test
    fun finishedTasksStopAcceptingStepsAndBeginReplacesARunningTask() {
        val tracker = TaskTracker(null)
        val first = tracker.begin("first", "typed", Brains.GROK, "Grok Bot")
        val second = tracker.begin("second", "voice", Brains.PHONE, "Claude")
        assertEquals(TaskState.Stopped, tracker.find(first.id)!!.state)
        tracker.finish(TaskState.Done, "ok")
        assertNull(tracker.step(StepKind.Tap, "late tap"))
        assertEquals(TaskState.Done, tracker.find(second.id)!!.state)
        assertFalse(tracker.live.value!!.running)
    }

    @Test
    fun handingATaskToAnotherBrainKeepsItsHistory() {
        val tracker = TaskTracker(null)
        tracker.begin("set a timer", "typed", Brains.GROK, "Grok Bot", state = TaskState.Queued)
        tracker.step(StepKind.Wait, "Waiting for Grok Bot")
        assertEquals(TaskState.Queued, tracker.current()!!.state)
        tracker.reassign(Brains.PHONE, "Claude", "Claude on this phone is doing it")
        val task = tracker.current()!!
        assertEquals(Brains.PHONE, task.brain)
        assertEquals(TaskState.Running, task.state)
        assertEquals(1, task.steps.size)
    }

    @Test
    fun theStoreKeepsTheNewestTasksAndDeletesOldScreenshots() {
        val root = tempRoot()
        val store = TaskStore(root, keep = 2)
        var id = 0
        var now = 0L
        val tracker = TaskTracker(store, clock = { now += 10; now }, ids = { "t${++id}" })
        repeat(3) { index ->
            tracker.begin("task $index", "typed", Brains.GROK, "Grok Bot")
            tracker.step(StepKind.Look, "Looked", shot = byteArrayOf(9))
            tracker.finish(TaskState.Done, "done")
        }
        val kept = store.list().map { it.id }
        assertEquals(listOf("t3", "t2"), kept)
        assertFalse(File(root, "t1").exists())
        tracker.delete("t3")
        assertEquals(listOf("t2"), store.list().map { it.id })
        tracker.clear()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun recordsSurviveAJsonRoundTrip() {
        val record = TaskRecord(
            id = "x",
            text = "say \"hi\"\nthen stop",
            source = "voice",
            brain = Brains.GROK,
            brainLabel = "Grok Bot",
            createdAt = 5,
            state = TaskState.Stopped,
            steps = listOf(TaskStep(6, StepKind.Speak, "Said “hi”", ok = true, count = 2)),
            outcome = "Stopped by you",
            endedAt = 9,
            implicit = true,
        )
        assertEquals(record, TaskRecord.fromJson(record.toJson()))
    }

    @Test
    fun aRestartPutsAWaitingRequestBackInLineAndClosesTheRest() {
        val now = 1_000_000L
        val store = TaskStore(tempRoot())
        val tap = TaskStep(now - 3_000, StepKind.Tap, "Tapped “OK”")
        store.upsert(TaskRecord("a", "take the old route", "typed", Brains.GROK, "Grok Bot", now - 4_000, TaskState.Waiting))
        store.upsert(TaskRecord("b", "tap around", "assistant", Brains.GROK, "Grok Bot", now - 3_000, TaskState.Running, listOf(tap), implicit = true))
        store.upsert(TaskRecord("c", "open the calculator", "typed", Brains.BASICS, Brains.BASICS_LABEL, now - 2_000, TaskState.Running, listOf(tap)))
        store.upsert(TaskRecord("d", "what is on my calendar", "voice", Brains.GROK, "Grok Bot", now - 1_000, TaskState.Queued))

        val requeued = mutableListOf<String>()
        val after = TaskTracker(store, clock = { now + 60_000 })
        val live = after.recover(requeueWindowMs = 15 * 60_000L) { requeued += it.text; true }

        assertEquals(listOf("what is on my calendar"), requeued)
        assertEquals("what is on my calendar", live?.text)
        assertEquals(TaskState.Queued, after.current()?.state)
        val byText = after.history.value.associateBy { it.text }
        assertEquals(TaskState.Expired, byText.getValue("take the old route").state)
        assertEquals(TaskState.Done, byText.getValue("tap around").state)
        assertEquals(TaskState.Failed, byText.getValue("open the calculator").state)
        assertEquals("Pony restarted before this finished.", byText.getValue("open the calculator").outcome)
        assertTrue(after.history.value.none { it.running && it.text != "what is on my calendar" })
    }

    @Test
    fun aRequestTooOldToRequeueExpires() {
        var now = 0L
        val store = TaskStore(tempRoot())
        TaskTracker(store, clock = { now }).begin("old ask", "typed", Brains.GROK, "Grok Bot", TaskState.Waiting)
        now += 16 * 60_000L
        val after = TaskTracker(store, clock = { now })
        assertNull(after.recover(15 * 60_000L) { true })
        assertEquals(TaskState.Expired, after.history.value.single().state)
        assertNull(after.current())
    }

    @Test
    fun aResumableFinishMarksTheTaskAndStripsMarkdownFromTheResult() {
        val tracker = TaskTracker(null)
        tracker.begin("set up hebrew typing", "voice", Brains.PHONE, "Claude")
        tracker.step(StepKind.Tap, "Tapped “Languages and types”")
        tracker.finish(TaskState.Failed, "**What I tried:**\n- opened Settings", resumable = true)
        val task = tracker.find(tracker.history.value.first().id)!!
        assertTrue(task.resumable)
        assertEquals("What I tried:\nopened Settings", task.outcome)
    }

    @Test
    fun aPlainFinishIsNotResumable() {
        val tracker = TaskTracker(null)
        tracker.begin("open notes", "typed", Brains.PHONE, "Claude")
        tracker.finish(TaskState.Done, "Opened Notes.")
        assertFalse(tracker.history.value.first().resumable)
    }

    @Test
    fun aReadOnlyLookRecordsAStepButKeepsTheLastActionHeadline() {
        val tracker = TaskTracker(null)
        tracker.begin("tap around", "assistant", Brains.GROK, "Grok Bot", implicit = true)
        tracker.step(StepKind.Tap, "Tapped “Settings”")
        assertEquals("Tapped “Settings”", tracker.current()!!.headline)
        // A passive look is recorded, but the pill keeps showing the last real action.
        tracker.step(StepKind.Look, "Looked at the screen")
        val task = tracker.current()!!
        assertEquals("Tapped “Settings”", task.headline)
        assertEquals(listOf("Tapped “Settings”", "Looked at the screen"), task.steps.map { it.label })
    }

    @Test
    fun aDetachedSessionLandsInHistoryWithoutBecomingTheLiveTask() {
        var now = 1_000L
        val store = TaskStore(tempRoot())
        val tracker = TaskTracker(store, clock = { now++ }, ids = { "detached-1" })
        val session = tracker.beginDetached("Grok used your phone", Brains.GROK, "Grok Bot")
        // The owner sees no live card, and the Ask box stays free.
        assertNull(tracker.current())
        assertNull(tracker.live.value)
        tracker.stepOn(session.id, StepKind.Tap, "Tapped “Settings”")
        assertNull(tracker.current())
        val saved = TaskTracker(store).history.value.single()
        assertEquals("Grok used your phone", saved.text)
        assertTrue(saved.implicit)
        assertEquals(listOf("Tapped “Settings”"), saved.steps.map { it.label })
    }

    @Test
    fun aDetachedSessionDoesNotDisturbARunningOwnerTask() {
        var id = 0
        val tracker = TaskTracker(null, ids = { "id-${++id}" })
        val owner = tracker.begin("open the calculator", "typed", Brains.BASICS, Brains.BASICS_LABEL)
        val session = tracker.beginDetached("Grok used your phone", Brains.GROK, "Grok Bot")
        // The owner's brain task stays live and keeps its own steps.
        assertEquals(owner.id, tracker.current()!!.id)
        tracker.stepOn(session.id, StepKind.Tap, "Tapped “Wi‑Fi”")
        tracker.step(StepKind.Open, "Opened Calculator")
        val live = tracker.current()!!
        assertEquals(owner.id, live.id)
        assertEquals(listOf("Opened Calculator"), live.steps.map { it.label })
        assertEquals(listOf("Tapped “Wi‑Fi”"), tracker.find(session.id)!!.steps.map { it.label })
    }

    @Test
    fun stepsOnADetachedSessionFoldAndKeepTheLastActionHeadline() {
        var now = 0L
        val tracker = TaskTracker(null, clock = { now += 10; now }, ids = { "detached-2" })
        val session = tracker.beginDetached("Grok used your phone", Brains.GROK, "Grok Bot")
        tracker.stepOn(session.id, StepKind.Tap, "Tapped “Settings”")
        repeat(3) { tracker.stepOn(session.id, StepKind.Read, "Read the screen") }
        val task = tracker.find(session.id)!!
        assertEquals(listOf("Tapped “Settings”", "Read the screen"), task.steps.map { it.label })
        assertEquals(3, task.steps.last().count)
        assertEquals("Tapped “Settings”", task.headline)
    }

    @Test
    fun finishingADetachedSessionLeavesTheLiveOwnerTaskAlone() {
        var id = 0
        val tracker = TaskTracker(null, ids = { "id-${++id}" })
        val owner = tracker.begin("look around", "typed", Brains.GROK, "Grok Bot")
        val session = tracker.beginDetached("Grok used your phone", Brains.GROK, "Grok Bot")
        tracker.finishDetached(session.id, TaskState.Done, "**Done** tidying up")
        val finished = tracker.find(session.id)!!
        assertEquals(TaskState.Done, finished.state)
        assertEquals("Done tidying up", finished.outcome)
        assertNotNull(finished.endedAt)
        // The owner's task is still live and untouched.
        assertEquals(owner.id, tracker.current()!!.id)
        assertTrue(tracker.current()!!.running)
        assertNull(tracker.stepOn(session.id, StepKind.Tap, "late tap"))
    }

    private fun tempRoot(): File = File.createTempFile("pony-tasks", "").apply {
        delete()
        mkdirs()
    }
}
