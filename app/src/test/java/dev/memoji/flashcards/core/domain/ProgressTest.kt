package dev.memoji.flashcards.core.domain

import dev.memoji.flashcards.core.model.ProgressDay
import dev.memoji.flashcards.core.model.Session
import dev.memoji.flashcards.core.testing.MutableClock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every figure the Progress screen shows, against a clock the test moves by hand. The day
 * boundaries are the whole difficulty here — a Session at five to midnight and a phone that
 * changes timezone are both a day apart from what they look like.
 */
class ProgressTest {

    /** Nine in the morning on a Tuesday, in UTC, which is where [MutableClock] starts. */
    private val today: LocalDate = LocalDate.ofInstant(MutableClock.START, ZoneOffset.UTC)

    @Test
    fun `the streak counts consecutive days with at least one Session`() {
        val summary = summarize(sessionsOn(daysAgo = listOf(0, 1, 2)))

        assertEquals(3, summary.dayStreak)
    }

    @Test
    fun `a skipped day breaks the streak, and only the days since it are counted`() {
        val summary = summarize(sessionsOn(daysAgo = listOf(0, 1, 3, 4)))

        assertEquals(2, summary.dayStreak)
    }

    /**
     * Nobody has studied yet at breakfast. A streak that read zero until they did would be
     * telling them they had lost something they still have.
     */
    @Test
    fun `a streak survives a today the user has not studied yet`() {
        val summary = summarize(sessionsOn(daysAgo = listOf(1, 2, 3)))

        assertEquals(3, summary.dayStreak)
    }

    @Test
    fun `a gap of two days leaves no streak at all`() {
        val summary = summarize(sessionsOn(daysAgo = listOf(2, 3, 4)))

        assertEquals(0, summary.dayStreak)
    }

    @Test
    fun `several Sessions in one day are still one day of the streak`() {
        val summary = summarize(sessionsOn(daysAgo = listOf(0, 0, 0, 1)))

        assertEquals(2, summary.dayStreak)
    }

    /** A Session belongs to the day the user sat down on, not the day it happened to end. */
    @Test
    fun `a Session started before midnight counts on the day it started`() {
        val lastNight = at(daysAgo = 1, hour = 23, minute = 55)
        val session = session(started = lastNight, duration = Duration.ofMinutes(10))
        // It ended today, and today has nothing else in it.
        assertEquals(today, LocalDate.ofInstant(session.ended, ZoneOffset.UTC))

        val summary = summarize(listOf(session))

        assertEquals(1, summary.dayStreak)
        assertEquals(listOf(today.minusDays(1)), summary.week.filter(ProgressDay::studied).map { it.date })
    }

    /**
     * The whole log moves together when the phone changes zone, so a run of daily Sessions is
     * still a run of daily Sessions after the flight.
     */
    @Test
    fun `a streak holds across a change of timezone`() {
        val sessions = sessionsOn(daysAgo = listOf(0, 1, 2, 3))

        val inTokyo = summarize(sessions, clock = fixedClock(ZoneId.of("Asia/Tokyo")))
        val inSantiago = summarize(sessions, clock = fixedClock(ZoneId.of("America/Santiago")))

        assertEquals(4, inTokyo.dayStreak)
        assertEquals(4, inSantiago.dayStreak)
    }

    @Test
    fun `the seven-day grid runs to today and marks the days that were studied`() {
        val summary = summarize(sessionsOn(daysAgo = listOf(0, 2, 6)))

        assertEquals(7, summary.week.size)
        assertEquals(today.minusDays(6), summary.week.first().date)
        assertEquals(today, summary.week.last().date)
        assertEquals(
            listOf(true, false, false, false, true, false, true),
            summary.week.map(ProgressDay::studied),
        )
    }

    @Test
    fun `a Session older than the window is not marked on the grid`() {
        val summary = summarize(sessionsOn(daysAgo = listOf(7)))

        assertEquals(listOf(false), summary.week.map(ProgressDay::studied).distinct())
    }

    @Test
    fun `the tiles add up the Sessions in the window and no others`() {
        val summary = summarize(
            listOf(
                session(at(daysAgo = 0), Duration.ofMinutes(3), deckId = 1L, cardsReviewed = 5),
                session(at(daysAgo = 2), Duration.ofMinutes(4), deckId = 2L, cardsReviewed = 10),
                session(at(daysAgo = 6), Duration.ofMinutes(1), deckId = 1L, cardsReviewed = 3),
                // Eight days back: outside the window, and counted in nothing.
                session(at(daysAgo = 8), Duration.ofMinutes(9), deckId = 3L, cardsReviewed = 40),
            ),
            deckNames = mapOf(1L to "Big-O notation", 2L to "Git basics", 3L to "React hooks"),
        )

        assertEquals(18, summary.cardsReviewed)
        assertEquals(8, summary.minutes)
        assertEquals(2, summary.decksTouched)
    }

    /** Rounded once, at the end: seven sittings of ninety seconds are eighteen minutes. */
    @Test
    fun `minutes are summed before they are rounded`() {
        val summary = summarize(
            List(7) { session(at(daysAgo = 0), Duration.ofSeconds(90)) },
        )

        assertEquals(10, summary.minutes)
    }

    /** A phone put down mid-Session must not report the afternoon it spent in a pocket. */
    @Test
    fun `a backgrounded Session contributes its ceiling and no more`() {
        val summary = summarize(listOf(session(at(daysAgo = 0), Duration.ofHours(8))))

        assertEquals(Session.MAX_DURATION.toMinutes().toInt(), summary.minutes)
    }

    @Test
    fun `Decks touched this week are listed, most studied first`() {
        val summary = summarize(
            listOf(
                session(at(daysAgo = 0), Duration.ofMinutes(2), deckId = 1L, cardsReviewed = 5),
                session(at(daysAgo = 1), Duration.ofMinutes(2), deckId = 2L, cardsReviewed = 10),
                session(at(daysAgo = 2), Duration.ofMinutes(2), deckId = 1L, cardsReviewed = 5),
                session(at(daysAgo = 9), Duration.ofMinutes(2), deckId = 3L, cardsReviewed = 40),
            ),
            deckNames = mapOf(1L to "Big-O notation", 2L to "Git basics", 3L to "React hooks"),
        )

        assertEquals(listOf("Big-O notation", "Git basics"), summary.decks.map { it.name })
        assertEquals(listOf(10, 10), summary.decks.map { it.cardsReviewed })
    }

    /**
     * The Deck is gone but the sitting is not: it drops out of the list without taking the
     * Cards, the minutes, or the day off the user's streak.
     */
    @Test
    fun `a deleted Deck leaves the list but not the totals`() {
        val summary = summarize(
            listOf(session(at(daysAgo = 0), Duration.ofMinutes(2), deckId = null, cardsReviewed = 5)),
            deckNames = emptyMap(),
        )

        assertEquals(emptyList<String>(), summary.decks.map { it.name })
        assertEquals(5, summary.cardsReviewed)
        assertEquals(0, summary.decksTouched)
        assertEquals(1, summary.dayStreak)
    }

    @Test
    fun `the skipped day is the most recent one behind today`() {
        val summary = summarize(sessionsOn(daysAgo = listOf(0, 1, 3, 5)))

        assertEquals(today.minusDays(2), summary.skippedDay)
    }

    /**
     * The days before the user's first Session are not days they skipped. Greeting someone
     * whose first Session was this morning with "You skipped Sunday" would be the app telling
     * them off for not having installed it yet.
     */
    @Test
    fun `the days before the first Session were not skipped`() {
        val summary = summarize(sessionsOn(daysAgo = listOf(0)))

        assertNull(summary.skippedDay)
    }

    /** Today is not skipped. It has not happened yet. */
    @Test
    fun `a full week with nothing studied today has no skipped day to speak of`() {
        val summary = summarize(sessionsOn(daysAgo = listOf(1, 2, 3, 4, 5, 6)))

        assertNull(summary.skippedDay)
    }

    private fun summarize(
        sessions: List<Session>,
        deckNames: Map<Long, String> = mapOf(DECK_ID to "Big-O notation"),
        clock: Clock = MutableClock(),
    ) = summarizeProgress(sessions = sessions, deckNames = deckNames, clock = clock)

    /** One Session on each of the given days, all at nine in the morning. */
    private fun sessionsOn(daysAgo: List<Int>): List<Session> =
        daysAgo.map { session(at(daysAgo = it), Duration.ofMinutes(2)) }

    private fun at(daysAgo: Int, hour: Int = 9, minute: Int = 0): Instant =
        LocalDateTime.of(today.minusDays(daysAgo.toLong()), LocalTime.of(hour, minute))
            .toInstant(ZoneOffset.UTC)

    private fun session(
        started: Instant,
        duration: Duration,
        deckId: Long? = DECK_ID,
        cardsReviewed: Int = 5,
    ) = Session(
        id = nextId++,
        deckId = deckId,
        started = started,
        ended = started.plus(duration),
        cardsReviewed = cardsReviewed,
        knewIt = cardsReviewed,
    )

    private var nextId = 1L

    /** Now, in a zone of the test's choosing, so the same rows can be read from two places. */
    private fun fixedClock(zone: ZoneId): Clock = Clock.fixed(MutableClock.START, zone)

    private companion object {
        const val DECK_ID = 1L
    }
}
