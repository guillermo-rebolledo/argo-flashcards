package dev.memoji.flashcards.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.memoji.flashcards.core.coroutines.ApplicationScope
import dev.memoji.flashcards.core.data.CardRepository
import dev.memoji.flashcards.core.data.SessionRepository
import dev.memoji.flashcards.core.data.SettingsRepository
import dev.memoji.flashcards.core.domain.composeSession
import dev.memoji.flashcards.core.model.Card
import dev.memoji.flashcards.core.model.Grade
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One sitting. The Cards are drawn once, when the Session starts, and held here for as long as
 * it lasts: a Session the user is part-way through must not reshuffle underneath them because
 * a Grade they just gave changed what the Deck would compose now.
 *
 * The sitting is also what Progress is counted from, so it leaves exactly one row in the
 * Session log when it ends, however it ended.
 */
@HiltViewModel
internal class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cardRepository: CardRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle[SessionRoute.DECK_ID_ARG]) {
        "A Session was started without a Deck id"
    }

    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var cards: List<Card> = emptyList()
    private var position = 0
    private var knewIt = 0
    private val misses = mutableListOf<Card>()

    /** When the user sat down, which is the day the whole sitting belongs to. */
    private val startedAt: Instant = clock.instant()

    /**
     * What the passes before this one came to. [position] and [knewIt] are the pass in hand and
     * are reset by each one, because the score on the results screen is that pass alone; the
     * sitting is all of them, and is what the Session log records.
     */
    private var carriedReviewed = 0
    private var carriedKnewIt = 0
    private var recorded = false

    init {
        viewModelScope.launch {
            val length = settingsRepository.observeSettings().first().sessionLength.cards
            // A Deck deleted before the Session started draws nothing, because the database
            // took its Cards with it — so there is no separate check for one.
            begin(composeSession(cardRepository.cardsInDeck(deckId), length))
        }
    }

    /**
     * Revealing is deliberate — the Back is not shown until the user asks for it. Asking again
     * puts it away, so a Card opened by accident can be closed and still attempted.
     */
    fun toggleReveal() {
        val reviewing = _uiState.value as? SessionUiState.Reviewing ?: return
        _uiState.value = reviewing.copy(revealed = !reviewing.revealed)
    }

    /**
     * Writes the Grade and moves on. The Card is graded whether or not its Back was revealed:
     * a user who knew it on sight has no reason to look.
     */
    fun grade(grade: Grade) {
        val reviewing = _uiState.value as? SessionUiState.Reviewing ?: return
        val card = reviewing.card

        when (grade) {
            Grade.KNEW_IT -> knewIt++
            Grade.AGAIN -> misses += card
        }
        // Deliberately not `viewModelScope`: the user can leave the moment they have graded,
        // and a Grade that is dropped because the screen went away is the silent data loss
        // this whole feature is meant to avoid.
        applicationScope.launch { cardRepository.recordGrade(card.id, grade) }

        position++
        _uiState.value = if (position < cards.size) {
            reviewing.copy(card = cards[position], revealed = false, position = position + 1)
        } else {
            SessionUiState.Finished(
                knewIt = knewIt,
                total = cards.size,
                misses = misses.toList(),
            )
        }
    }

    /**
     * A second Session over the Cards that did not come back, scored on its own. Closing the
     * loop while the material is fresh is the point, so nothing is drawn from the Deck around
     * them — these Cards and no others.
     */
    fun reviewMisses() {
        val finished = _uiState.value as? SessionUiState.Finished ?: return
        if (finished.misses.isEmpty()) return
        begin(finished.misses)
    }

    /**
     * Writes the sitting to the Session log, once. Called when the screen goes away rather than
     * from a button, because every way out of a Session is the same sitting ending: Done, the
     * close button, and the system Back all arrive here, and none of them needs to remember to.
     *
     * The process being killed does not, and no row is written for a Session that ends that
     * way — the Grades themselves are already on disk, which is the part that would be missed.
     * A Session nobody Graded a Card in is not a sitting and leaves no row either.
     */
    fun recordSession() {
        val reviewed = carriedReviewed + position
        if (recorded || reviewed == 0) return
        recorded = true
        val knewIt = carriedKnewIt + this.knewIt
        // As with a Grade, deliberately not `viewModelScope`: by the time this runs the screen
        // is already on its way out, and the scope with it.
        applicationScope.launch {
            sessionRepository.recordSession(deckId, startedAt, reviewed, knewIt)
        }
    }

    override fun onCleared() {
        recordSession()
    }

    private fun begin(session: List<Card>) {
        // Whatever the pass just finished came to goes into the sitting's running total before
        // the counters that hold it are reset under the next pass.
        carriedReviewed += position
        carriedKnewIt += knewIt

        cards = session
        position = 0
        knewIt = 0
        misses.clear()
        _uiState.value = if (session.isEmpty()) {
            SessionUiState.Empty
        } else {
            SessionUiState.Reviewing(
                card = session.first(),
                revealed = false,
                position = 1,
                total = session.size,
            )
        }
    }
}
