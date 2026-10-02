package app.pony.companion.brain

import app.pony.companion.voice.ActionGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {
    @Test
    fun stepCapStopsAfterMaxSteps() {
        val executed = mutableListOf<String>()
        var rounds = 0
        val loop = AgentLoop(
            model = StepModel {
                rounds++
                ModelTurn(
                    calls = (1..AgentLoop.MAX_STEPS + 2).map { index ->
                        ToolCall("c$index", "tap", mapOf("x" to "$index", "y" to "2"))
                    },
                )
            },
            execute = { call ->
                executed += call.id
                "tapped"
            },
            guard = { Guard.Allow },
            confirm = { true },
        )
        val result = loop.run("tap around")
        assertEquals("capped", result.status)
        assertEquals(AgentLoop.MAX_STEPS, executed.size)
        assertEquals(1, rounds)
        assertTrue(result.message.contains(AgentLoop.MAX_STEPS.toString()))
    }

    @Test
    fun doneFinishesWithoutFurtherSteps() {
        var rounds = 0
        val loop = AgentLoop(
            model = StepModel {
                rounds++
                ModelTurn(calls = listOf(ToolCall("d", "done", mapOf("text" to "The door is locked."))))
            },
            execute = { error("should not run") },
            guard = { Guard.Allow },
            confirm = { true },
        )
        val result = loop.run("lock the door")
        assertEquals("done", result.status)
        assertEquals("The door is locked.", result.message)
        assertEquals(0, result.steps)
        assertEquals(1, rounds)
    }

    @Test
    fun answerOrFinishEndsTheTaskLikeDone() {
        for (verb in listOf("answer", "finish")) {
            var rounds = 0
            val loop = AgentLoop(
                model = StepModel {
                    rounds++
                    ModelTurn(calls = listOf(ToolCall("a", verb, mapOf("text" to "You ordered the spicy tuna rolls."))))
                },
                execute = { error("should not run") },
                guard = { Guard.Allow },
                confirm = { true },
            )
            val result = loop.run("what did I order")
            assertEquals("done", result.status)
            assertEquals("You ordered the spicy tuna rolls.", result.message)
            assertEquals(0, result.steps)
            assertEquals(1, rounds)
        }
    }

    @Test
    fun confirmationNoDoesNotExecuteAndStopHaltsTheLoop() {
        val executed = mutableListOf<String>()
        var rounds = 0
        val loop = AgentLoop(
            model = StepModel {
                rounds++
                if (rounds == 1) {
                    ModelTurn(calls = listOf(ToolCall("1", "tap", mapOf("label" to "Send"))))
                } else {
                    ModelTurn(calls = listOf(ToolCall("2", "tap", mapOf("x" to "1", "y" to "1"))))
                }
            },
            execute = { call ->
                executed += call.id
                "ok"
            },
            guard = { call -> ActionGuard.decide(call, call.args["label"].orEmpty(), passwordFocused = false) },
            confirm = { false },
            stopped = { rounds >= 1 && executed.isEmpty() && rounds > 1 },
        )
        val result = loop.run("send it")
        assertTrue(executed.isEmpty())
        assertEquals("stopped", result.status)
    }

    @Test
    fun passwordTypeIsRefusedBeforeExecute() {
        val executed = mutableListOf<String>()
        var rounds = 0
        val loop = AgentLoop(
            model = StepModel {
                rounds++
                if (rounds == 1) ModelTurn(calls = listOf(ToolCall("1", "type", mapOf("text" to "hunter2"))))
                else ModelTurn(text = "I stopped.")
            },
            execute = { call ->
                executed += call.name
                "typed"
            },
            guard = { call -> ActionGuard.decide(call, "", passwordFocused = true) },
            confirm = { true },
        )
        val result = loop.run("type the password")
        assertTrue(executed.isEmpty())
        assertEquals("done", result.status)
        assertEquals("I stopped.", result.message)
    }

    @Test
    fun lockedPhoneDoesNotCallTheModelUntilUnlockFails() {
        var called = false
        val loop = AgentLoop(
            model = StepModel {
                called = true
                ModelTurn(text = "nope")
            },
            execute = { "ok" },
            guard = { Guard.Allow },
            confirm = { true },
            locked = { true },
            waitForUnlock = { false },
        )
        val result = loop.run("open notes")
        assertFalse(called)
        assertEquals("locked", result.status)
        assertEquals(AgentLoop.UNLOCK_LINE, result.message)
    }

    @Test
    fun askPausesForTheOwnerThenCarriesOnWithTheirChoice() {
        val asked = mutableListOf<String>()
        var rounds = 0
        val loop = AgentLoop(
            model = StepModel { messages ->
                rounds++
                if (rounds == 1) {
                    ModelTurn(calls = listOf(ToolCall("q", "ask", mapOf("text" to "Which Ben — Ben Cohen or Ben Lee?"))))
                } else {
                    assertTrue(messages.any { it.text.contains("The owner said: Ben Lee") })
                    ModelTurn(calls = listOf(ToolCall("d", "done", mapOf("text" to "Texted Ben Lee."))))
                }
            },
            execute = { error("ask must not reach execute") },
            guard = { Guard.Allow },
            confirm = { true },
            ask = { question -> asked += question; "Ben Lee" },
        )
        val result = loop.run("text ben")
        assertEquals("done", result.status)
        assertEquals("Texted Ben Lee.", result.message)
        assertEquals("Which Ben — Ben Cohen or Ben Lee?", asked.single())
        assertEquals(1, result.steps)
    }

    @Test
    fun anUnansweredAskLetsTheLoopDecideWithoutGuessing() {
        var rounds = 0
        val loop = AgentLoop(
            model = StepModel { messages ->
                rounds++
                if (rounds == 1) {
                    ModelTurn(calls = listOf(ToolCall("q", "ask", mapOf("text" to "Which one?"))))
                } else {
                    assertTrue(messages.any { it.text.contains("The owner didn't answer.") })
                    ModelTurn(calls = listOf(ToolCall("d", "done", mapOf("text" to "I'll wait until you tell me which one."))))
                }
            },
            execute = { error("should not run") },
            guard = { Guard.Allow },
            confirm = { true },
            ask = { "" },
        )
        val result = loop.run("text ben")
        assertEquals("done", result.status)
        assertEquals(1, result.steps)
    }

    @Test
    fun theSystemPromptCarriesTheOwnerTestedGuidance() {
        val prompt = AgentLoop.SYSTEM.lowercase()
        // Pony's own UI is never the task (self-screenshot bug).
        assertTrue(prompt.contains("pony's own app is never the task"))
        assertTrue(prompt.contains("press home and look again"))
        // It acts on reachable settings instead of refusing.
        assertTrue(prompt.contains("change any system setting"))
        assertTrue(prompt.contains("never say you can't change a setting"))
        // Setup tasks jump straight to the screen with open_settings, else the search box.
        assertTrue(prompt.contains("open_settings"))
        assertTrue(prompt.contains("search box"))
        assertTrue(prompt.contains("voice input"))
        assertTrue(prompt.contains("don't open a messaging app"))
        // Plain answers, no markdown.
        assertTrue(prompt.contains("no markdown"))
        // Settings changes don't need a confirmation; only the risky verbs do.
        assertTrue(prompt.contains("needs no confirmation"))
        assertFalse(prompt.contains("changes security settings"))
        // An ambiguous target (several contacts named Ben) asks instead of guessing.
        assertTrue(prompt.contains("don't guess"))
        assertTrue(prompt.contains("ask tool"))
        // Worth-keeping facts are remembered; secrets never.
        assertTrue(prompt.contains("call remember"))
        assertTrue(prompt.contains("never remember a password"))
    }

    @Test
    fun aRememberedSummaryIsInjectedIntoTheSystemPrompt() {
        var systemSeen = ""
        val loop = AgentLoop(
            model = StepModel { messages ->
                systemSeen = messages.first { it.role == "system" }.text
                ModelTurn(calls = listOf(ToolCall("d", "done", mapOf("text" to "Done."))))
            },
            execute = { error("should not run") },
            guard = { Guard.Allow },
            confirm = { true },
            memory = "What you remember about the owner (use it; don't ask again): home city is Portland.",
        )
        loop.run("where do I live")
        assertTrue(systemSeen.startsWith(AgentLoop.SYSTEM))
        assertTrue(systemSeen.contains("home city is Portland"))
    }

    @Test
    fun noMemoryLeavesTheSystemPromptUntouched() {
        var systemSeen = ""
        val loop = AgentLoop(
            model = StepModel { messages ->
                systemSeen = messages.first { it.role == "system" }.text
                ModelTurn(calls = listOf(ToolCall("d", "done", mapOf("text" to "Done."))))
            },
            execute = { error("should not run") },
            guard = { Guard.Allow },
            confirm = { true },
        )
        loop.run("hi")
        assertEquals(AgentLoop.SYSTEM, systemSeen)
    }

    @Test
    fun aStalledScreenAddsABackAndSearchNudge() {
        val seen = mutableListOf<String>()
        var rounds = 0
        val loop = AgentLoop(
            model = StepModel { messages ->
                seen += messages.last().text
                rounds++
                if (rounds < 3) {
                    ModelTurn(calls = listOf(ToolCall("t$rounds", "tap", mapOf("x" to "1", "y" to "1"))))
                } else {
                    ModelTurn(calls = listOf(ToolCall("d", "done", mapOf("text" to "Done."))))
                }
            },
            execute = { "tapped" },
            guard = { Guard.Allow },
            confirm = { true },
            observe = { ScreenView("Settings\nAccount: Alex Rivera\nSearch") },
        )
        loop.run("set up hebrew")
        assertTrue(seen.any { it.contains(StallCheck.HINT) })
    }

    @Test
    fun aModelTimeoutIsRetriedWithBackoffThenSucceeds() {
        var calls = 0
        val waits = mutableListOf<Long>()
        val loop = AgentLoop(
            model = StepModel {
                calls++
                if (calls <= 2) throw java.net.SocketTimeoutException("timeout")
                ModelTurn(calls = listOf(ToolCall("d", "done", mapOf("text" to "Opened Settings. Battery is 82%."))))
            },
            execute = { error("should not run") },
            guard = { Guard.Allow },
            confirm = { true },
            sleep = { waits += it },
        )
        val result = loop.run("tell me the battery percentage")
        assertEquals("done", result.status)
        assertEquals("Opened Settings. Battery is 82%.", result.message)
        assertEquals(3, calls)
        // Two retries waited their growing backoff (600ms + 1200ms), sliced for a prompt Stop.
        assertEquals(BrainError.backoffMs(1) + BrainError.backoffMs(2), waits.sum())
        assertTrue(waits.all { it <= AgentLoop.BACKOFF_SLICE_MS })
    }

    @Test
    fun aPersistentTimeoutGivesUpWithPlainLanguageNotTheRawError() {
        var calls = 0
        val loop = AgentLoop(
            model = StepModel {
                calls++
                throw java.net.SocketTimeoutException("timeout")
            },
            execute = { error("should not run") },
            guard = { Guard.Allow },
            confirm = { true },
            sleep = {},
        )
        val result = loop.run("open settings and read the battery")
        assertEquals("error", result.status)
        assertEquals("The brain took too long to answer — try again.", result.message)
        // The first call plus MODEL_RETRIES retries.
        assertEquals(AgentLoop.MODEL_RETRIES + 1, calls)
    }

    @Test
    fun aRejectedKeyFailsImmediatelyWithoutRetrying() {
        var calls = 0
        val loop = AgentLoop(
            model = StepModel {
                calls++
                throw IllegalStateException("HTTP 401: key was rejected")
            },
            execute = { error("should not run") },
            guard = { Guard.Allow },
            confirm = { true },
            sleep = { error("a non-retryable error must not wait") },
        )
        val result = loop.run("do a thing")
        assertEquals("error", result.status)
        assertEquals("HTTP 401: key was rejected", result.message)
        assertEquals(1, calls)
    }

    @Test
    fun aStopDuringTheRetryBackoffEndsTheLoop() {
        var calls = 0
        var stop = false
        val loop = AgentLoop(
            model = StepModel {
                calls++
                throw java.net.SocketTimeoutException("timeout")
            },
            execute = { error("should not run") },
            guard = { Guard.Allow },
            confirm = { true },
            stopped = { stop },
            sleep = { stop = true },
        )
        val result = loop.run("open settings")
        assertEquals("stopped", result.status)
        assertEquals("Stopped.", result.message)
        // First call throws, the backoff flips Stop, so there's no second model call.
        assertEquals(1, calls)
    }
}

