package dev.memoji.flashcards.core.model

/**
 * The time of day the one daily reminder is aimed at.
 *
 * Aimed at, not promised for — delivery is deliberately inexact so the app never has to ask
 * for the exact-alarm privilege. See ADR 0004.
 */
data class ReminderTime(val hour: Int, val minute: Int) {

    /**
     * Stored as one number rather than two, so a half-written pair cannot leave the reminder
     * at an hour the user never picked.
     */
    val minutesOfDay: Int get() = hour * MINUTES_PER_HOUR + minute

    companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

        /** Evening, where a study nudge belongs, until the user moves it. */
        val DEFAULT = ReminderTime(hour = 20, minute = 0)

        fun ofMinutesOfDay(minutesOfDay: Int): ReminderTime =
            if (minutesOfDay in 0 until MINUTES_PER_DAY) {
                ReminderTime(
                    hour = minutesOfDay / MINUTES_PER_HOUR,
                    minute = minutesOfDay % MINUTES_PER_HOUR,
                )
            } else {
                DEFAULT
            }
    }
}
