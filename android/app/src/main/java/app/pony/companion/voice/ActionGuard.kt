package app.pony.companion.voice

import app.pony.companion.brain.Guard
import app.pony.companion.brain.ToolCall

object ActionGuard {
    fun decide(call: ToolCall, label: String, passwordFocused: Boolean, target: TapTarget? = null): Guard {
        if (call.name == "type" && passwordFocused) return Guard.Refuse("password_field")
        if (call.name == "done" || call.name == "speak") return Guard.Allow
        val known = target ?: TapTarget(label.ifBlank { call.args["label"].orEmpty() })
        val verdict = SafetyPolicy.forAction(
            op = if (call.name == "key") "press" else call.name,
            target = known,
            key = call.args["key"] ?: call.args["label"],
        )
        return when (verdict) {
            Verdict.Allow -> Guard.Allow
            is Verdict.Confirm -> Guard.Confirm(verdict.prompt)
            is Verdict.Block -> Guard.Refuse(verdict.reason)
        }
    }
}
