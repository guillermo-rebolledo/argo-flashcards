package dev.memoji.flashcards.core.model

import java.time.LocalDate

/**
 * Everything the Progress screen shows, counted from the Session log and from nothing else.
 *
 * The figures all cover the same seven days the grid does, so the screen reads as one answer to
 * one question rather than as four totals over four different windows.
 */
data class ProgressSummary(
    /** Consecutive days with at least one Session, counted back from the most recent one. */
    val dayStreak: Int,
    /** Seven days, oldest first; the last is today. */
    val week: List<ProgressDay>,
    val cardsReviewed: Int,
    val minutes: Int,
    val decksTouched: Int,
    /** The Decks studied in those seven days, most studied first. */
    val decks: List<DeckProgress>,
    /**
     * The most recent day in the window the user did not study, today aside — today has not
     * been skipped, it just has not happened yet. Null when there is no gap to speak to.
     *
     * The screen says this out loud, gently. A gap the app notices and does not mention reads
     * as a gap it is holding against you.
     */
    val skippedDay: LocalDate?,
)

/** One dot in the seven-day row. */
data class ProgressDay(
    val date: LocalDate,
    val studied: Boolean,
)

/** A Deck studied in the last seven days, and how much of it. */
data class DeckProgress(
    val deckId: Long,
    val name: String,
    val cardsReviewed: Int,
)
