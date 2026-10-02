package app.pony.companion.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class RequestInboxTest {
    @Test
    fun aRequestWithNoAssistantWaitingStaysQueuedPastTwentySeconds() {
        var now = 1_000_000L
        val inbox = RequestInbox(clock = { now })
        val request = inbox.submit("open the calculator and add 2 plus 2", "typed")
        now += 23_000
        assertTrue(inbox.isQueued(request.id))
        now += 5 * 60_000
        assertTrue(inbox.isQueued(request.id))
        val taken = inbox.awaitNext(1_000)
        assertEquals(request.id, taken?.id)
        assertEquals("typed", taken?.source)
        assertFalse(inbox.isQueued(request.id))
    }

    @Test
    fun expiredRequestsAreNotDelivered() {
        var now = 0L
        val inbox = RequestInbox(clock = { now }, ttlMs = 10_000)
        inbox.submit("old", "typed")
        now += 10_001
        val fresh = inbox.submit("new", "voice")
        assertEquals(fresh.id, inbox.awaitNext(10)?.id)
        assertNull(inbox.awaitNext(10))
    }

    @Test
    fun requestsAreDeliveredInOrderAndCanBeTakenBack() {
        val inbox = RequestInbox()
        val first = inbox.submit("one", "typed")
        val second = inbox.submit("two", "typed")
        val third = inbox.submit("three", "typed")
        assertEquals(second.id, inbox.take(second.id)?.id)
        assertNull(inbox.take(second.id))
        assertEquals(first.id, inbox.awaitNext(10)?.id)
        assertTrue(inbox.cancel(third.id))
        assertNull(inbox.awaitNext(10))
    }

    @Test
    fun aWaitingAssistantCountsAsListeningAndGetsTheRequestAtOnce() {
        val inbox = RequestInbox()
        val got = AtomicReference<OwnerRequest?>()
        val started = CountDownLatch(1)
        val done = CountDownLatch(1)
        val waiter = Thread {
            started.countDown()
            got.set(inbox.awaitNext(10_000))
            done.countDown()
        }
        waiter.start()
        started.await()
        val deadline = System.currentTimeMillis() + 2_000
        while (inbox.state.value.waiters == 0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertTrue(inbox.listening())
        val begun = System.currentTimeMillis()
        val request = inbox.submit("set a timer for 1 minute", "typed")
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertTrue(System.currentTimeMillis() - begun < 1_500)
        assertEquals(request.id, got.get()?.id)
    }

    @Test
    fun listeningLastsForTheGraceWindowAfterAWaitEnds() {
        var now = 50_000L
        val inbox = RequestInbox(clock = { now }, listenGraceMs = 90_000)
        assertFalse(inbox.listening())
        assertNull(inbox.awaitNext(0))
        assertTrue(inbox.listening())
        now += 89_000
        assertTrue(inbox.listening())
        now += 2_000
        assertFalse(inbox.listening())
    }

    @Test
    fun forgettingTheListenerKeepsQueuedRequests() {
        val inbox = RequestInbox()
        assertNull(inbox.awaitNext(0))
        assertTrue(inbox.listening())
        val held = inbox.submit("hold me", "typed")
        inbox.forgetListener()
        assertFalse(inbox.listening())
        assertTrue(inbox.isQueued(held.id))
    }

    @Test
    fun deliveryListenersHearWhichRequestWasTaken() {
        val inbox = RequestInbox()
        val heard = mutableListOf<String>()
        inbox.onDelivered { heard += it.id }
        val request = inbox.submit("hello", "voice")
        inbox.awaitNext(10)
        assertEquals(listOf(request.id), heard)
    }

    @Test
    fun aWaitFromADroppedLinkLeavesTheRequestForTheNextOne() {
        val inbox = RequestInbox()
        var link = 1
        val started = CountDownLatch(1)
        val result = AtomicReference<OwnerRequest?>()
        val waiter = Thread {
            started.countDown()
            val epoch = link
            result.set(inbox.awaitNext(5_000) { link != epoch })
        }
        waiter.start()
        started.await(1, TimeUnit.SECONDS)
        Thread.sleep(50)
        link = 2
        inbox.wake()
        waiter.join(2_000)
        assertNull(result.get())
        val request = inbox.submit("set a timer", "voice")
        assertEquals(request.id, inbox.awaitNext(10)?.id)
    }

    @Test
    fun aRequestPutBackGoesOutFirstWithoutAnotherDeliveryEvent() {
        val inbox = RequestInbox()
        val heard = mutableListOf<String>()
        inbox.onDelivered { heard += it.id }
        val first = inbox.submit("first", "typed")
        val second = inbox.submit("second", "typed")
        val taken = inbox.awaitNext(10)!!
        inbox.putBack(taken)
        inbox.putBack(taken)
        assertEquals(listOf(first.id, second.id), inbox.queued().map { it.id })
        assertEquals(first.id, inbox.awaitNext(10)?.id)
        assertEquals(listOf(first.id, first.id), heard)
    }
}
