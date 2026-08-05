package dev.memoji.flashcards.core.domain

import dev.memoji.flashcards.core.model.Card
import java.time.Instant

/**
 * Which Cards a Session draws from a Deck, and in what order.
 *
 * Most slots go to Learning Cards, weakest first, so the user's attention lands on what they
 * do not know yet. Every fifth slot goes to a Mastered Card, so "Mastered" still means
 * something months later. When a category runs out its slots fall through to the other, so a
 * Deck with nothing Mastered, a Deck with nothing left to learn, and a Deck smaller than the
 * Session all give a full Session of whatever they have.
 *
 * The every-fifth rule is positional rather than probabilistic: the same Cards always compose
 * the same Session, so there is no `Random` to inject and nothing to seed in a test.
 */
fun composeSession(cards: List<Card>, length: Int): List<Card> {
    val learning = ArrayDeque(cards.filterNot(Card::isMastered).sortedWith(LEARNING_ORDER))
    val mastered = ArrayDeque(cards.filter(Card::isMastered).sortedWith(MASTERED_ORDER))

    return (1..length).mapNotNull { slot ->
        val preferred = if (slot % MASTERED_SLOT == 0) mastered else learning
        val fallback = if (preferred === mastered) learning else mastered
        preferred.removeFirstOrNull() ?: fallback.removeFirstOrNull()
    }
}

/** Slot five, then ten — the first four of any Session are Learning Cards. */
private const val MASTERED_SLOT = 5

/**
 * Weakest first, then whatever has waited longest. A Card that has never been through a
 * Review has waited longest of all, which is why nulls sort first. The id breaks the last
 * tie, so a Deck of new Cards composes the same Session on every read rather than the order
 * the rows happened to come back in.
 */
private val LEARNING_ORDER = compareBy<Card> { it.masteryStreak }
    .thenBy(nullsFirst<Instant>()) { it.lastSeenAt }
    .thenBy { it.id }

/** Streaks differ among Mastered Cards but say nothing useful; only the wait matters here. */
private val MASTERED_ORDER = compareBy<Card, Instant?>(nullsFirst()) { it.lastSeenAt }
    .thenBy { it.id }
