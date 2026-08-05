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

/** What flipping the switch is asking for. */
internal enum class ReminderSwitchAction { TURN_ON, TURN_OFF, ASK_FOR_PERMISSION }

/**
 * The switch reports the position it moved to, which says nothing the [status] does not
 * already say — so the decision is made from the status alone.
 *
 * [BLOCKED][ReminderStatus.BLOCKED] is the case worth naming. Its switch is drawn off while
 * the setting underneath is on, so the only move available on it reads as "turn on" and would
 * loop straight back into a permission Android has already refused. It turns the setting off
 * instead: a user who has given up on reminders must have a way to plainly off, and the row
 * itself is what offers the retry, by opening the one screen that can undo the block.
 */
internal fun reminderSwitchAction(
    status: ReminderStatus,
    /** Whether this version of Android has a notification permission to ask for at all. */
    permissionNeeded: Boolean,
): ReminderSwitchAction = when (status) {
    ReminderStatus.ON, ReminderStatus.BLOCKED -> ReminderSwitchAction.TURN_OFF
    ReminderStatus.OFF ->
        if (permissionNeeded) {
            ReminderSwitchAction.ASK_FOR_PERMISSION
        } else {
            ReminderSwitchAction.TURN_ON
        }
}
