package dev.memoji.flashcards.core.domain

import dev.memoji.flashcards.core.model.DeckProgress
import dev.memoji.flashcards.core.model.ProgressDay
import dev.memoji.flashcards.core.model.ProgressSummary
import dev.memoji.flashcards.core.model.Session
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * Turns the Session log into what the Progress screen shows. Every figure comes from the same
 * rows, so no two of them can disagree, and nothing is kept anywhere to be updated in step.
 *
 * A Session belongs to the day it **started**. Someone who sits down at five to midnight studied
 * that evening, not the following morning, and their streak should say so.
 *
 * [deckNames] carries only the Decks that still exist. A deleted Deck drops out of the list
 * without taking its Cards or minutes with it — the sitting happened either way.
 */
fun summarizeProgress(
    sessions: List<Session>,
    deckNames: Map<Long, String>,
    clock: Clock,
): ProgressSummary {
    val today = LocalDate.now(clock)
    val dayOf = { session: Session -> session.started.atZone(clock.zone).toLocalDate() }
    val studiedDays = sessions.mapTo(mutableSetOf(), dayOf)

    val week = (WEEK - 1 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        ProgressDay(date = date, studied = date in studiedDays)
    }
    val thisWeek = sessions.filter { dayOf(it) >= week.first().date }
    val decksThisWeek = decksThisWeek(thisWeek, deckNames)

    return ProgressSummary(
        dayStreak = dayStreak(studiedDays, today),
        week = week,
        cardsReviewed = thisWeek.sumOf(Session::cardsReviewed),
        // Summed as durations and converted once: seven sittings of ninety seconds are
        // eighteen minutes, not seven roundings of one.
        minutes = thisWeek.fold(Duration.ZERO) { total, session -> total + session.duration }
            .toMinutes()
            .toInt(),
        // Counted off the list below rather than off the rows, so the tile and the list the
        // user reads it against can never say different numbers.
        decksTouched = decksThisWeek.size,
        decks = decksThisWeek,
        skippedDay = skippedDay(week, startedOn = studiedDays.minOrNull()),
    )
}

/**
 * The most recent day in the window the user did not study — today aside, which has not been
 * skipped so much as not happened yet, and the days before they started, which cannot be
 * skipped at all. Someone whose first Session was this morning has missed nothing.
 */
private fun skippedDay(week: List<ProgressDay>, startedOn: LocalDate?): LocalDate? {
    if (startedOn == null) return null
    return week.dropLast(1).lastOrNull { !it.studied && it.date > startedOn }?.date
}

/**
 * Consecutive days with at least one Session, counted back from today — or from yesterday when
 * today has not been studied yet. A streak that read zero every morning until the user opened
 * the app would be telling them they had lost something they still have.
 */
private fun dayStreak(studiedDays: Set<LocalDate>, today: LocalDate): Int {
    val anchor = listOf(today, today.minusDays(1)).firstOrNull { it in studiedDays } ?: return 0
    return generateSequence(anchor) { it.minusDays(1) }.takeWhile { it in studiedDays }.count()
}

/** Most studied first, then by name, so the list does not reshuffle between two equal Decks. */
private fun decksThisWeek(
    sessions: List<Session>,
    deckNames: Map<Long, String>,
): List<DeckProgress> = sessions
    .mapNotNull { session -> session.deckId?.let { it to session } }
    .groupBy({ it.first }, { it.second })
    .mapNotNull { (deckId, forDeck) ->
        val name = deckNames[deckId] ?: return@mapNotNull null
        DeckProgress(
            deckId = deckId,
            name = name,
            cardsReviewed = forDeck.sumOf(Session::cardsReviewed),
        )
    }
    .sortedWith(compareByDescending(DeckProgress::cardsReviewed).thenBy(DeckProgress::name))

/** Seven days, today included — the row of dots the design shows. */
private const val WEEK = 7
