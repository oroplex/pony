package app.pony.companion.tasks

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import app.pony.companion.a11y.PonyAccessibilityService
import app.pony.companion.display.ScreenRouter
import org.json.JSONObject
import java.math.BigDecimal

/** Finds an installed app by the name the owner said. */
object AppFinder {
    data class App(val packageName: String, val label: String)

    private val aliases = mapOf(
        "calculator" to listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator", "com.android.calculator2", "com.miui.calculator", "com.oneplus.calculator"),
        "camera" to listOf("com.sec.android.app.camera", "com.google.android.GoogleCamera", "com.android.camera2", "com.android.camera"),
        "settings" to listOf("com.android.settings"),
        "clock" to listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock"),
        "photos" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
        "gallery" to listOf("com.sec.android.gallery3d", "com.google.android.apps.photos"),
        "notes" to listOf("com.samsung.android.app.notes", "com.google.android.keep"),
        "messages" to listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
        "calendar" to listOf("com.samsung.android.calendar", "com.google.android.calendar"),
        "browser" to listOf("com.sec.android.app.sbrowser", "com.android.chrome"),
        "maps" to listOf("com.google.android.apps.maps"),
        "files" to listOf("com.sec.android.app.myfiles", "com.google.android.apps.nbu.files"),
    )

    fun find(context: Context, query: String): App? {
        val pm = context.packageManager
        val launchers = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        }.getOrDefault(emptyList())
        val apps = launchers.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            App(pkg, info.loadLabel(pm).toString())
        }.distinctBy { it.packageName }
        val wanted = query.lowercase().trim()
        aliases[wanted]?.firstNotNullOfOrNull { pkg -> apps.firstOrNull { it.packageName == pkg } }?.let { return it }
        return apps.firstOrNull { it.label.lowercase() == wanted }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(wanted) }
            ?: apps.firstOrNull { it.label.lowercase().contains(wanted) }
            ?: aliases.entries.firstOrNull { (name, _) -> wanted.contains(name) }?.value
                ?.firstNotNullOfOrNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    }
}

/**
 * Runs Pony Basics on the main screen, where the owner can watch. Every
 * press is a real accessibility action on the app in front, recorded as a
 * step of the live task.
 */
object SkillRunner {
    data class Outcome(val ok: Boolean, val message: String)

    fun run(context: Context, skills: List<Skill>, cancelled: () -> Boolean): Outcome {
        if (PonyAccessibilityService.instance == null) {
            return Outcome(false, "Turn on Pony control first, then try again.")
        }
        var message: String? = null
        for (skill in skills) {
            if (cancelled()) return Outcome(false, "Stopped.")
            val a11y = PonyAccessibilityService.instance ?: return Outcome(false, "Pony control turned off.")
            when (skill) {
                is Skill.OpenApp -> {
                    val app = AppFinder.find(context, skill.query)
                        ?: return Outcome(false, missingApp(skill.query))
                    val opened = ScreenRouter.open(context, app.packageName, JSONObject().put("display", "main"), consent = { true })
                    TaskRuntime.step(StepKind.Open, "Opened ${app.label}", opened.ok)
                    if (!opened.ok) return Outcome(false, "${app.label} didn't open.")
                    ScreenRouter.expectPackage(app.packageName)
                    if (!awaitForeground(a11y, app.packageName, 6_000, cancelled)) {
                        return Outcome(false, "${app.label} didn't come to the front.")
                    }
                    pause(450)
                    message = message ?: "Opened ${app.label}."
                }
                is Skill.Calculate -> {
                    val outcome = calculate(a11y, skill, cancelled)
                    if (!outcome.ok) return outcome
                    message = outcome.message
                }
                is Skill.SetTimer -> {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, skill.seconds)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, "Pony")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val ok = runCatching { a11y.launch(intent, null) }.getOrDefault(false)
                    val length = describe(skill.seconds)
                    TaskRuntime.step(StepKind.Open, "Set a $length timer in Clock", ok)
                    if (!ok) return Outcome(false, "The clock app didn't take the timer.")
                    message = "Your $length timer is running."
                }
                Skill.GoHome -> {
                    val ok = a11y.press("home")
                    TaskRuntime.step(StepKind.Key, "Went to the home screen", ok)
                    message = message ?: "Home screen."
                }
                Skill.GoBack -> {
                    val ok = a11y.press("back")
                    TaskRuntime.step(StepKind.Key, "Went back", ok)
                    message = message ?: "Went back."
                }
            }
        }
        return Outcome(true, message ?: "Done.")
    }

    private fun calculate(a11y: PonyAccessibilityService, skill: Skill.Calculate, cancelled: () -> Boolean): Outcome {
        val answer = skill.answer
        for (key in skill.keys) {
            if (cancelled()) return Outcome(false, "Stopped.")
            val (labels, ids) = targets(key)
            val pressed = a11y.pressControl(labels, ids)
            if (pressed == null) {
                if (key == CalcKey.Clear) continue
                TaskRuntime.step(StepKind.Tap, "Couldn't find “${key.label}”", ok = false)
                return Outcome(false, "The calculator in front has no “${key.label}” key Pony can press.")
            }
            TaskRuntime.step(StepKind.Tap, "Tapped “${key.label}”")
            pause(if (key == CalcKey.Equals) 500 else 220)
        }
        val shown = readResult(a11y, answer)
        val expression = skill.expression
        val value = answer?.let { Basics.format(it) }
        if (shown != null) TaskRuntime.step(StepKind.Read, "Read the result: $shown")
        return when {
            value == null -> Outcome(true, "$expression has no answer. The calculator shows why.")
            shown != null -> Outcome(true, "$expression = $shown")
            else -> Outcome(true, "$expression = $value")
        }
    }

    private fun targets(key: CalcKey): Pair<List<String>, List<String>> = when (key) {
        is CalcKey.Digit -> if (key.char == '.') {
            listOf(".", ",", "point", "decimal point", "decimal") to listOf("dec_point", "btn_dot", "calc_keypad_btn_dot", "point", "decimal")
        } else {
            val d = key.char
            listOf(d.toString()) to listOf("digit_$d", "btn_0$d", "calc_keypad_btn_0$d", "btn_$d", "key_$d", "bt_0$d")
        }
        is CalcKey.Op -> when (key.op) {
            CalcOp.PLUS -> listOf("+", "plus", "add") to listOf("op_add", "btn_add", "calc_keypad_btn_add", "plus")
            CalcOp.MINUS -> listOf("−", "-", "minus", "subtract") to listOf("op_sub", "btn_sub", "calc_keypad_btn_sub", "minus")
            CalcOp.TIMES -> listOf("×", "*", "x", "multiply", "times", "multiplication") to listOf("op_mul", "btn_mul", "calc_keypad_btn_mul", "multiply")
            CalcOp.DIVIDE -> listOf("÷", "/", "divide", "divided by", "division") to listOf("op_div", "btn_div", "calc_keypad_btn_div", "divide")
        }
        CalcKey.Equals -> listOf("=", "equals", "equal", "calculate") to listOf("eq", "equal", "btn_equal", "calc_keypad_btn_equal", "equals")
        CalcKey.Clear -> listOf("c", "ac", "clr", "clear", "all clear", "ce") to listOf("clr", "clear", "btn_clear", "calc_keypad_btn_clear", "all_clear")
    }

    private fun readResult(a11y: PonyAccessibilityService, answer: BigDecimal?): String? {
        val expected = answer?.let { Basics.format(it) } ?: return null
        val texts = a11y.visibleTexts()
        val preferred = texts.sortedByDescending { (_, id) ->
            val name = id.orEmpty().lowercase()
            when {
                name.contains("result") -> 3
                name.contains("formula") || name.contains("display") || name.contains("edt") -> 2
                else -> 0
            }
        }
        for ((text, _) in preferred) {
            val cleaned = text.replace(",", "").replace(" ", "").replace('−', '-').removePrefix("=")
            if (cleaned == expected) return expected
        }
        return null
    }

    private fun awaitForeground(a11y: PonyAccessibilityService, packageName: String, timeoutMs: Long, cancelled: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cancelled()) return false
            if (a11y.foregroundPackage() == packageName) return true
            pause(150)
        }
        return a11y.foregroundPackage() == packageName
    }

    fun missingApp(query: String): String =
        if (Basics.isCalculator(query)) {
            "This phone doesn't have a calculator app Pony can open. Install one, then try again."
        } else {
            "Pony couldn't find an app called “${query.replaceFirstChar { it.uppercase() }}” on this phone."
        }

    private fun describe(seconds: Int): String = when {
        seconds % 3600 == 0 -> "${seconds / 3600}-hour"
        seconds % 60 == 0 -> "${seconds / 60}-minute"
        else -> "$seconds-second"
    }

    private fun pause(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
