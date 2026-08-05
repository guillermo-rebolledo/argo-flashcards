package dev.memoji.flashcards.core.generation

/**
 * The raw material a Deck is generated from. Pasted text is the only Source the app takes
 * today; a URL is a member this gains later, which is why this is a sealed type over one case
 * rather than a bare `String`.
 */
sealed interface Source {

    data class PastedText(val text: String) : Source
}

/**
 * One proposed Card, before the user has decided whether to keep it. It has no id and no
 * Mastery streak because it is not a Card yet — it becomes one only if it is Kept and saved.
 */
data class GeneratedCard(val front: String, val back: String)

/**
 * The app's one behavioural seam: a Source in, a Deck name and Cards out, or a typed failure.
 *
 * The interface exists so that every flow that depends on Generation — including all of its
 * failures — is testable with no key and no network, and so that a second provider would be a
 * new class rather than a refactor. See ADR 0002; exactly one implementation ships.
 */
interface CardGenerator {

    suspend fun generate(source: Source): GenerationResult
}

sealed interface GenerationResult {

    /** [cards] is what the model proposed, in the order it proposed them. */
    data class Generated(val deckName: String, val cards: List<GeneratedCard>) : GenerationResult

    data class Failed(val failure: GenerationFailure) : GenerationResult
}

/**
 * Every way a Generation can end without Cards. Each one is a separate member because each one
 * has its own thing to say and its own way forward — a single "something went wrong" would
 * leave the user with no idea whether to check their key, their connection, or their text.
 *
 * The screen owns the wording; what belongs here is only the distinction between them.
 */
enum class GenerationFailure {

    /** No key has been entered yet. The first launch lands here — see ADR 0002. */
    NO_KEY_SET,

    /** The key was rejected. What was wrong was the credential, not the pasted text. */
    INVALID_KEY,

    RATE_LIMITED,

    OFFLINE,

    TIMEOUT,

    /**
     * The model declined the request. It arrives as a perfectly successful HTTP 200 carrying a
     * refusal, so it is an outcome of a Generation rather than an error in making one.
     */
    DECLINED,

    /**
     * The request worked and produced no Card worth keeping — material too short, or too
     * abstract to state in single sentences. A refusal is [DECLINED], not this.
     */
    NO_CARDS,

    /**
     * Anything else the API can return: a 500, a 529, a status this version does not know. Not
     * one of the seven the design names, but the alternative is a path with nothing to show.
     */
    UNEXPECTED,
}
