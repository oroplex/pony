package app.pony.companion.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset

class ScheduleParserTest {
    private val zone = ZoneOffset.UTC
    // A fixed anchor so every phrase parses the same every run: Wednesday, 9:00 AM.
    private val wed9am = at(2026, 1, 7, 9, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun parse(text: String, now: Long = wed9am) = ScheduleParser.parse(text, now, zone)

    @Test
    fun everyMorningAtSevenRepeatsDailyAndKeepsTheTask() {
        val parsed = parse("every morning at 7 read my calendar")!!
        assertEquals(7, parsed.schedule.hour)
        assertEquals(0, parsed.schedule.minute)
        assertTrue(parsed.schedule.days.isEmpty())
        assertTrue(parsed.schedule.repeats)
        assertEquals("read my calendar", parsed.task)
        // 7:00 already passed today, so it fires tomorrow morning.
        assertEquals(at(2026, 1, 8, 7, 0), parsed.schedule.nextFireAt(wed9am, zone))
    }

    @Test
    fun everyDayAtSevenAmParsesTheMeridiem() {
        val parsed = parse("every day at 7am")!!
        assertEquals(7, parsed.schedule.hour)
        assertTrue(parsed.schedule.repeats)
        assertTrue(parsed.schedule.days.isEmpty())
    }

    @Test
    fun weekdaysAtEightThirtyRestrictsToTheWorkWeek() {
        val parsed = parse("weekdays at 8:30 water the plants")!!
        assertEquals(8, parsed.schedule.hour)
        assertEquals(30, parsed.schedule.minute)
        assertEquals(Schedule.WEEKDAYS, parsed.schedule.days)
        assertEquals("water the plants", parsed.task)
    }

    @Test
    fun weekdaysSkipTheWeekendWhenFindingTheNextFire() {
        val schedule = parse("weekdays at 8:30")!!.schedule
        // From Friday 9am, the next weekday fire is Monday, not Saturday.
        val friday9am = at(2026, 1, 9, 9, 0)
        assertEquals(DayOfWeek.FRIDAY, LocalDateTime.of(2026, 1, 9, 9, 0).dayOfWeek)
        assertEquals(at(2026, 1, 12, 8, 30), schedule.nextFireAt(friday9am, zone))
    }

    @Test
    fun tomorrowAtEighteenHundredIsAOneShotTheNextDay() {
        val parsed = parse("tomorrow at 18:00 send the report")!!
        assertEquals(18, parsed.schedule.hour)
        assertFalse(parsed.schedule.repeats)
        assertEquals(at(2026, 1, 8, 18, 0), parsed.schedule.onceAt)
        assertEquals(at(2026, 1, 8, 18, 0), parsed.schedule.nextFireAt(wed9am, zone))
        assertEquals("send the report", parsed.task)
    }

    @Test
    fun tonightUsesTheEveningAndFiresTheSameDay() {
        val parsed = parse("tonight at 9 remind me to stretch")!!
        assertEquals(21, parsed.schedule.hour)
        assertFalse(parsed.schedule.repeats)
        assertEquals(at(2026, 1, 7, 21, 0), parsed.schedule.nextFireAt(wed9am, zone))
        assertEquals("stretch", parsed.task)
    }

    @Test
    fun aPassedOneShotHasNoNextFire() {
        val tenPm = at(2026, 1, 7, 22, 0)
        val schedule = parse("tonight at 9", tenPm)!!.schedule
        assertNull(schedule.nextFireAt(tenPm, zone))
    }

    @Test
    fun onMondaysRepeatsWeeklyOnThatDay() {
        val schedule = parse("on mondays at 7 check the news")!!.schedule
        assertEquals(setOf(DayOfWeek.MONDAY), schedule.days)
        assertTrue(schedule.repeats)
        assertEquals(at(2026, 1, 12, 7, 0), schedule.nextFireAt(wed9am, zone))
    }

    @Test
    fun aSingularWeekdayIsAOneShotOnTheNextSuchDay() {
        val schedule = parse("monday at 6pm call mom")!!.schedule
        assertEquals(18, schedule.hour)
        assertFalse(schedule.repeats)
        assertEquals(at(2026, 1, 12, 18, 0), schedule.onceAt)
    }

    @Test
    fun aDailyScheduleFiresTodayWhenTheTimeIsStillAhead() {
        val schedule = parse("every day at 6pm")!!.schedule
        assertEquals(at(2026, 1, 7, 18, 0), schedule.nextFireAt(wed9am, zone))
    }

    @Test
    fun spelledOutHoursBecomeDigits() {
        val schedule = parse("every morning at seven")!!.schedule
        assertEquals(7, schedule.hour)
    }

    @Test
    fun noonAndMidnightAreUnderstood() {
        assertEquals(12 to 0, parse("every day at noon")!!.schedule.let { it.hour to it.minute })
        assertEquals(0 to 0, parse("every day at midnight")!!.schedule.let { it.hour to it.minute })
    }

    @Test
    fun aRequestWithNoTimeIsNotASchedule() {
        assertNull(parse("read my calendar"))
        assertNull(parse("hello there"))
    }

    @Test
    fun timeLabelsReadAsAClock() {
        assertEquals("7:00 AM", Schedule(7, 0).timeLabel())
        assertEquals("6:00 PM", Schedule(18, 0).timeLabel())
        assertEquals("8:30 AM", Schedule(8, 30).timeLabel())
        assertEquals("12:00 AM", Schedule(0, 0).timeLabel())
        assertEquals("12:00 PM", Schedule(12, 0).timeLabel())
    }

    @Test
    fun describeReadsNaturally() {
        assertEquals("Every day at 7:00 AM", Schedule(7, 0).describe(wed9am, zone))
        assertEquals("Weekdays at 8:30 AM", Schedule(8, 30, Schedule.WEEKDAYS).describe(wed9am, zone))
        assertEquals("Tomorrow at 6:00 PM", Schedule(18, 0, onceAt = at(2026, 1, 8, 18, 0)).describe(wed9am, zone))
        assertEquals("Today at 9:00 PM", Schedule(21, 0, onceAt = at(2026, 1, 7, 21, 0)).describe(wed9am, zone))
    }
}
