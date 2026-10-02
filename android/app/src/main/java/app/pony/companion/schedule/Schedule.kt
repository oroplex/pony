package app.pony.companion.schedule

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * When a scheduled task should run. A recurring schedule has a time of day and a
 * set of weekdays (empty means every day); a one-shot carries the exact instant
 * it should fire in [onceAt] and keeps its time of day only for the label.
 *
 * Everything here is pure so [nextFireAt] and the labels can be unit-tested on
 * the JVM without a device clock.
 */
data class Schedule(
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek> = emptySet(),
    val onceAt: Long? = null,
) {
    val repeats: Boolean get() = onceAt == null

    /**
     * The next moment this should fire, strictly after [now], or null when a
     * one-shot has already passed. Strictly-after is what keeps a recurring
     * alarm from firing twice: rescheduling right after a fire rolls to the next
     * day, never the instant that just fired.
     */
    fun nextFireAt(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        onceAt?.let { return it.takeIf { instant -> instant > now } }
        var day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        repeat(DAYS_IN_WEEK + 1) {
            if (days.isEmpty() || day.dayOfWeek in days) {
                val candidate = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                if (candidate > now) return candidate
            }
            day = day.plusDays(1)
        }
        return null
    }

    /** "7:00 AM", "6:30 PM" — a plain 12-hour clock label. */
    fun timeLabel(): String {
        val h12 = ((hour + 11) % 12) + 1
        val meridiem = if (hour < 12) "AM" else "PM"
        return "%d:%02d %s".format(h12, minute, meridiem)
    }

    /** "Every day", "Weekdays", "Weekends", or "Mon, Wed" for a recurring schedule. */
    fun recurrenceLabel(): String = when {
        days.isEmpty() || days.size == DAYS_IN_WEEK -> "Every day"
        days == WEEKDAYS -> "Weekdays"
        days == WEEKENDS -> "Weekends"
        else -> days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    }

    /**
     * A full human line for the list and the spoken confirmation, e.g.
     * "Every day at 7:00 AM", "Weekdays at 8:30 AM", "Tomorrow at 6:00 PM".
     * One-shots read relative to [now] so "tomorrow" stays "tomorrow".
     */
    fun describe(now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = timeLabel()
        val at = onceAt ?: return "${recurrenceLabel()} at $time"
        val fireDay = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val lead = when (ChronoUnit.DAYS.between(today, fireDay)) {
            0L -> "Today"
            1L -> "Tomorrow"
            in 2L..6L -> fireDay.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            else -> fireDay.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
        }
        return "$lead at $time"
    }

    companion object {
        const val DAYS_IN_WEEK = 7
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val WEEKENDS: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}
