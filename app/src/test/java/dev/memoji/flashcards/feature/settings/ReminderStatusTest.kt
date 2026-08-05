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
}
