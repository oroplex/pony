package app.pony.companion.brain

data class ToolCall(
    val id: String,
    val name: String,
    val args: Map<String, String>,
)

data class LoopMessage(
    val role: String,
    val text: String = "",
    val imageBase64: String? = null,
    val toolCallId: String? = null,
    val calls: List<ToolCall> = emptyList(),
)

data class ModelTurn(
    val text: String = "",
    val calls: List<ToolCall> = emptyList(),
)

data class AdapterRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

data class ScreenView(
    val tree: String,
    val imageBase64: String? = null,
)

data class LoopResult(
    val status: String,
    val message: String,
    val steps: Int,
)
