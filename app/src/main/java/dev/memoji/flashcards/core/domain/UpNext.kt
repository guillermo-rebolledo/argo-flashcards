package dev.memoji.flashcards.core.domain

import dev.memoji.flashcards.core.model.DeckSummary
import java.time.Instant

/**
 * Which Deck the home screen offers to study, or null when there is nothing to study.
 *
 * The user should be able to guess what will happen before they tap, so the answer is the Deck
 * they studied most recently. Two things move it on: a Deck with no Cards is not a Session, and
 * a Deck with nothing left to learn is not where an evening should go — so a fully Mastered
 * Deck steps aside for the next one that still has Learning Cards. Before anything has been
 * studied, the newest Deck is the one they just made, which is the one they meant.
 *
 * If every Deck is fully Mastered, the most recent of them is offered anyway: an all-Mastered
 * Session is a real Session, and the alternative is a home screen with nothing on it.
 */
fun upNextDeck(summaries: List<DeckSummary>): DeckSummary? {
    val studiable = summaries.filter(DeckSummary::hasCards).sortedWith(UP_NEXT_ORDER)
    return studiable.firstOrNull { !it.isFullyMastered } ?: studiable.firstOrNull()
}

/**
 * Most recently studied first; a Deck never studied sorts below every Deck that has been, and
 * among those the newest comes first. The id breaks the last tie so the home screen does not
 * change what it offers between two identical reads.
 */
private val UP_NEXT_ORDER = compareByDescending<DeckSummary, Instant?>(nullsFirst()) {
    it.lastStudiedAt
}
    .thenByDescending { it.deck.createdAt }
    .thenByDescending { it.deck.id }
