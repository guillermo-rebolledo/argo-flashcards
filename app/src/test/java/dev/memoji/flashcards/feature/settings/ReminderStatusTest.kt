package dev.memoji.flashcards.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that keeps the switch honest. Every combination is here because the interesting
 * one — asked for, refused by the system — is the one an app is most tempted to draw as on.
 */
class ReminderStatusTest {

    @Test
    fun `not asked for is off`() {
        assertEquals(
            ReminderStatus.OFF,
            reminderStatus(remindersEnabled = false, notificationsAllowed = true),
        )
    }

    @Test
    fun `asked for and allowed is on`() {
        assertEquals(
            ReminderStatus.ON,
            reminderStatus(remindersEnabled = true, notificationsAllowed = true),
        )
    }

    /** The switch must not read as on while Android is dropping every notification. */
    @Test
    fun `asked for but not allowed is blocked, never on`() {
        assertEquals(
            ReminderStatus.BLOCKED,
            reminderStatus(remindersEnabled = true, notificationsAllowed = false),
        )
    }

    /**
     * Nothing is being blocked from someone who never asked. A fresh install on Android 13 has
     * no notification permission and must not be told the app is being blocked.
     */
    @Test
    fun `never asked for is off even where notifications are not allowed`() {
        assertEquals(
            ReminderStatus.OFF,
            reminderStatus(remindersEnabled = false, notificationsAllowed = false),
        )
    }

    @Test
    fun `flipping an off switch asks for the permission where there is one to ask for`() {
        assertEquals(
            ReminderSwitchAction.ASK_FOR_PERMISSION,
            reminderSwitchAction(ReminderStatus.OFF, permissionNeeded = true),
        )
    }

    /** Before Android 13 there is nothing to ask for, so the switch simply turns it on. */
    @Test
    fun `flipping an off switch turns reminders on where no permission exists`() {
        assertEquals(
            ReminderSwitchAction.TURN_ON,
            reminderSwitchAction(ReminderStatus.OFF, permissionNeeded = false),
        )
    }

    @Test
    fun `flipping an on switch turns reminders off`() {
        assertEquals(
            ReminderSwitchAction.TURN_OFF,
            reminderSwitchAction(ReminderStatus.ON, permissionNeeded = true),
        )
    }

    /**
     * Off has to be reachable from blocked. The switch is drawn off there while the setting
     * is on, so without this the only move available would ask again for a permission Android
     * has already refused, and the user could never get back to plainly off.
     */
    @Test
    fun `flipping a blocked switch turns reminders off rather than asking again`() {
        assertEquals(
            ReminderSwitchAction.TURN_OFF,
            reminderSwitchAction(ReminderStatus.BLOCKED, permissionNeeded = true),
        )
    }
}
