package dev.memoji.flashcards.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `the bottom bar offers Decks, Progress and Settings in that order`() {
        assertEquals(
            listOf(
                TopLevelDestination.DECKS,
                TopLevelDestination.PROGRESS,
                TopLevelDestination.SETTINGS,
            ),
            TopLevelDestination.entries,
        )
    }

    @Test
    fun `the app starts on Decks`() {
        assertEquals(TopLevelDestination.DECKS, TopLevelDestination.START)
    }

    @Test
    fun `every destination has its own route`() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
        assertTrue(routes.none { it.isBlank() })
    }

    @Test
    fun `a route resolves back to the destination that owns it`() {
        TopLevelDestination.entries.forEach { destination ->
            assertEquals(destination, TopLevelDestination.forRoute(destination.route))
        }
    }

    @Test
    fun `a route belonging to no top-level destination resolves to null`() {
        assertEquals(null, TopLevelDestination.forRoute("deck/42"))
        assertEquals(null, TopLevelDestination.forRoute(null))
    }
}
