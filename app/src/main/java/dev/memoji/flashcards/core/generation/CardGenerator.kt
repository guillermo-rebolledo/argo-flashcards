package dev.memoji.flashcards.core.generation

/**
 * The raw material a Deck is generated from: either pasted text or a URL. The user types into
 * one box and the app decides which of the two it got — [of] is that decision, and it is made
 * in one place so the hint shown before generating and the request sent after it cannot
 * disagree about what was pasted.
 */
sealed interface Source {

    data class PastedText(val text: String) : Source

    /** [url] always carries a scheme, even when what the user pasted started at `www.`. */
    data class Url(val url: String) : Source

    companion object {

        /**
         * A string that looks like a link is treated as one. It is not checked for being
         * reachable, or even for existing — that is the fetch's job, and its failure has its
         * own message. All this decides is which of the two things the user meant.
         */
        fun of(input: String): Source {
            val trimmed = input.trim()
            val match = LINK.matchEntire(trimmed) ?: return PastedText(input)
            // A link with no scheme is what a copied address bar or a spoken domain looks
            // like. https, not http: every host worth reading from speaks it.
            val scheme = match.groupValues[1]
            return Url(if (scheme.isEmpty()) "https://$trimmed" else trimmed)
        }

        /**
         * One token, a host with a real-looking suffix, and optionally a path. Deliberately
         * strict about the host: prose that happens to end in a full stop is not a link, and
         * being wrong in that direction costs the user a Generation.
         */
        private val LINK = Regex(
            "(https?://)?(?:[\\w-]+\\.)+[a-z]{2,}(?::\\d+)?(?:[/?#]\\S*)?",
            RegexOption.IGNORE_CASE,
        )
    }
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
     * The page behind the URL could not be read: a paywall, a login wall, a 404, or a page
     * that only exists once JavaScript has run. Only the URL flow can reach this, and the way
     * out of it is the other flow — pasting the text.
     */
    PAGE_UNREADABLE,

    /**
     * Anything else the API can return: a 500, a 529, a status this version does not know. Not
     * one of the seven the design names, but the alternative is a path with nothing to show.
     */
    UNEXPECTED,
}
