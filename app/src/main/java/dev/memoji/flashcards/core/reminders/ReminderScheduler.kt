package dev.memoji.flashcards.core.reminders

import dev.memoji.flashcards.core.model.ReminderTime

/**
 * Puts exactly one reminder in the diary, or takes it out again.
 *
 * There is never more than one scheduled, whatever order these are called in — changing the
 * time is a reschedule, not a second nudge.
 */
interface ReminderScheduler {

    /**
     * Schedules the next reminder for [time], throwing away anything already scheduled. This
     * is what a settings change calls.
     */
    fun schedule(time: ReminderTime)

    /**
     * The same, called by the reminder that has just fired to line up tomorrow's. Separate
     * because a reminder cannot [schedule] over itself — it is the thing being replaced —
     * so this one queues behind the run that is asking rather than cancelling it.
     */
    fun scheduleAfterFiring(time: ReminderTime)

    fun cancel()
}
