package dev.memoji.flashcards.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one place a share waits between arriving at the activity and being picked up by the Add
 * Cards flow. What matters is that it is picked up once and that a newer share is never lost
 * to an older one being taken.
 */
class ShareInboxTest {

    private val inbox = ShareInbox()

    @Test
    fun `nothing is waiting until something is shared`() {
        assertNull(inbox.shared.value)
    }

    @Test
    fun `what was shared is what is waiting`() {
        inbox.offer("https://example.com/big-o")

        assertEquals("https://example.com/big-o", inbox.shared.value)
    }

    @Test
    fun `taking it leaves nothing waiting`() {
        inbox.offer("A selection.")

        inbox.take("A selection.")

        assertNull(inbox.shared.value)
    }

    /**
     * A second share arriving while the first is being picked up is the one the user meant.
     * Taking the first must not empty the inbox of the second.
     */
    @Test
    fun `taking one share does not take the share that replaced it`() {
        inbox.offer("The first.")
        inbox.offer("The second.")

        inbox.take("The first.")

        assertEquals("The second.", inbox.shared.value)
    }
}
