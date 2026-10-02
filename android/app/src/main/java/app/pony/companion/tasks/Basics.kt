package app.pony.companion.tasks

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class CalcOp(val symbol: String, val spoken: String) {
    PLUS("+", "plus"),
    MINUS("−", "minus"),
    TIMES("×", "times"),
    DIVIDE("÷", "divided by"),
}

sealed class CalcKey {
    data class Digit(val char: Char) : CalcKey()
    data class Op(val op: CalcOp) : CalcKey()
    data object Equals : CalcKey()
    data object Clear : CalcKey()

    val label: String
        get() = when (this) {
            is Digit -> char.toString()
            is Op -> op.symbol
            Equals -> "="
            Clear -> "C"
        }
}

sealed class Skill {
    data class OpenApp(val query: String) : Skill()
    data class Calculate(val numbers: List<BigDecimal>, val ops: List<CalcOp>) : Skill() {
        val expression: String
            get() = buildString {
                numbers.forEachIndexed { index, number ->
                    if (index > 0) append(' ').append(ops[index - 1].symbol).append(' ')
                    append(number.toPlainString())
                }
            }

        val keys: List<CalcKey>
            get() = buildList {
                add(CalcKey.Clear)
                numbers.forEachIndexed { index, number ->
                    if (index > 0) add(CalcKey.Op(ops[index - 1]))
                    number.toPlainString().forEach { add(CalcKey.Digit(it)) }
                }
                add(CalcKey.Equals)
            }

        /** Null when the expression has no finite answer, such as division by zero. */
        val answer: BigDecimal? get() = Basics.evaluate(numbers, ops)
    }

    data class SetTimer(val seconds: Int) : Skill()
    data object GoHome : Skill()
    data object GoBack : Skill()
}

/**
 * Pony Basics: requests the phone can do by itself, with no AI and no key.
 * [parse] returns null unless it understands the whole request, so Basics
 * never does half of something and calls it done.
 */
object Basics {
    const val CALCULATOR = "calculator"

    fun parse(text: String): List<Skill>? {
        val normalized = normalize(text)
        if (normalized.isBlank()) return null
        val skills = mutableListOf<Skill>()
        for (clause in clauses(normalized)) {
            val parsed = clause(clause) ?: return null
            skills += parsed
        }
        if (skills.isEmpty()) return null
        val hasCalc = skills.any { it is Skill.Calculate }
        val opensCalc = skills.any { it is Skill.OpenApp && isCalculator(it.query) }
        if (hasCalc && !opensCalc) skills.add(0, Skill.OpenApp(CALCULATOR))
        return skills.distinct()
    }

    fun canHandle(text: String): Boolean = parse(text) != null

    fun isCalculator(query: String): Boolean = query.contains("calc")

    fun evaluate(numbers: List<BigDecimal>, ops: List<CalcOp>): BigDecimal? {
        if (numbers.isEmpty() || ops.size != numbers.size - 1) return null
        val terms = ArrayDeque<BigDecimal>().apply { add(numbers[0]) }
        val pending = ArrayDeque<CalcOp>()
        for (index in ops.indices) {
            val op = ops[index]
            val next = numbers[index + 1]
            when (op) {
                CalcOp.TIMES -> terms.addLast(terms.removeLast().multiply(next, MATH))
                CalcOp.DIVIDE -> {
                    if (next.signum() == 0) return null
                    terms.addLast(terms.removeLast().divide(next, MATH))
                }
                else -> {
                    pending.addLast(op)
                    terms.addLast(next)
                }
            }
        }
        var total = terms.removeFirst()
        while (terms.isNotEmpty()) {
            val value = terms.removeFirst()
            total = if (pending.removeFirst() == CalcOp.PLUS) total.add(value, MATH) else total.subtract(value, MATH)
        }
        return total
    }

    fun format(value: BigDecimal): String {
        val rounded = value.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (rounded.signum() == 0) "0" else rounded.toPlainString()
    }

    private fun clause(text: String): List<Skill>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        timer(trimmed)?.let { return listOf(it) }
        if (trimmed.matches(Regex("(go (to )?)?(the )?home( screen)?"))) return listOf(Skill.GoHome)
        if (trimmed.matches(Regex("go back|back|press back"))) return listOf(Skill.GoBack)
        calculation(trimmed)?.let { calc ->
            val wantsCalculator = trimmed.contains("calc")
            return if (wantsCalculator) listOf(Skill.OpenApp(CALCULATOR), calc) else listOf(calc)
        }
        open(trimmed)?.let { return listOf(it) }
        return null
    }

    private fun open(text: String): Skill.OpenApp? {
        val match = Regex("^(open|launch|start|show( me)?|go to|switch to|bring up)\\s+(up\\s+)?(.+)$").find(text) ?: return null
        val name = match.groupValues[4]
            .replace(Regex("^(the|my|a)\\s+"), "")
            .replace(Regex("\\s+(app|application)$"), "")
            .trim()
        if (name.isEmpty() || name.split(' ').size > 4) return null
        if (name.any { it.isDigit() }) return null
        return Skill.OpenApp(if (name.contains("calc")) CALCULATOR else name)
    }

    private fun timer(text: String): Skill.SetTimer? {
        if (!text.contains("timer")) return null
        val match = Regex("(\\d+(?:\\.\\d+)?)\\s*[- ]?\\s*(second|sec|minute|min|hour|hr)s?").find(text) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        val seconds = when {
            unit.startsWith("s") -> amount
            unit.startsWith("m") -> amount * 60
            else -> amount * 3600
        }.toInt()
        if (seconds <= 0 || seconds > 24 * 3600) return null
        val rest = text.replace(match.value, " ")
            .replace(Regex("\\b(set|start|a|an|the|timer|for|please|me|up|of|countdown)\\b"), " ")
            .replace(Regex("[- ]"), "")
        return if (rest.isEmpty()) Skill.SetTimer(seconds) else null
    }

    private fun calculation(text: String): Skill.Calculate? {
        var body = text
            .replace(Regex("\\b(in|on|using|with)\\s+(the\\s+)?calculator( app)?\\b"), " ")
            .replace(Regex("\\bcalculator\\b"), " ")
            .replace(Regex("^(please\\s+)?(and\\s+)?"), "")
            .trim()
        body = body
            .replace(Regex("^(what('s| is)|calculate|compute|work out|figure out|tell me|type|enter|do)\\s+"), "")
            .trim()
        Regex("^subtract\\s+(\\S+)\\s+from\\s+(\\S+)$").find(body)?.let {
            return build(listOf(it.groupValues[2], it.groupValues[1]), listOf(CalcOp.MINUS))
        }
        val rewritten = when {
            body.startsWith("add ") -> body.removePrefix("add ").replace(Regex("\\s+(and|to)\\s+"), " plus ")
            body.startsWith("sum of ") -> body.removePrefix("sum of ").replace(Regex("\\s+and\\s+"), " plus ")
            body.startsWith("the sum of ") -> body.removePrefix("the sum of ").replace(Regex("\\s+and\\s+"), " plus ")
            body.startsWith("multiply ") -> body.removePrefix("multiply ").replace(Regex("\\s+(by|and)\\s+"), " times ")
            body.startsWith("divide ") -> body.removePrefix("divide ").replace(Regex("\\s+by\\s+"), " divided by ")
            else -> body
        }
        return expression(rewritten)
    }

    private fun expression(body: String): Skill.Calculate? {
        val spaced = body
            .replace("multiplied by", " × ")
            .replace("divided by", " ÷ ")
            .replace(Regex("\\bover\\b"), " ÷ ")
            .replace(Regex("\\bplus\\b"), " + ")
            .replace(Regex("\\bminus\\b"), " − ")
            .replace(Regex("\\btimes\\b"), " × ")
            .replace(Regex("(?<=\\d)\\s*x\\s*(?=\\d)"), " × ")
            .replace("*", " × ")
            .replace("/", " ÷ ")
            .replace("+", " + ")
            .replace(Regex("(?<=\\d)\\s*-\\s*(?=\\d)"), " − ")
            .replace("=", " ")
            .replace("?", " ")
            .trim()
        val tokens = spaced.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size < 3 || tokens.size % 2 == 0) return null
        val numbers = mutableListOf<String>()
        val ops = mutableListOf<CalcOp>()
        tokens.forEachIndexed { index, token ->
            if (index % 2 == 0) {
                numbers += token
            } else {
                ops += when (token) {
                    "+" -> CalcOp.PLUS
                    "−" -> CalcOp.MINUS
                    "×" -> CalcOp.TIMES
                    "÷" -> CalcOp.DIVIDE
                    else -> return null
                }
            }
        }
        return build(numbers, ops)
    }

    private fun build(rawNumbers: List<String>, ops: List<CalcOp>): Skill.Calculate? {
        val numbers = rawNumbers.map { raw ->
            val cleaned = raw.trim().removeSuffix("?").trim()
            if (!cleaned.matches(Regex("\\d{1,12}(\\.\\d{1,8})?"))) return null
            BigDecimal(cleaned)
        }
        if (numbers.size < 2 || ops.size != numbers.size - 1) return null
        return Skill.Calculate(numbers, ops)
    }

    private fun clauses(text: String): List<String> {
        val parts = mutableListOf<String>()
        val pieces = text.split(Regex("\\s*(?:,|;|\\band then\\b|\\bthen\\b)\\s*"))
        for (piece in pieces) {
            parts += splitOnAnd(piece)
        }
        return parts.map { it.trim() }.filter { it.isNotEmpty() && it != "and" }
    }

    /** "and" joins clauses, except inside "add 2 and 2" or "sum of 2 and 2". */
    private fun splitOnAnd(piece: String): List<String> {
        val words = piece.split(' ')
        val out = mutableListOf<String>()
        var current = mutableListOf<String>()
        for ((index, word) in words.withIndex()) {
            val joinsNumbers = word == "and" && current.any { it == "add" || it == "multiply" || it == "sum" } &&
                current.lastOrNull()?.any { it.isDigit() } == true &&
                words.getOrNull(index + 1)?.firstOrNull()?.isDigit() == true
            if (word == "and" && !joinsNumbers) {
                if (current.isNotEmpty()) out += current.joinToString(" ")
                current = mutableListOf()
            } else {
                current += word
            }
        }
        if (current.isNotEmpty()) out += current.joinToString(" ")
        return out
    }

    fun normalize(text: String): String {
        var s = text.lowercase()
            .replace('’', '\'')
            .replace(Regex("^\\s*(try|example)\\s*:\\s*"), "")
            .replace(Regex("\\b(hey|ok|okay)\\s+pony\\b[,!]?"), " ")
            .replace(Regex("\\bplease\\b|\\bcan you\\b|\\bcould you\\b|\\bfor me\\b"), " ")
            .replace(Regex("[!.]+$"), "")
            .replace(Regex("(?<=\\d),(?=\\d{3}\\b)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        s = numberWords(s)
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun numberWords(text: String): String {
        val words = text.split(' ')
        val out = mutableListOf<String>()
        var index = 0
        while (index < words.size) {
            var value = 0L
            var used = 0
            var current = 0L
            var cursor = index
            while (cursor < words.size) {
                val word = words[cursor].trimEnd('?', ',')
                val small = SMALL[word]
                val tens = TENS[word]
                when {
                    small != null -> current += small
                    tens != null -> current += tens
                    word == "hundred" && used > 0 -> current *= 100
                    word == "thousand" && used > 0 -> {
                        value += current * 1000
                        current = 0
                    }
                    word == "a" && words.getOrNull(cursor + 1) in setOf("hundred", "thousand") -> current = 1
                    else -> break
                }
                used += 1
                cursor += 1
            }
            if (used > 0 && !(used == 1 && words[index] == "a")) {
                out += (value + current).toString() + words[cursor - 1].takeLastWhile { it == '?' }
                index = cursor
            } else {
                out += words[index]
                index += 1
            }
        }
        return out.joinToString(" ")
    }

    private val MATH = MathContext(20, RoundingMode.HALF_UP)

    private val SMALL = mapOf(
        "zero" to 0L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L, "six" to 6L,
        "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L, "eleven" to 11L, "twelve" to 12L,
        "thirteen" to 13L, "fourteen" to 14L, "fifteen" to 15L, "sixteen" to 16L, "seventeen" to 17L,
        "eighteen" to 18L, "nineteen" to 19L,
    )

    private val TENS = mapOf(
        "twenty" to 20L, "thirty" to 30L, "forty" to 40L, "fifty" to 50L,
        "sixty" to 60L, "seventy" to 70L, "eighty" to 80L, "ninety" to 90L,
    )
}
