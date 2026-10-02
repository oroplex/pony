package app.pony.companion.tasks

import app.pony.companion.brain.JsonValue

enum class TaskState(val wire: String) {
    Queued("queued"),
    Sent("sent"),
    Running("running"),
    Waiting("waiting"),
    Done("done"),
    Stopped("stopped"),
    Failed("failed"),
    Expired("expired");

    val finished: Boolean get() = this == Done || this == Stopped || this == Failed || this == Expired

    companion object {
        fun of(wire: String?): TaskState = entries.firstOrNull { it.wire == wire } ?: Failed
    }
}

enum class StepKind(val wire: String) {
    Open("open"),
    Tap("tap"),
    Type("type"),
    Swipe("swipe"),
    Key("key"),
    Look("look"),
    Read("read"),
    Speak("speak"),
    Ask("ask"),
    Confirm("confirm"),
    Wait("wait"),
    Status("status"),
    Result("result"),
    Error("error");

    companion object {
        fun of(wire: String?): StepKind = entries.firstOrNull { it.wire == wire } ?: Status
    }
}

object Brains {
    const val GROK = "grok"
    const val PHONE = "phone"
    const val BASICS = "basics"
    const val BASICS_LABEL = "Pony Basics"
}

data class TaskStep(
    val at: Long,
    val kind: StepKind,
    val label: String,
    val ok: Boolean = true,
    val detail: String? = null,
    val shot: String? = null,
    val count: Int = 1,
)

data class TaskRecord(
    val id: String,
    val text: String,
    val source: String,
    val brain: String,
    val brainLabel: String,
    val createdAt: Long,
    val state: TaskState,
    val steps: List<TaskStep> = emptyList(),
    val outcome: String? = null,
    val headline: String? = null,
    val endedAt: Long? = null,
    val implicit: Boolean = false,
    val resumable: Boolean = false,
) {
    val running: Boolean get() = !state.finished

    val screenshots: List<String> get() = steps.mapNotNull { it.shot }

    fun durationMs(now: Long): Long = (endedAt ?: now) - createdAt

    /** Steps the owner cares about. Repeated looks are already folded into [TaskStep.count]. */
    val actionCount: Int get() = steps.count { it.kind != StepKind.Status && it.kind != StepKind.Result }

    fun toJson(): JsonValue = JsonValue.Obj(
        buildList {
            add("id" to JsonValue.str(id))
            add("text" to JsonValue.str(text))
            add("source" to JsonValue.str(source))
            add("brain" to JsonValue.str(brain))
            add("brainLabel" to JsonValue.str(brainLabel))
            add("createdAt" to JsonValue.num(createdAt))
            add("state" to JsonValue.str(state.wire))
            outcome?.let { add("outcome" to JsonValue.str(it)) }
            headline?.let { add("headline" to JsonValue.str(it)) }
            endedAt?.let { add("endedAt" to JsonValue.num(it)) }
            if (implicit) add("implicit" to JsonValue.bool(true))
            if (resumable) add("resumable" to JsonValue.bool(true))
            add("steps" to JsonValue.arr(steps.map { it.toJson() }))
        },
    )

    companion object {
        fun fromJson(value: JsonValue): TaskRecord? {
            val obj = value as? JsonValue.Obj ?: return null
            val id = obj.string("id") ?: return null
            val steps = (obj.get("steps") as? JsonValue.Arr)?.items.orEmpty().mapNotNull { stepFromJson(it) }
            return TaskRecord(
                id = id,
                text = obj.string("text").orEmpty(),
                source = obj.string("source") ?: "typed",
                brain = obj.string("brain") ?: Brains.GROK,
                brainLabel = obj.string("brainLabel") ?: "Grok Bot",
                createdAt = obj.long("createdAt") ?: 0L,
                state = TaskState.of(obj.string("state")),
                steps = steps,
                outcome = obj.string("outcome"),
                headline = obj.string("headline"),
                endedAt = obj.long("endedAt"),
                implicit = (obj.get("implicit") as? JsonValue.Bool)?.value ?: false,
                resumable = (obj.get("resumable") as? JsonValue.Bool)?.value ?: false,
            )
        }
    }
}

private fun TaskStep.toJson(): JsonValue = JsonValue.Obj(
    buildList {
        add("at" to JsonValue.num(at))
        add("kind" to JsonValue.str(kind.wire))
        add("label" to JsonValue.str(label))
        add("ok" to JsonValue.bool(ok))
        detail?.let { add("detail" to JsonValue.str(it)) }
        shot?.let { add("shot" to JsonValue.str(it)) }
        if (count > 1) add("count" to JsonValue.num(count))
    },
)

private fun stepFromJson(value: JsonValue): TaskStep? {
    val obj = value as? JsonValue.Obj ?: return null
    return TaskStep(
        at = obj.long("at") ?: 0L,
        kind = StepKind.of(obj.string("kind")),
        label = obj.string("label") ?: return null,
        ok = (obj.get("ok") as? JsonValue.Bool)?.value ?: true,
        detail = obj.string("detail"),
        shot = obj.string("shot"),
        count = obj.long("count")?.toInt() ?: 1,
    )
}

internal fun JsonValue.Obj.string(name: String): String? = (get(name) as? JsonValue.Str)?.value

internal fun JsonValue.Obj.long(name: String): Long? = (get(name) as? JsonValue.Num)?.value?.toLong()
