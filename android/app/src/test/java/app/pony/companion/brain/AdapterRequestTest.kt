package app.pony.companion.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterRequestTest {
    private val key = "sk-pony-PLAINTEXT-secret-9999"
    private val shot = "abcJPEG=="

    @Test
    fun openAiRequestHasToolsVisionAndNoKeyInTheBody() {
        val request = AdapterRequests.build(
            ProviderPreset.XAI,
            "grok-4.7",
            ProviderPreset.XAI.defaultBaseUrl,
            key,
            listOf(LoopMessage("user", "turn the lamp off", imageBase64 = shot)),
            includeTools = true,
        )
        assertEquals("https://api.x.ai/v1/chat/completions", request.url)
        assertEquals("Bearer $key", request.headers["Authorization"])
        assertTrue(request.body.contains("\"tools\""))
        assertTrue(request.body.contains("\"name\":\"tap\""))
        assertTrue(request.body.contains("\"name\":\"open_settings\""))
        assertTrue(request.body.contains("\"name\":\"long_press\""))
        assertTrue(request.body.contains("\"name\":\"drag\""))
        assertTrue(request.body.contains("\"name\":\"pinch\""))
        assertTrue(request.body.contains("\"name\":\"wait_idle\""))
        assertTrue(request.body.contains("\"name\":\"remember\""))
        assertTrue(request.body.contains("\"name\":\"forget\""))
        assertTrue(request.body.contains("\"name\":\"schedule_task\""))
        assertTrue(request.body.contains("\"name\":\"copy_text\""))
        assertTrue(request.body.contains("\"name\":\"recall_text\""))
        assertTrue(request.body.contains("image_url"))
        assertTrue(request.body.contains("data:image/jpeg;base64,$shot"))
        assertFalse(request.body.contains(key))
        assertFalse(request.url.contains(key))
        assertFalse(AdapterRequests.redact(request.headers, key).values.any { it.contains(key) })
    }

    @Test
    fun anthropicRequestUsesMessagesToolsAndImageBlocks() {
        val request = AdapterRequests.build(
            ProviderPreset.ANTHROPIC,
            "claude-sonnet-4-5",
            ProviderPreset.ANTHROPIC.defaultBaseUrl,
            key,
            listOf(
                LoopMessage("system", "be brief"),
                LoopMessage("user", "look", imageBase64 = shot),
            ),
            includeTools = true,
        )
        assertEquals("https://api.anthropic.com/v1/messages", request.url)
        assertEquals(key, request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])
        assertTrue(request.body.contains("\"input_schema\""))
        assertTrue(request.body.contains("\"type\":\"image\""))
        assertTrue(request.body.contains("\"media_type\":\"image/jpeg\""))
        assertTrue(request.body.contains(shot))
        assertFalse(request.body.contains("\"role\":\"system\""))
        assertTrue(request.body.contains("be brief"))
        assertFalse(request.body.contains(key))
        assertFalse(request.url.contains(key))
    }

    @Test
    fun geminiRequestUsesGenerateContentAndHeaderKey() {
        val request = AdapterRequests.build(
            ProviderPreset.GEMINI,
            "gemini-2.5-flash",
            ProviderPreset.GEMINI.defaultBaseUrl,
            key,
            listOf(LoopMessage("user", "look", imageBase64 = shot)),
            includeTools = true,
        )
        assertTrue(request.url.endsWith("/models/gemini-2.5-flash:generateContent"))
        assertEquals(key, request.headers["x-goog-api-key"])
        assertFalse(request.url.contains(key))
        assertFalse(request.body.contains(key))
        assertTrue(request.body.contains("functionDeclarations"))
        // Every 0.6 tool must reach Gemini through the one shared tools list, so
        // "ask Gemini" has the same reach as "ask Claude".
        listOf(
            "tap", "long_press", "drag", "pinch", "open_settings", "wait_idle",
            "remember", "forget", "schedule_task", "copy_text", "recall_text",
        ).forEach { tool ->
            assertTrue("Gemini is missing $tool", request.body.contains("\"name\":\"$tool\""))
        }
        assertTrue(request.body.contains("inlineData"))
        assertTrue(request.body.contains(shot))
        assertTrue(request.body.contains("systemInstruction"))
    }

    @Test
    fun parsesToolCallsFromEachAdapter() {
        val openAi = AdapterRequests.parse(
            ProviderKind.OPENAI_COMPAT,
            """{"choices":[{"message":{"content":"","tool_calls":[{"id":"a","type":"function","function":{"name":"tap","arguments":"{\"x\":\"10\",\"y\":\"20\"}"}}]}}]}""",
        )
        assertEquals("tap", openAi.calls.single().name)
        assertEquals("10", openAi.calls.single().args["x"])

        val claude = AdapterRequests.parse(
            ProviderKind.ANTHROPIC,
            """{"content":[{"type":"tool_use","id":"t1","name":"done","input":{"text":"Locked the door"}}]}""",
        )
        assertEquals("done", claude.calls.single().name)
        assertEquals("Locked the door", claude.calls.single().args["text"])

        val gemini = AdapterRequests.parse(
            ProviderKind.GEMINI,
            """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"key","args":{"key":"home"}}}]}}]}""",
        )
        assertEquals("home", gemini.calls.single().args["key"])
    }

    @Test
    fun openRouterUsesTheOpenAiShape() {
        val request = AdapterRequests.probe(
            ProviderPreset.OPENROUTER,
            "openai/gpt-4.1",
            ProviderPreset.OPENROUTER.defaultBaseUrl,
            key,
        )
        assertEquals("https://openrouter.ai/api/v1/chat/completions", request.url)
        assertTrue(request.headers.containsKey("HTTP-Referer"))
        assertFalse(request.body.contains("\"tools\""))
        assertFalse(request.body.contains(key))
    }

    @Test
    fun openAiBrainCarriesEveryToolAndHidesTheKey() {
        val request = AdapterRequests.build(
            ProviderPreset.OPENAI,
            ProviderPreset.OPENAI.defaultModel,
            ProviderPreset.OPENAI.defaultBaseUrl,
            key,
            listOf(LoopMessage("user", "open settings", imageBase64 = shot)),
            includeTools = true,
        )
        assertEquals("https://api.openai.com/v1/chat/completions", request.url)
        assertEquals("Bearer $key", request.headers["Authorization"])
        // OpenAI is an OpenAI-compatible brain, so it has the same reach as Grok.
        listOf(
            "tap", "swipe", "long_press", "drag", "pinch", "type", "key", "open_app",
            "open_settings", "wait_idle", "speak", "ask", "remember", "forget",
            "schedule_task", "copy_text", "recall_text", "done",
        ).forEach { tool ->
            assertTrue("OpenAI is missing $tool", request.body.contains("\"name\":\"$tool\""))
        }
        assertTrue(request.body.contains("data:image/jpeg;base64,$shot"))
        assertFalse(request.body.contains(key))
        assertFalse(request.url.contains(key))
        assertFalse(AdapterRequests.redact(request.headers, key).values.any { it.contains(key) })
    }

    @Test
    fun openAiAndXaiProbesAreCheapKeyChecks() {
        // Both are OpenAI-compatible, so Test & Save sends the same tiny, no-tools
        // request — just enough to prove the key works — to each provider's host.
        val openAi = AdapterRequests.probe(
            ProviderPreset.OPENAI,
            ProviderPreset.OPENAI.defaultModel,
            ProviderPreset.OPENAI.defaultBaseUrl,
            key,
        )
        assertEquals("https://api.openai.com/v1/chat/completions", openAi.url)
        assertEquals("Bearer $key", openAi.headers["Authorization"])
        assertFalse(openAi.headers.containsKey("HTTP-Referer"))
        assertTrue(openAi.body.contains("\"max_tokens\":16"))
        assertFalse(openAi.body.contains("\"tools\""))
        assertFalse(openAi.body.contains(key))

        val xai = AdapterRequests.probe(
            ProviderPreset.XAI,
            ProviderPreset.XAI.defaultModel,
            ProviderPreset.XAI.defaultBaseUrl,
            key,
        )
        assertEquals("https://api.x.ai/v1/chat/completions", xai.url)
        assertEquals("Bearer $key", xai.headers["Authorization"])
        assertFalse(xai.body.contains("\"tools\""))
        assertFalse(xai.body.contains(key))
    }

    @Test
    fun parsesAPlainTextReplyFromOpenAiWithNoToolCalls() {
        val turn = AdapterRequests.parse(
            ProviderKind.OPENAI_COMPAT,
            """{"choices":[{"message":{"content":"ok"}}]}""",
        )
        assertEquals("ok", turn.text)
        assertTrue(turn.calls.isEmpty())
    }
}
