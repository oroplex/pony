package app.pony.companion.brain

/** Tiny JSON codec so brain tests run on the JVM, where org.json is a stub. */
sealed class JsonValue {
    abstract fun write(out: StringBuilder)

    fun encode(): String = buildString { write(this) }

    data class Obj(val fields: List<Pair<String, JsonValue>>) : JsonValue() {
        override fun write(out: StringBuilder) {
            out.append('{')
            fields.forEachIndexed { index, (key, value) ->
                if (index > 0) out.append(',')
                out.append('"').append(escape(key)).append('"').append(':')
                value.write(out)
            }
            out.append('}')
        }

        fun get(name: String): JsonValue? = fields.firstOrNull { it.first == name }?.second
    }

    data class Arr(val items: List<JsonValue>) : JsonValue() {
        override fun write(out: StringBuilder) {
            out.append('[')
            items.forEachIndexed { index, value ->
                if (index > 0) out.append(',')
                value.write(out)
            }
            out.append(']')
        }
    }

    data class Str(val value: String) : JsonValue() {
        override fun write(out: StringBuilder) {
            out.append('"').append(escape(value)).append('"')
        }
    }

    data class Num(val value: Double) : JsonValue() {
        override fun write(out: StringBuilder) {
            if (value.isFinite() && value % 1.0 == 0.0 && value <= Long.MAX_VALUE && value >= Long.MIN_VALUE) {
                out.append(value.toLong())
            } else {
                out.append(value)
            }
        }
    }

    data class Bool(val value: Boolean) : JsonValue() {
        override fun write(out: StringBuilder) {
            out.append(if (value) "true" else "false")
        }
    }

    data object Null : JsonValue() {
        override fun write(out: StringBuilder) {
            out.append("null")
        }
    }

    fun asString(): String = when (this) {
        is Str -> value
        is Num -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is Bool -> value.toString()
        Null -> ""
        else -> encode()
    }

    companion object {
        fun obj(vararg fields: Pair<String, JsonValue>): Obj = Obj(fields.toList())

        fun arr(items: List<JsonValue>): Arr = Arr(items)

        fun str(value: String): Str = Str(value)

        fun num(value: Number): Num = Num(value.toDouble())

        fun bool(value: Boolean): Bool = Bool(value)

        fun parse(raw: String): JsonValue = Parser(raw).parseValue()

        private fun escape(value: String): String = buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
        }
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseValue(): JsonValue {
            skip()
            if (i >= s.length) error("empty json")
            return when (val ch = s[i]) {
                '{' -> parseObj()
                '[' -> parseArr()
                '"' -> Str(parseString())
                't' -> literal("true", Bool(true))
                'f' -> literal("false", Bool(false))
                'n' -> literal("null", Null)
                else -> if (ch == '-' || ch.isDigit()) parseNum() else error("bad json at $i")
            }
        }

        private fun parseObj(): Obj {
            expect('{')
            skip()
            val fields = mutableListOf<Pair<String, JsonValue>>()
            if (peek('}')) {
                i++
                return Obj(fields)
            }
            while (i < s.length) {
                skip()
                val key = parseString()
                skip()
                expect(':')
                val value = parseValue()
                fields += key to value
                skip()
                when {
                    peek(',') -> i++
                    peek('}') -> {
                        i++
                        return Obj(fields)
                    }
                    else -> error("expected , or }")
                }
            }
            error("unclosed object")
        }

        private fun parseArr(): Arr {
            expect('[')
            skip()
            val items = mutableListOf<JsonValue>()
            if (peek(']')) {
                i++
                return Arr(items)
            }
            while (i < s.length) {
                items += parseValue()
                skip()
                when {
                    peek(',') -> i++
                    peek(']') -> {
                        i++
                        return Arr(items)
                    }
                    else -> error("expected , or ]")
                }
            }
            error("unclosed array")
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (i < s.length) {
                val ch = s[i++]
                when (ch) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (i >= s.length) error("bad escape")
                        when (val esc = s[i++]) {
                            '"', '\\', '/' -> out.append(esc)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) error("bad unicode")
                                val hex = s.substring(i, i + 4)
                                out.append(hex.toInt(16).toChar())
                                i += 4
                            }
                            else -> error("bad escape")
                        }
                    }
                    else -> out.append(ch)
                }
            }
            error("unclosed string")
        }

        private fun parseNum(): Num {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            if (i < s.length && s[i] == '.') {
                i++
                while (i < s.length && s[i].isDigit()) i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            return Num(s.substring(start, i).toDouble())
        }

        private fun literal(text: String, value: JsonValue): JsonValue {
            if (!s.regionMatches(i, text, 0, text.length)) error("expected $text")
            i += text.length
            return value
        }

        private fun expect(ch: Char) {
            skip()
            if (i >= s.length || s[i] != ch) error("expected $ch")
            i++
        }

        private fun peek(ch: Char): Boolean = i < s.length && s[i] == ch

        private fun skip() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
    }
}
