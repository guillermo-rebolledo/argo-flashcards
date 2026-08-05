package dev.memoji.flashcards.core.generation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the user pasted, read one way or the other. The cost of the two mistakes is not the
 * same: prose read as a link is a Generation spent fetching nothing, so the rule leans towards
 * text and only calls something a link when it really looks like one.
 */
class SourceTest {

    @Test
    fun `a full address is a URL`() {
        assertEquals(
            Source.Url("https://en.wikipedia.org/wiki/Big_O_notation"),
            Source.of("https://en.wikipedia.org/wiki/Big_O_notation"),
        )
    }

    /** What copying an address out of a browser or writing one from memory looks like. */
    @Test
    fun `an address with no scheme gets one`() {
        assertEquals(Source.Url("https://www.example.com"), Source.of("www.example.com"))
    }

    @Test
    fun `a bare domain is a URL`() {
        assertEquals(Source.Url("https://example.com/notes"), Source.of("example.com/notes"))
    }

    @Test
    fun `http is left as it was pasted`() {
        assertEquals(Source.Url("http://example.com"), Source.of("http://example.com"))
    }

    @Test
    fun `surrounding whitespace does not stop it being a URL`() {
        assertEquals(Source.Url("https://example.com"), Source.of("  https://example.com \n"))
    }

    @Test
    fun `a paragraph is text`() {
        val text = "Big-O notation describes how work grows with input size."
        assertEquals(Source.PastedText(text), Source.of(text))
    }

    /** A sentence ending in a full stop is the mistake worth not making. */
    @Test
    fun `a single word ending in a full stop is text`() {
        assertEquals(Source.PastedText("notation."), Source.of("notation."))
    }

    @Test
    fun `an abbreviation is text`() {
        assertEquals(Source.PastedText("e.g."), Source.of("e.g."))
    }

    /** A link with a sentence around it is a paste of text that happens to mention one. */
    @Test
    fun `text containing a link is text`() {
        val text = "Read https://example.com and make cards"
        assertEquals(Source.PastedText(text), Source.of(text))
    }

    @Test
    fun `text keeps exactly what was typed`() {
        assertEquals(Source.PastedText("  spaced  "), Source.of("  spaced  "))
    }
}
