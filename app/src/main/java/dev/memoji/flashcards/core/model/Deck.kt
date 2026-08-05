package dev.memoji.flashcards.core.model

import java.time.Instant

/** A named collection of Cards on one topic. The unit a Session is drawn from. */
data class Deck(
    val id: Long,
    val name: String,
    val createdAt: Instant,
)
