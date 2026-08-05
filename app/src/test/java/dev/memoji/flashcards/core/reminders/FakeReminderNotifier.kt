package dev.memoji.flashcards.core.reminders

/**
 * A notifier that says whether it was asked to post, and lets the test decide what Android
 * would have done about it.
 */
internal class FakeReminderNotifier(
    /** What Android would say if asked right now. The test moves it as the user would. */
    var allowed: Boolean = true,
) : ReminderNotifier {

    /** Posts that actually reached the user, which is not the same as posts asked for. */
    var delivered = 0
        private set

    var asked = 0
        private set

    override fun notifyReminder() {
        asked++
        if (allowed) delivered++
    }

    override fun notificationsAllowed(): Boolean = allowed
}
