package dev.memoji.flashcards.feature.generate

import androidx.annotation.StringRes
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.generation.GeneratedCard
import dev.memoji.flashcards.core.generation.GenerationFailure

/**
 * A generated Card and whether it is Kept. Kept Cards become real Cards on save; the rest are
 * dropped where they are — nothing here has been written to the database.
 */
internal data class ProposedCard(val card: GeneratedCard, val kept: Boolean = true)

/**
 * What each failure says and what it offers to do about it. The two live together so that a
 * new failure cannot be added without answering both questions, and so the screen has one
 * `when` instead of three.
 */
internal enum class FailureAction {

    /** No key set: the only one that is not really about this attempt. */
    OPEN_SETTINGS,

    /** Everything the user can just try again. */
    TRY_AGAIN,

    /** Nothing to retry with — the text itself is what has to change. */
    NONE,
    ;

    companion object {

        fun of(failure: GenerationFailure) = when (failure) {
            GenerationFailure.NO_KEY_SET -> OPEN_SETTINGS
            GenerationFailure.INVALID_KEY -> OPEN_SETTINGS
            GenerationFailure.RATE_LIMITED,
            GenerationFailure.OFFLINE,
            GenerationFailure.TIMEOUT,
            GenerationFailure.UNEXPECTED,
            -> TRY_AGAIN
            GenerationFailure.DECLINED, GenerationFailure.NO_CARDS -> NONE
        }
    }
}

/** Each failure says its own thing. A shared "something went wrong" would help nobody. */
@get:StringRes
internal val GenerationFailure.messageRes: Int
    get() = when (this) {
        GenerationFailure.NO_KEY_SET -> R.string.generate_error_no_key
        GenerationFailure.INVALID_KEY -> R.string.generate_error_invalid_key
        GenerationFailure.RATE_LIMITED -> R.string.generate_error_rate_limited
        GenerationFailure.OFFLINE -> R.string.generate_error_offline
        GenerationFailure.TIMEOUT -> R.string.generate_error_timeout
        GenerationFailure.DECLINED -> R.string.generate_error_declined
        GenerationFailure.NO_CARDS -> R.string.generate_error_no_cards
        GenerationFailure.UNEXPECTED -> R.string.generate_error_unexpected
    }

internal sealed interface GenerateUiState {

    /**
     * Where the flow starts and where a failure lands: the box to paste into, what was pasted,
     * and — once something has been tried — why it did not work.
     */
    data class Entry(
        val text: String = "",
        val failure: GenerationFailure? = null,
    ) : GenerateUiState {

        /** What the user can see they have given it. Whitespace of any kind separates words. */
        val wordCount: Int get() = if (text.isBlank()) 0 else text.trim().split(WHITESPACE).size

        val canGenerate: Boolean get() = text.isNotBlank()

        val failureAction: FailureAction? get() = failure?.let(FailureAction::of)
    }

    /** Nothing for the user to do, which the screen says out loud. */
    data object Busy : GenerateUiState

    /**
     * The proposed Cards, held in memory. Unticking one drops it here and nowhere else — an
     * unkept Card is never written.
     */
    data class Review(
        val deckName: String,
        val cards: List<ProposedCard>,
        /** Kept so that backing out of a Generation does not mean pasting it all again. */
        val sourceText: String,
        val saving: Boolean = false,
    ) : GenerateUiState {

        val keptCount: Int get() = cards.count(ProposedCard::kept)

        /** Nothing ticked is nothing to save, and a Deck has to be called something. */
        val canSave: Boolean get() = keptCount > 0 && deckName.isNotBlank() && !saving
    }
}

private val WHITESPACE = Regex("\\s+")
