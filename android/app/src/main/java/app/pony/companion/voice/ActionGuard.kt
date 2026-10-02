package app.pony.companion.voice

import app.pony.companion.brain.Guard
import app.pony.companion.brain.ToolCall

object ActionGuard {
    fun decide(call: ToolCall, label: String, passwordFocused: Boolean): Guard {
        if (call.name == "type" && passwordFocused) return Guard.Refuse("password_field")
        if (call.name == "done" || call.name == "speak" || call.name == "key" || call.name == "swipe") {
            return Guard.Allow
        }
        val visible = label.ifBlank { call.args["label"].orEmpty() }
        val prompt = VoiceRisk.promptFor(call.name, visible)
        return if (prompt != null) Guard.Confirm(prompt) else Guard.Allow
    }
}
