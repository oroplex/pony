package app.pony.companion.schedule

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns a natural phrase into a [Schedule] and the task left over. Handles the
 * shapes Pony actually hears — "every morning at 7", "every day at 7am",
 * "weekdays at 8:30", "tomorrow at 18:00", "tonight at 9", "on Mondays at 7" —
 * and returns null when there's no time it can pin down.
 *
 * Pure: the only clock it sees is [now], so the same phrase always parses the
 * same way in a test. [now] is used only to resolve relative words like
 * "tomorrow" and to pick the next occurrence of a one-shot.
 */
object ScheduleParser {
    data class Parsed(val schedule: Schedule, val task: String)

    private enum class DayPart(val hour: Int, val minute: Int, val pm: Boolean) {
        MORNING(8, 0, false),
        NOON(12, 0, true),
        AFTERNOON(15, 0, true),
        EVENING(19, 0, true),
        NIGHT(21, 0, true),
        MIDNIGHT(0, 0, false),
    }

    private val DAY_WORDS = listOf(
        DayOfWeek.MONDAY to "mon(days?)?",
        DayOfWeek.TUESDAY to "tue(s|sdays?)?",
        DayOfWeek.WEDNESDAY to "wed(nesdays?)?",
        DayOfWeek.THURSDAY to "thu(r|rs|rsdays?)?",
        DayOfWeek.FRIDAY to "fri(days?)?",
        DayOfWeek.SATURDAY to "sat(urdays?)?",
        DayOfWeek.SUNDAY to "sun(days?)?",
    )

    private val HOUR_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    )

    fun parse(raw: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): Parsed? {
        val text = normalize(raw)
        if (text.isBlank()) return null

        val part = dayPartOf(text)
        val time = timeOf(text, part)
        // A schedule needs a clock. Either an explicit time or a day-part that
        // implies one (every morning); otherwise this isn't a schedule.
        val hour = time?.first ?: part?.hour ?: return null
        val minute = time?.second ?: part?.minute ?: return null

        val schedule = when (val recurrence = recurrenceOf(text)) {
            is Recurrence.Repeating -> Schedule(hour, minute, recurrence.days)
            Recurrence.Tomorrow -> Schedule(hour, minute, onceAt = tomorrowInstant(now, zone, hour, minute))
            Recurrence.Today -> Schedule(hour, minute, onceAt = todayInstant(now, zone, hour, minute))
            is Recurrence.OnDay -> Schedule(hour, minute, onceAt = nextInstant(now, zone, hour, minute, recurrence.day))
            // A time with no day qualifier: fire once at its next occurrence.
            null -> Schedule(hour, minute, onceAt = nextInstant(now, zone, hour, minute, onDay = null))
        }
        return Parsed(schedule, taskOf(text))
    }

    private sealed interface Recurrence {
        data class Repeating(val days: Set<DayOfWeek>) : Recurrence
        data object Tomorrow : Recurrence
        data object Today : Recurrence
        data class OnDay(val day: DayOfWeek) : Recurrence
    }

    private fun recurrenceOf(text: String): Recurrence? {
        if (Regex("\\btomorrow\\b").containsMatchIn(text)) return Recurrence.Tomorrow
        if (Regex("\\b(tonight|today|this (morning|afternoon|evening|noon))\\b").containsMatchIn(text)) {
            return Recurrence.Today
        }
        if (Regex("\\b(every ?day|each day|daily|every single day)\\b").containsMatchIn(text)) {
            return Recurrence.Repeating(emptySet())
        }
        if (Regex("\\bevery (morning|afternoon|evening|night|noon)\\b").containsMatchIn(text)) {
            return Recurrence.Repeating(emptySet())
        }
        if (Regex("\\bweek ?days?\\b").containsMatchIn(text)) return Recurrence.Repeating(Schedule.WEEKDAYS)
        if (Regex("\\bweek ?ends?\\b").containsMatchIn(text)) return Recurrence.Repeating(Schedule.WEEKENDS)

        val days = namedDays(text)
        if (days.isNotEmpty()) {
            val recurring = Regex("\\b(every|each)\\b").containsMatchIn(text) || hasPluralDay(text)
            return if (recurring) Recurrence.Repeating(days) else Recurrence.OnDay(days.first())
        }
        return null
    }

    private fun namedDays(text: String): Set<DayOfWeek> {
        val found = linkedSetOf<DayOfWeek>()
        DAY_WORDS.forEach { (day, pattern) ->
            if (Regex("\\b$pattern\\b").containsMatchIn(text)) found += day
        }
        return found
    }

    private fun hasPluralDay(text: String): Boolean =
        Regex("\\b(mondays|tuesdays|wednesdays|thursdays|fridays|saturdays|sundays)\\b").containsMatchIn(text)

    /** The next time at (hour:minute) matching [onDay] (null = any day), strictly after now. */
    private fun nextInstant(now: Long, zone: ZoneId, hour: Int, minute: Int, onDay: DayOfWeek?): Long {
        var day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        repeat(Schedule.DAYS_IN_WEEK + 1) {
            if (onDay == null || day.dayOfWeek == onDay) {
                val at = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                if (at > now) return at
            }
            day = day.plusDays(1)
        }
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    /** Tomorrow at the time, whether or not the time has passed today. */
    private fun tomorrowInstant(now: Long, zone: ZoneId, hour: Int, minute: Int): Long {
        val tomorrow: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
        return tomorrow.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    /** Today at the time. May be in the past (for "tonight at 9" said at 10pm); the caller rejects a passed one-shot. */
    private fun todayInstant(now: Long, zone: ZoneId, hour: Int, minute: Int): Long {
        val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    private fun dayPartOf(text: String): DayPart? = when {
        Regex("\\bmidnight\\b").containsMatchIn(text) -> DayPart.MIDNIGHT
        Regex("\\bnoon\\b").containsMatchIn(text) -> DayPart.NOON
        Regex("\\b(morning)\\b").containsMatchIn(text) -> DayPart.MORNING
        Regex("\\b(afternoon)\\b").containsMatchIn(text) -> DayPart.AFTERNOON
        Regex("\\b(evening)\\b").containsMatchIn(text) -> DayPart.EVENING
        Regex("\\b(night|tonight)\\b").containsMatchIn(text) -> DayPart.NIGHT
        else -> null
    }

    /** The (hour, minute) an explicit time mentions, or null if none is written. Prefers a time right after "at". */
    private fun timeOf(text: String, part: DayPart?): Pair<Int, Int>? {
        if (Regex("\\bmidnight\\b").containsMatchIn(text)) return 0 to 0
        if (Regex("\\bnoon\\b").containsMatchIn(text)) return 12 to 0
        val match = Regex("\\bat (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b").find(text)
            ?: Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b").find(text)
            ?: return null
        val rawHour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if (rawHour > 23 || minute > 59) return null
        val meridiem = match.groupValues[3]
        val hour = when {
            meridiem == "am" -> if (rawHour == 12) 0 else rawHour
            meridiem == "pm" -> if (rawHour == 12) 12 else rawHour + 12
            rawHour >= 13 -> rawHour // 24-hour clock, e.g. 18:00
            part != null -> dayPartHour(rawHour, part)
            else -> if (rawHour == 12) 12 else rawHour // bare hour with no cue: take it as written (AM-ish)
        }
        return hour to minute
    }

    private fun dayPartHour(rawHour: Int, part: DayPart): Int = when {
        rawHour == 12 && !part.pm -> 0
        rawHour == 12 -> 12
        part.pm && rawHour < 12 -> rawHour + 12
        else -> rawHour
    }

    /** What's left once the schedule words are stripped out — the thing Pony should actually do. */
    private fun taskOf(text: String): String {
        // Framing verbs the owner wraps around the real task, wherever they sit:
        // "tonight at 9 remind me to stretch" is really just "stretch".
        var rest = text.replace(Regex("\\b(i want (you|pony) to|remind me to|remind me|set (up )?a reminder to|set a reminder)\\b"), " ")
            .replace(Regex("^\\s*(and |then |please |schedule |set up )+"), " ")
        SCHEDULE_PHRASES.forEach { rest = rest.replace(it, " ") }
        rest = rest
            .replace(Regex("\\b(at|by)\\s+(\\d{1,2})(?::\\d{2})?\\s*(am|pm)?\\b"), " ")
            .replace(Regex("\\b(\\d{1,2})(?::\\d{2})?\\s*(am|pm)?\\b"), " ")
            .replace(Regex("[,;]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        // Whatever filler is left dangling at either end after the schedule words
        // were cut out — "on", "at", "to", "every" — is noise, not the task.
        val words = rest.split(" ").filter { it.isNotBlank() }.toMutableList()
        while (words.isNotEmpty() && words.first() in EDGE_FILLERS) words.removeAt(0)
        while (words.isNotEmpty() && words.last() in EDGE_FILLERS) words.removeAt(words.size - 1)
        return words.joinToString(" ")
    }

    fun normalize(raw: String): String {
        var text = raw.lowercase()
            .replace('’', '\'')
            .replace(Regex("\\b([ap])\\.m\\.?\\b"), "$1m")
            .replace(Regex("\\bo'?clock\\b"), " ")
            .replace(Regex("\\bhey\\s+pony\\b[,!]?"), " ")
            .replace(Regex("\\bplease\\b|\\bcan you\\b|\\bcould you\\b|\\bfor me\\b"), " ")
            .replace(Regex("[.!?]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        text = wordTimes(text)
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun wordTimes(text: String): String {
        var out = text
        HOUR_WORDS.forEach { (word, value) ->
            out = out.replace(Regex("\\b$word\\b"), value.toString())
        }
        out = out
            .replace(Regex("\\bhalf past (\\d{1,2})\\b"), "$1:30")
            .replace(Regex("\\bquarter past (\\d{1,2})\\b"), "$1:15")
            .replace(Regex("\\b(\\d{1,2}) thirty\\b"), "$1:30")
            .replace(Regex("\\b(\\d{1,2}) fifteen\\b"), "$1:15")
            .replace(Regex("\\b(\\d{1,2}) forty[ -]?five\\b"), "$1:45")
        return out
    }

    private val EDGE_FILLERS = setOf("on", "at", "by", "to", "and", "then", "every", "each", "this", "for")

    private val SCHEDULE_PHRASES = listOf(
        Regex("\\bevery ?day\\b"),
        Regex("\\beach day\\b"),
        Regex("\\bevery single day\\b"),
        Regex("\\bdaily\\b"),
        Regex("\\bweek ?days?\\b"),
        Regex("\\bweek ?ends?\\b"),
        Regex("\\bevery (morning|afternoon|evening|night|noon)\\b"),
        Regex("\\bthis (morning|afternoon|evening|noon)\\b"),
        Regex("\\b(tomorrow|tonight|today)\\b"),
        Regex("\\b(morning|afternoon|evening|night|noon|midnight)\\b"),
        Regex("\\bevery (mon(days?)?|tue(s|sdays?)?|wed(nesdays?)?|thu(r|rs|rsdays?)?|fri(days?)?|sat(urdays?)?|sun(days?)?)\\b"),
        Regex("\\b(mondays?|tuesdays?|wednesdays?|thursdays?|fridays?|saturdays?|sundays?|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)\\b"),
    )
}
