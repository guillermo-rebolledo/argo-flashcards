package dev.memoji.flashcards.core.reminders

/**
 * The nudge itself.
 *
 * It carries no numbers — no Cards waiting, no days missed, no streak at risk. Per ADR 0001
 * there is no backlog for it to report, and a reminder that invented one would contradict the
 * only promise the product makes.
 */
interface ReminderNotifier {

    /**
     * Posts the reminder, or does nothing if the user has notifications turned off for the
     * app. Silence is the correct outcome there, not an error: the system would drop it
     * anyway, and the Settings screen is where that gets explained.
     */
    fun notifyReminder()

    /** Whether a posted reminder would actually reach the user. */
    fun notificationsAllowed(): Boolean
}
