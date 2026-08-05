package dev.memoji.flashcards.core.reminders

import dev.memoji.flashcards.core.model.ReminderTime
import java.time.Duration
import java.time.ZonedDateTime

/**
 * How long until the next time it is [time].
 *
 * Worked out from the wall clock every time rather than by adding a day to the last one, so a
 * reminder that Doze held back by twenty minutes does not drag every following one with it,
 * and the hour stays put when the clocks change.
 *
 * Always greater than zero: at exactly the reminder time the answer is tomorrow, because the
 * run that has just fired is the one asking, and zero would have it fire again at once.
 */
fun delayUntilNextReminder(time: ReminderTime, now: ZonedDateTime): Duration {
    val today = now.toLocalDate().atTime(time.hour, time.minute).atZone(now.zone)
    // `atZone` has already moved a time the clocks skipped forward onto the hour that exists,
    // so a reminder set inside the missing hour lands just after it rather than not at all.
    val next = if (today > now) today else today.plusDays(1)
    return Duration.between(now, next)
}
