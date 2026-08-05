package dev.memoji.flashcards.feature.settings

/**
 * What the reminder row is actually able to say.
 *
 * The setting and the system permission are two different facts and either can change without
 * the other — a permission revoked in system settings does not reach back and turn the
 * preference off — so the row is worked out from both rather than from the switch alone. A
 * switch drawn on while Android drops every notification would be the app lying to the user.
 */
internal enum class ReminderStatus {
    /** The user has not asked for reminders. */
    OFF,

    /** The user asked, and the system will deliver. */
    ON,

    /**
     * The user asked and the system will not deliver — the permission was denied, or
     * notifications were turned off for the app afterwards. The preference is deliberately
     * left on: the user's answer has not changed, and granting the permission later brings
     * the reminder back without them having to ask twice.
     */
    BLOCKED,
}

internal fun reminderStatus(
    remindersEnabled: Boolean,
    notificationsAllowed: Boolean,
): ReminderStatus = when {
    !remindersEnabled -> ReminderStatus.OFF
    notificationsAllowed -> ReminderStatus.ON
    else -> ReminderStatus.BLOCKED
}
