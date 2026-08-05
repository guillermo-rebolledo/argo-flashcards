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
 * What a failure offers to do about itself.
 */
internal enum class FailureAction {

    /** No key set: the only one that is not really about this attempt. */
    OPEN_SETTINGS,

    /** Everything the user can just try again. */
    TRY_AGAIN,

    /** Nothing to retry with — the text itself is what has to change. */
    NONE,
}

/**
 * What a failure says and what it offers to do about it. Both answers come out of one `when`,
 * so a failure added later cannot be given a message and left without a way forward.
 *
 * It lives here rather than beside [GenerationFailure] because the wording is the screen's:
 * nothing under `core` knows about string resources.
 */
internal data class FailureCopy(@param:StringRes val messageRes: Int, val action: FailureAction)

internal val GenerationFailure.copy: FailureCopy
    get() = when (this) {
        // The only one that is not about this attempt at all.
        GenerationFailure.NO_KEY_SET ->
            FailureCopy(R.string.generate_error_no_key, FailureAction.OPEN_SETTINGS)
        GenerationFailure.INVALID_KEY ->
            FailureCopy(R.string.generate_error_invalid_key, FailureAction.OPEN_SETTINGS)
        GenerationFailure.RATE_LIMITED ->
            FailureCopy(R.string.generate_error_rate_limited, FailureAction.TRY_AGAIN)
        GenerationFailure.OFFLINE ->
            FailureCopy(R.string.generate_error_offline, FailureAction.TRY_AGAIN)
        GenerationFailure.TIMEOUT ->
            FailureCopy(R.string.generate_error_timeout, FailureAction.TRY_AGAIN)
        GenerationFailure.UNEXPECTED ->
            FailureCopy(R.string.generate_error_unexpected, FailureAction.TRY_AGAIN)
        // Nothing to retry with — the material itself is what has to change.
        GenerationFailure.DECLINED ->
            FailureCopy(R.string.generate_error_declined, FailureAction.NONE)
        GenerationFailure.NO_CARDS ->
            FailureCopy(R.string.generate_error_no_cards, FailureAction.NONE)
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

        val failureCopy: FailureCopy? get() = failure?.copy
    }

    /** Nothing for the user to do, which the screen says out loud. */
    data object Busy : GenerateUiState

    /**
     * The proposed Cards, held in memory, waiting to be Kept. Unticking one drops it here and
     * nowhere else — an unkept Card is never written.
     *
     * Not called Review: in this app that word is the phase of a Session where Cards are shown
     * and Graded, and it means only that.
     */
    data class Proposed(
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
