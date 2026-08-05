package dev.memoji.flashcards.core.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.memoji.flashcards.R
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The reminder carries no numbers, and this is the test that keeps it that way.
 *
 * Not fussiness about wording: per ADR 0001 nothing accumulates while the app is closed, so a
 * count in a reminder would be a number the app invented about a backlog it does not have.
 * Digits are the thing to look for — "12 cards waiting" and "you are 3 days behind" both fail
 * here, and so does any well-meant edit that reaches for a figure.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderCopyTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the reminder carries no counts`() {
        listOf(
            R.string.reminder_title,
            R.string.reminder_body,
            R.string.reminder_channel_name,
            R.string.reminder_channel_description,
        ).forEach { copy ->
            val text = context.getString(copy)

            assertFalse("\"$text\" has a number in it", text.any(Char::isDigit))
            assertFalse("\"$text\" takes a number", text.contains("%"))
        }
    }

    /**
     * The words a backlog would arrive in. A reminder that says something is waiting, overdue
     * or owed is making the promise the product exists to avoid making.
     */
    @Test
    fun `the reminder does not say anything is owed`() {
        val text = listOf(R.string.reminder_title, R.string.reminder_body)
            .joinToString(" ") { context.getString(it) }
            .lowercase()

        listOf("waiting for", "due", "overdue", "behind", "backlog", "streak", "missed", "don't lose")
            .forEach { assertFalse("the reminder says \"$it\"", text.contains(it)) }
    }
}
