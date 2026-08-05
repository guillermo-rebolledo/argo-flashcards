package dev.memoji.flashcards.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTimeTest {

    @Test
    fun `every minute of the day round-trips through storage`() {
        (0 until ReminderTime.MINUTES_PER_DAY).forEach { minuteOfDay ->
            val time = ReminderTime.ofMinutesOfDay(minuteOfDay)

            assertEquals(minuteOfDay, time.minutesOfDay)
        }
    }

    /**
     * A stored value this version of the app cannot make sense of costs the user the reminder
     * time and nothing else — the same bargain every other setting strikes.
     */
    @Test
    fun `a stored value outside the day reads as the default`() {
        assertEquals(ReminderTime.DEFAULT, ReminderTime.ofMinutesOfDay(-1))
        assertEquals(ReminderTime.DEFAULT, ReminderTime.ofMinutesOfDay(ReminderTime.MINUTES_PER_DAY))
        assertEquals(ReminderTime.DEFAULT, ReminderTime.ofMinutesOfDay(Int.MAX_VALUE))
    }

    @Test
    fun `the default is the evening`() {
        assertEquals(20, ReminderTime.DEFAULT.hour)
        assertEquals(0, ReminderTime.DEFAULT.minute)
    }
}
