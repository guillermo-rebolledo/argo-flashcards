package dev.memoji.flashcards.feature.generate

import androidx.annotation.StringRes
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.generation.GeneratedCard
import dev.memoji.flashcards.core.generation.GenerationFailure
import dev.memoji.flashcards.core.generation.Source

/**
 * A generated Card and whether it is Kept. Kept Cards become real Cards on save; the rest are
 * dropped where they are — nothing here has been written to the database.
 */
internal data class ProposedCard(val card: GeneratedCard, val kept: Boolean = true)

/**
 * Where the Cards this flow produces are headed. Shown throughout, so the destination is never
 * something the user finds out about only after saving.
 */
internal sealed interface GenerateTarget {

    /** Nothing exists yet: the Deck is made on save, out of the name the user gives it. */
    data object NewDeck : GenerateTarget

    /**
     * A Deck that is already there. Its [name] is carried for the sake of saying which Deck;
     * the flow never writes it, because the Deck is already called something.
     */
    data class ExistingDeck(val id: Long, val name: String) : GenerateTarget
}

/** One Deck the target can be changed to, with what the picker shows about it. */
internal data class DeckOption(val id: Long, val name: String, val cardCount: Int)

/**
 * What a failure offers to do about itself.
 */
internal enum class FailureAction {

    /** No key set: the only one that is not really about this attempt. */
    OPEN_SETTINGS,

    /** Everything the user can just try again. */
    TRY_AGAIN,

    /**
     * The page did not open. Empties the box for the text the page would have held, which is
     * the one thing that reliably works when a link does not.
     */
    PASTE_TEXT,

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
        // The one failure the other flow is the answer to.
        GenerationFailure.PAGE_UNREADABLE ->
            FailureCopy(R.string.generate_error_page_unreadable, FailureAction.PASTE_TEXT)
        // Nothing to retry with — the material itself is what has to change.
        GenerationFailure.DECLINED ->
            FailureCopy(R.string.generate_error_declined, FailureAction.NONE)
        GenerationFailure.NO_CARDS ->
            FailureCopy(R.string.generate_error_no_cards, FailureAction.NONE)
    }

/** How far through the flow the user is. Where the Cards are headed sits beside it, not in it. */
internal sealed interface GenerateStep {

    /**
     * Where the flow starts and where a failure lands: the box to paste into, what was pasted,
     * and — once something has been tried — why it did not work.
     */
    data class Entry(
        val text: String = "",
        val failure: GenerationFailure? = null,
    ) : GenerateStep {

        /**
         * What the app made of what is in the box, shown before generating so the user can see
         * it read a link as a link. It is the same call the request is built from, so the hint
         * cannot promise one thing and the Generation do another.
         */
        val source: Source = Source.of(text)

        val isUrl: Boolean get() = source is Source.Url

        /** What the user can see they have given it. Whitespace of any kind separates words. */
        val wordCount: Int get() = if (text.isBlank()) 0 else text.trim().split(WHITESPACE).size

        val canGenerate: Boolean get() = text.isNotBlank()

        val failureCopy: FailureCopy? get() = failure?.copy
    }

    /** Nothing for the user to do, which the screen says out loud. */
    data object Busy : GenerateStep

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
    ) : GenerateStep {

        val keptCount: Int get() = cards.count(ProposedCard::kept)
    }
}

/**
 * The step and where its Cards are headed, together: whether a Deck name is asked for, and
 * whether there is anything to save, are questions only the two of them answer between them.
 */
internal data class GenerateUiState(
    val step: GenerateStep = GenerateStep.Entry(),
    val target: GenerateTarget = GenerateTarget.NewDeck,
    /** Every Deck the target can be changed to, for the picker. */
    val decks: List<DeckOption> = emptyList(),
) {

    /**
     * A Deck that exists is already called something, and this flow is not a way to rename it,
     * so the name field is not shown at all when the Cards are headed for one.
     */
    val isNamingNewDeck: Boolean get() = target is GenerateTarget.NewDeck

    /** The Cards are on their way to the Deck. Nothing about where they are going can move. */
    val isSaving: Boolean get() = (step as? GenerateStep.Proposed)?.saving == true

    /**
     * Nothing ticked is nothing to save; a new Deck also has to be called something, which an
     * existing one already is.
     */
    val canSave: Boolean
        get() {
            val proposed = step as? GenerateStep.Proposed ?: return false
            if (proposed.keptCount == 0 || isSaving) return false
            return !isNamingNewDeck || proposed.deckName.isNotBlank()
        }
}

private val WHITESPACE = Regex("\\s+")
