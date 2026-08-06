package dev.memoji.flashcards.core.share

import android.content.Intent
import android.text.SpannableString
import dev.memoji.flashcards.core.generation.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real Intents, built the way the apps that share into this one build them: a browser sending
 * a link, a reader sending a selection. What is being tested is the reading, so the Intents
 * are the real thing rather than a stand-in for one.
 */
@RunWith(RobolectricTestRunner::class)
class SharedTextTest {

    @Test
    fun `shared text is what was sent`() {
        val intent = send("Big-O notation describes how work grows with input size.")

        assertEquals(
            "Big-O notation describes how work grows with input size.",
            SharedText.of(intent),
        )
    }

    /**
     * A browser shares a link as text. Which of the two it is stays [Source.of]'s decision,
     * made in the same place for a share as for a paste — so a shared link is a Url without
     * this having to know anything about links.
     */
    @Test
    fun `a shared link reads as a link`() {
        val shared = SharedText.of(send("https://example.com/big-o"))

        assertEquals(Source.Url("https://example.com/big-o"), Source.of(shared!!))
    }

    /** Chrome sends the page title alongside the link. The link is what a Deck is made from. */
    @Test
    fun `the subject a browser sends alongside a link is not the Source`() {
        val intent = send("https://example.com/big-o")
            .putExtra(Intent.EXTRA_SUBJECT, "Big-O notation — Example")

        assertEquals("https://example.com/big-o", SharedText.of(intent))
    }

    /** Selected text arrives styled from some apps; the styling is not part of the Source. */
    @Test
    fun `styled text is read as text`() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, SpannableString("A selection."))

        assertEquals("A selection.", SharedText.of(intent))
    }

    @Test
    fun `launching the app is not a share`() {
        assertNull(SharedText.of(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun `a share of something that is not text carries no Source`() {
        val intent = Intent(Intent.ACTION_SEND).setType("image/png")

        assertNull(SharedText.of(intent))
    }

    @Test
    fun `a share with nothing in it carries no Source`() {
        assertNull(SharedText.of(Intent(Intent.ACTION_SEND).setType("text/plain")))
    }

    /** Whitespace is nothing to generate from, and would open the flow with an empty box. */
    @Test
    fun `blank shared text carries no Source`() {
        assertNull(SharedText.of(send("   \n  ")))
    }

    private fun send(text: String) = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
}
