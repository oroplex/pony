package app.pony.companion.brain

import java.net.URLEncoder

/**
 * Request and response shapes for the three native adapters.
 * The API key is a header, never a body or URL field.
 */
object AdapterRequests {
    val tools: List<ToolSpec> = listOf(
        ToolSpec(
            "tap",
            "Tap a point on the screen. Coordinates are pixels from the top left.",
            listOf("x", "y"),
            listOf(Prop("x", "number", "Pixels from the left"), Prop("y", "number", "Pixels from the top")),
        ),
        ToolSpec(
            "swipe",
            "Swipe from one point to another.",
            listOf("x1", "y1", "x2", "y2"),
            listOf(
                Prop("x1", "number", "Start x"),
                Prop("y1", "number", "Start y"),
                Prop("x2", "number", "End x"),
                Prop("y2", "number", "End y"),
                Prop("durationMs", "number", "Duration in milliseconds"),
            ),
        ),
        ToolSpec(
            "long_press",
            "Press and hold a point, for context menus, selecting text, or picking up an item to drag.",
            listOf("x", "y"),
            listOf(
                Prop("x", "number", "Pixels from the left"),
                Prop("y", "number", "Pixels from the top"),
                Prop("durationMs", "number", "How long to hold in milliseconds (default 600)"),
            ),
        ),
        ToolSpec(
            "drag",
            "Press at the start, move to the end, and release as one slow gesture — to reorder a list item or move something, not a quick flick (use swipe for scrolling).",
            listOf("x1", "y1", "x2", "y2"),
            listOf(
                Prop("x1", "number", "Start x"),
                Prop("y1", "number", "Start y"),
                Prop("x2", "number", "End x"),
                Prop("y2", "number", "End y"),
                Prop("durationMs", "number", "Duration in milliseconds (default 600)"),
            ),
        ),
        ToolSpec(
            "pinch",
            "Pinch two fingers around a point to zoom. fromDistance and toDistance are how far apart the fingers are in pixels; grow them to zoom in, shrink them to zoom out.",
            listOf("x", "y", "fromDistance", "toDistance"),
            listOf(
                Prop("x", "number", "Center x"),
                Prop("y", "number", "Center y"),
                Prop("fromDistance", "number", "Starting gap between the fingers, in pixels"),
                Prop("toDistance", "number", "Ending gap between the fingers, in pixels"),
                Prop("durationMs", "number", "Duration in milliseconds (default 300)"),
            ),
        ),
        ToolSpec(
            "type",
            "Type into the focused field. By default it replaces the field's text (overwrites what's there, so no need to clear first). Pass mode \"append\" to add to the end instead. Password fields are refused.",
            listOf("text"),
            listOf(
                Prop("text", "string", "The text to type"),
                Prop("mode", "string", "replace (default) overwrites the field, append adds to the end"),
            ),
        ),
        ToolSpec(
            "key",
            "Press a key: a navigation key, or enter/search to submit the focused field.",
            listOf("key"),
            listOf(Prop("key", "string", "back, home, recents, enter, or search")),
        ),
        ToolSpec(
            "open_app",
            "Open an installed app by package name.",
            listOf("package"),
            listOf(Prop("package", "string", "Android package name")),
        ),
        ToolSpec(
            "open_settings",
            "Jump straight to a settings screen instead of tapping through menus that swallow touches. name is a screen like \"input_method\" (the keyboard list), \"keyboard_settings\" (the current keyboard's own options, where languages and voice typing live), \"languages\", \"voice_input\", \"accessibility\", \"display\", \"sound\", \"wifi\", \"bluetooth\", \"location\", \"battery\", \"security\", or \"app_details\" (pass package). Use this for setup tasks so taps land on the real screen.",
            listOf("name"),
            listOf(
                Prop("name", "string", "The settings screen, e.g. input_method, keyboard_settings, languages, voice_input, app_details"),
                Prop("package", "string", "For app_details: the app's package name"),
            ),
        ),
        ToolSpec(
            "wait_idle",
            "Wait for the screen to stop changing — a page to finish loading or an animation to end — instead of guessing. Returns settled and waitedMs. Use it after opening an app or tapping something that takes a moment.",
            emptyList(),
            listOf(Prop("timeoutMs", "number", "How long to wait at most, in milliseconds (default 4000)")),
        ),
        ToolSpec(
            "speak",
            "Say a short line out loud.",
            listOf("text"),
            listOf(Prop("text", "string", "What Pony should say")),
        ),
        ToolSpec(
            "ask",
            "Ask the owner a question and get their spoken answer back. Use this when you need them to choose or clarify — like which contact they meant when a name matches several people — instead of guessing.",
            listOf("text"),
            listOf(Prop("text", "string", "The question to ask the owner")),
        ),
        ToolSpec(
            "remember",
            "Keep a fact or preference the owner told you — their name, home city, a usual order — so you don't ask again next time. Never remember a password, a one-time code, a PIN, or a card number.",
            listOf("key", "value"),
            listOf(
                Prop("key", "string", "A short label, e.g. home_city, coffee_order, name"),
                Prop("value", "string", "What to remember for that label"),
            ),
        ),
        ToolSpec(
            "forget",
            "Drop something you remembered earlier, by its key.",
            listOf("key"),
            listOf(Prop("key", "string", "The label to forget, e.g. coffee_order")),
        ),
        ToolSpec(
            "schedule_task",
            "Set something to run later, once or on a repeat. Give the time in plain words and the task separately. Time can be \"every weekday at 8am\", \"every morning at 7\", \"on Mondays at 7\", \"tonight at 9\", or \"tomorrow at 6pm\". When it fires Pony runs the task on its own, so phrase the task as an instruction to carry out, like a fresh request.",
            listOf("task", "time"),
            listOf(
                Prop("task", "string", "What to do when it fires, e.g. read my calendar and tell me what's on it"),
                Prop("time", "string", "When to run it, in plain words, e.g. every weekday at 8am, tonight at 9, tomorrow at 6pm"),
            ),
        ),
        ToolSpec(
            "copy_text",
            "Stash a value you'll need in another app — a confirmation number, address, price, or name you can see now. Give it a short label, then recall_text it after you switch apps. It's scratch memory for this task, not a saved fact; never copy a password or one-time code.",
            listOf("label", "text"),
            listOf(
                Prop("label", "string", "A short label to find it by, e.g. order_number, address"),
                Prop("text", "string", "The exact text to carry over"),
            ),
        ),
        ToolSpec(
            "recall_text",
            "Read back something you stashed earlier with copy_text, by its label — so you can type or paste it in the app you're in now.",
            listOf("label"),
            listOf(Prop("label", "string", "The label you copied it under, e.g. order_number")),
        ),
        ToolSpec(
            "done",
            "Finish the task and tell the owner the result.",
            listOf("text"),
            listOf(Prop("text", "string", "Spoken result")),
        ),
    )

    fun build(
        preset: ProviderPreset,
        model: String,
        baseUrl: String,
        apiKey: String,
        messages: List<LoopMessage>,
        includeTools: Boolean,
    ): AdapterRequest {
        val base = baseUrl.trim().trimEnd('/')
        return when (preset.kind) {
            ProviderKind.OPENAI_COMPAT -> openAi(preset, model, base, apiKey, messages, includeTools)
            ProviderKind.ANTHROPIC -> anthropic(model, base, apiKey, messages, includeTools)
            ProviderKind.GEMINI -> gemini(model, base, apiKey, messages, includeTools)
        }
    }

    fun probe(preset: ProviderPreset, model: String, baseUrl: String, apiKey: String): AdapterRequest =
        build(
            preset,
            model,
            baseUrl,
            apiKey,
            listOf(LoopMessage("user", "Reply with the word ok.")),
            includeTools = false,
        )

    fun parse(kind: ProviderKind, raw: String): ModelTurn {
        if (raw.isBlank()) return ModelTurn()
        val root = JsonValue.parse(raw)
        val turn = when (kind) {
            ProviderKind.OPENAI_COMPAT -> parseOpenAi(root)
            ProviderKind.ANTHROPIC -> parseAnthropic(root)
            ProviderKind.GEMINI -> parseGemini(root)
        }
        if (turn.calls.isNotEmpty()) return turn
        return fallbackAction(turn.text) ?: turn
    }

    fun redact(headers: Map<String, String>, apiKey: String): Map<String, String> =
        headers.mapValues { (name, value) ->
            if (name.equals("Authorization", true) ||
                name.equals("x-api-key", true) ||
                name.equals("x-goog-api-key", true)
            ) {
                "••••"
            } else {
                value.replace(apiKey, "••••")
            }
        }

    fun sanitize(text: String, apiKey: String): String {
        if (apiKey.isBlank()) return text
        return text.replace(apiKey, "••••")
    }

    private fun openAi(
        preset: ProviderPreset,
        model: String,
        base: String,
        apiKey: String,
        messages: List<LoopMessage>,
        includeTools: Boolean,
    ): AdapterRequest {
        val headers = linkedMapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json",
        )
        if (preset == ProviderPreset.OPENROUTER) {
            headers["HTTP-Referer"] = "https://pony.karlmagendavid.com"
            headers["X-Title"] = "Pony"
        }
        val body = linkedMapOf<String, JsonValue>(
            "model" to JsonValue.str(model),
            "messages" to JsonValue.arr(messages.map { openAiMessage(it) }),
        )
        if (!includeTools) body["max_tokens"] = JsonValue.num(16)
        if (includeTools) body["tools"] = JsonValue.arr(tools.map { openAiTool(it) })
        return AdapterRequest(
            url = "$base/chat/completions",
            headers = headers,
            body = JsonValue.Obj(body.toList()).encode(),
        )
    }

    private fun openAiMessage(message: LoopMessage): JsonValue {
        if (message.role == "tool") {
            return JsonValue.obj(
                "role" to JsonValue.str("tool"),
                "tool_call_id" to JsonValue.str(message.toolCallId ?: ""),
                "content" to JsonValue.str(message.text),
            )
        }
        if (message.role == "assistant" && message.calls.isNotEmpty()) {
            return JsonValue.obj(
                "role" to JsonValue.str("assistant"),
                "content" to JsonValue.str(message.text),
                "tool_calls" to JsonValue.arr(message.calls.map { call ->
                    JsonValue.obj(
                        "id" to JsonValue.str(call.id),
                        "type" to JsonValue.str("function"),
                        "function" to JsonValue.obj(
                            "name" to JsonValue.str(call.name),
                            "arguments" to JsonValue.str(argsJson(call.args)),
                        ),
                    )
                }),
            )
        }
        val content: JsonValue = if (message.imageBase64 != null) {
            JsonValue.arr(
                listOf(
                    JsonValue.obj("type" to JsonValue.str("text"), "text" to JsonValue.str(message.text)),
                    JsonValue.obj(
                        "type" to JsonValue.str("image_url"),
                        "image_url" to JsonValue.obj(
                            "url" to JsonValue.str("data:image/jpeg;base64,${message.imageBase64}"),
                        ),
                    ),
                ),
            )
        } else {
            JsonValue.str(message.text)
        }
        return JsonValue.obj("role" to JsonValue.str(message.role), "content" to content)
    }

    private fun openAiTool(tool: ToolSpec): JsonValue = JsonValue.obj(
        "type" to JsonValue.str("function"),
        "function" to JsonValue.obj(
            "name" to JsonValue.str(tool.name),
            "description" to JsonValue.str(tool.description),
            "parameters" to schema(tool),
        ),
    )

    private fun anthropic(
        model: String,
        base: String,
        apiKey: String,
        messages: List<LoopMessage>,
        includeTools: Boolean,
    ): AdapterRequest {
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.text }
        val chat = messages.filter { it.role != "system" }
        val body = linkedMapOf(
            "model" to JsonValue.str(model),
            "max_tokens" to JsonValue.num(if (includeTools) 1024 else 16),
            "system" to JsonValue.str(system),
            "messages" to JsonValue.arr(anthropicMessages(chat)),
        )
        if (includeTools) body["tools"] = JsonValue.arr(tools.map { anthropicTool(it) })
        return AdapterRequest(
            url = "$base/v1/messages",
            headers = linkedMapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
                "Content-Type" to "application/json",
            ),
            body = JsonValue.Obj(body.toList()).encode(),
        )
    }

    private fun anthropicMessages(messages: List<LoopMessage>): List<JsonValue> {
        val out = mutableListOf<JsonValue>()
        messages.forEach { message ->
            when {
                message.role == "tool" -> out += JsonValue.obj(
                    "role" to JsonValue.str("user"),
                    "content" to JsonValue.arr(
                        listOf(
                            JsonValue.obj(
                                "type" to JsonValue.str("tool_result"),
                                "tool_use_id" to JsonValue.str(message.toolCallId ?: ""),
                                "content" to JsonValue.str(message.text),
                            ),
                        ),
                    ),
                )
                message.role == "assistant" && message.calls.isNotEmpty() -> {
                    val blocks = mutableListOf<JsonValue>()
                    if (message.text.isNotBlank()) {
                        blocks += JsonValue.obj("type" to JsonValue.str("text"), "text" to JsonValue.str(message.text))
                    }
                    message.calls.forEach { call ->
                        blocks += JsonValue.obj(
                            "type" to JsonValue.str("tool_use"),
                            "id" to JsonValue.str(call.id),
                            "name" to JsonValue.str(call.name),
                            "input" to argsObject(call.args),
                        )
                    }
                    out += JsonValue.obj("role" to JsonValue.str("assistant"), "content" to JsonValue.arr(blocks))
                }
                else -> {
                    val blocks = mutableListOf<JsonValue>()
                    blocks += JsonValue.obj("type" to JsonValue.str("text"), "text" to JsonValue.str(message.text))
                    if (message.imageBase64 != null) {
                        blocks += JsonValue.obj(
                            "type" to JsonValue.str("image"),
                            "source" to JsonValue.obj(
                                "type" to JsonValue.str("base64"),
                                "media_type" to JsonValue.str("image/jpeg"),
                                "data" to JsonValue.str(message.imageBase64),
                            ),
                        )
                    }
                    out += JsonValue.obj("role" to JsonValue.str(message.role), "content" to JsonValue.arr(blocks))
                }
            }
        }
        return out
    }

    private fun anthropicTool(tool: ToolSpec): JsonValue = JsonValue.obj(
        "name" to JsonValue.str(tool.name),
        "description" to JsonValue.str(tool.description),
        "input_schema" to schema(tool),
    )

    private fun gemini(
        model: String,
        base: String,
        apiKey: String,
        messages: List<LoopMessage>,
        includeTools: Boolean,
    ): AdapterRequest {
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.text }
        val contents = messages.filter { it.role != "system" }.map { geminiContent(it) }
        val encodedModel = URLEncoder.encode(model, "UTF-8")
        val body = linkedMapOf(
            "contents" to JsonValue.arr(contents),
            "systemInstruction" to JsonValue.obj(
                "parts" to JsonValue.arr(listOf(JsonValue.obj("text" to JsonValue.str(system)))),
            ),
        )
        if (includeTools) {
            body["tools"] = JsonValue.arr(
                listOf(
                    JsonValue.obj(
                        "functionDeclarations" to JsonValue.arr(tools.map { geminiTool(it) }),
                    ),
                ),
            )
        } else {
            body["generationConfig"] = JsonValue.obj("maxOutputTokens" to JsonValue.num(16))
        }
        return AdapterRequest(
            url = "$base/models/$encodedModel:generateContent",
            headers = linkedMapOf(
                "x-goog-api-key" to apiKey,
                "Content-Type" to "application/json",
            ),
            body = JsonValue.Obj(body.toList()).encode(),
        )
    }

    private fun geminiContent(message: LoopMessage): JsonValue {
        val role = if (message.role == "assistant") "model" else "user"
        val parts = mutableListOf<JsonValue>()
        if (message.role == "tool") {
            parts += JsonValue.obj(
                "functionResponse" to JsonValue.obj(
                    "name" to JsonValue.str(message.toolCallId ?: "tool"),
                    "response" to JsonValue.obj("output" to JsonValue.str(message.text)),
                ),
            )
        } else {
            if (message.text.isNotBlank()) parts += JsonValue.obj("text" to JsonValue.str(message.text))
            if (message.imageBase64 != null) {
                parts += JsonValue.obj(
                    "inlineData" to JsonValue.obj(
                        "mimeType" to JsonValue.str("image/jpeg"),
                        "data" to JsonValue.str(message.imageBase64),
                    ),
                )
            }
            message.calls.forEach { call ->
                parts += JsonValue.obj(
                    "functionCall" to JsonValue.obj(
                        "name" to JsonValue.str(call.name),
                        "args" to argsObject(call.args),
                    ),
                )
            }
        }
        if (parts.isEmpty()) parts += JsonValue.obj("text" to JsonValue.str(""))
        return JsonValue.obj("role" to JsonValue.str(role), "parts" to JsonValue.arr(parts))
    }

    private fun geminiTool(tool: ToolSpec): JsonValue = JsonValue.obj(
        "name" to JsonValue.str(tool.name),
        "description" to JsonValue.str(tool.description),
        "parameters" to schema(tool),
    )

    private fun schema(tool: ToolSpec): JsonValue = JsonValue.obj(
        "type" to JsonValue.str("object"),
        "properties" to JsonValue.Obj(tool.props.map { prop ->
            prop.name to JsonValue.obj(
                "type" to JsonValue.str(prop.type),
                "description" to JsonValue.str(prop.description),
            )
        }),
        "required" to JsonValue.arr(tool.required.map { JsonValue.str(it) }),
    )

    private fun argsObject(args: Map<String, String>): JsonValue =
        JsonValue.Obj(args.map { (key, value) -> key to JsonValue.str(value) })

    private fun argsJson(args: Map<String, String>): String = argsObject(args).encode()

    private fun parseOpenAi(root: JsonValue): ModelTurn {
        val message = root.obj("choices")?.arrOrNull()?.firstOrNull()
            ?.obj("message") ?: return ModelTurn()
        val text = message.obj("content")?.asString().orEmpty()
        val calls = message.obj("tool_calls")?.arrOrNull().orEmpty().mapNotNull { item ->
            val fn = item.obj("function") ?: return@mapNotNull null
            val argsRaw = fn.obj("arguments")?.asString().orEmpty()
            val args = runCatching { objectArgs(JsonValue.parse(argsRaw)) }.getOrDefault(emptyMap())
            ToolCall(
                id = item.obj("id")?.asString().orEmpty().ifBlank { "call" },
                name = fn.obj("name")?.asString().orEmpty(),
                args = args,
            )
        }.filter { it.name.isNotBlank() }
        return ModelTurn(text = text, calls = calls)
    }

    private fun parseAnthropic(root: JsonValue): ModelTurn {
        val blocks = root.obj("content")?.arrOrNull().orEmpty()
        val text = blocks.mapNotNull { block ->
            if (block.obj("type")?.asString() == "text") block.obj("text")?.asString() else null
        }.joinToString("\n")
        val calls = blocks.mapNotNull { block ->
            if (block.obj("type")?.asString() != "tool_use") return@mapNotNull null
            ToolCall(
                id = block.obj("id")?.asString().orEmpty().ifBlank { "tool" },
                name = block.obj("name")?.asString().orEmpty(),
                args = block.obj("input")?.let { objectArgs(it) } ?: emptyMap(),
            )
        }.filter { it.name.isNotBlank() }
        return ModelTurn(text = text, calls = calls)
    }

    private fun parseGemini(root: JsonValue): ModelTurn {
        val parts = root.obj("candidates")?.arrOrNull()?.firstOrNull()
            ?.obj("content")?.obj("parts")?.arrOrNull().orEmpty()
        val text = parts.mapNotNull { it.obj("text")?.asString() }.joinToString("\n")
        val calls = parts.mapNotNull { part ->
            val call = part.obj("functionCall") ?: return@mapNotNull null
            ToolCall(
                id = call.obj("name")?.asString().orEmpty(),
                name = call.obj("name")?.asString().orEmpty(),
                args = call.obj("args")?.let { objectArgs(it) } ?: emptyMap(),
            )
        }.filter { it.name.isNotBlank() }
        return ModelTurn(text = text, calls = calls)
    }

    private fun fallbackAction(text: String): ModelTurn? {
        val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        if (!trimmed.startsWith("{")) return null
        val obj = runCatching { JsonValue.parse(trimmed) as? JsonValue.Obj }.getOrNull() ?: return null
        val name = obj.get("action")?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val args = objectArgs(obj).filterKeys { it != "action" }
        return ModelTurn(text = "", calls = listOf(ToolCall(id = "fallback", name = name, args = args)))
    }

    private fun objectArgs(value: JsonValue): Map<String, String> {
        val obj = value as? JsonValue.Obj ?: return emptyMap()
        return obj.fields.associate { it.first to it.second.asString() }
    }

    private fun JsonValue.obj(name: String): JsonValue? = (this as? JsonValue.Obj)?.get(name)

    private fun JsonValue.arrOrNull(): List<JsonValue>? = (this as? JsonValue.Arr)?.items
}

data class ToolSpec(
    val name: String,
    val description: String,
    val required: List<String>,
    val props: List<Prop>,
)

data class Prop(val name: String, val type: String, val description: String)
