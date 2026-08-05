package dev.memoji.flashcards.core.reminders

import dev.memoji.flashcards.core.model.ReminderTime
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole of the scheduling decision, kept away from WorkManager so it can be asked about
 * midnight and the clocks going forward without a device being involved.
 */
class ReminderScheduleTest {

    @Test
    fun `a time later today is later today`() {
        val delay = delayUntilNextReminder(ReminderTime(20, 0), at("2026-08-05T09:00:00"))

        assertEquals(Duration.ofHours(11), delay)
    }

    @Test
    fun `a time already past today is tomorrow`() {
        val delay = delayUntilNextReminder(ReminderTime(20, 0), at("2026-08-05T20:30:00"))

        assertEquals(Duration.ofHours(23).plusMinutes(30), delay)
    }

    /**
     * The reminder that has just fired must not schedule itself for a second ago and run
     * again immediately — the exact minute is the one case that has to land tomorrow.
     */
    @Test
    fun `the reminder time itself is tomorrow, not now`() {
        val delay = delayUntilNextReminder(ReminderTime(20, 0), at("2026-08-05T20:00:00"))

        assertEquals(Duration.ofDays(1), delay)
    }

    @Test
    fun `a minute before the reminder is a minute away`() {
        val delay = delayUntilNextReminder(ReminderTime(20, 0), at("2026-08-05T19:59:00"))

        assertEquals(Duration.ofMinutes(1), delay)
    }

    @Test
    fun `midnight is a reminder time like any other`() {
        val delay = delayUntilNextReminder(ReminderTime(0, 0), at("2026-08-05T23:30:00"))

        assertEquals(Duration.ofMinutes(30), delay)
    }

    /**
     * Recomputed from the wall clock on every run rather than by adding twenty-four hours, so
     * the nudge stays at the hour the user picked when the clocks move. The day the clocks go
     * forward is twenty-three hours long, and the reminder still lands at eight.
     */
    @Test
    fun `the day the clocks go forward is an hour shorter`() {
        val madrid = ZoneId.of("Europe/Madrid")
        val theEveningBefore = ZonedDateTime.of(2027, 3, 27, 20, 0, 0, 0, madrid)

        val delay = delayUntilNextReminder(ReminderTime(20, 0), theEveningBefore)

        assertEquals(Duration.ofHours(23), delay)
    }

    /** A reminder set for an hour that a spring-forward skips still has to happen. */
    @Test
    fun `a reminder inside the skipped hour still fires that day`() {
        val madrid = ZoneId.of("Europe/Madrid")
        val theNightBefore = ZonedDateTime.of(2027, 3, 27, 23, 0, 0, 0, madrid)

        val delay = delayUntilNextReminder(ReminderTime(2, 30), theNightBefore)

        // 02:30 does not exist that night; it resolves to 03:30, three and a half hours of
        // real time away — and, more to the point, still that night rather than never.
        assertEquals(Duration.ofHours(3).plusMinutes(30), delay)
        assertTrue(delay < Duration.ofDays(1))
    }

    /** Never negative and never longer than a day, whatever it is asked. */
    @Test
    fun `every minute of the day is somewhere in the next twenty-four hours`() {
        val now = at("2026-08-05T13:47:31")

        (0 until ReminderTime.MINUTES_PER_DAY).forEach { minuteOfDay ->
            val delay = delayUntilNextReminder(ReminderTime.ofMinutesOfDay(minuteOfDay), now)

            assertTrue("$minuteOfDay is in the past", delay > Duration.ZERO)
            assertTrue("$minuteOfDay is more than a day away", delay <= Duration.ofDays(1))
        }
    }

    private fun at(local: String): ZonedDateTime =
        ZonedDateTime.parse("${local}Z").withZoneSameInstant(ZoneId.of("UTC"))
}
