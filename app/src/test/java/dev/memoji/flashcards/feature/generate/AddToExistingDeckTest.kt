package dev.memoji.flashcards.feature.generate

import androidx.lifecycle.SavedStateHandle
import dev.memoji.flashcards.core.data.FakeCardRepository
import dev.memoji.flashcards.core.data.FakeDeckRepository
import dev.memoji.flashcards.core.data.deckIds
import dev.memoji.flashcards.core.data.deckNames
import dev.memoji.flashcards.core.generation.FakeCardGenerator
import dev.memoji.flashcards.core.share.ShareInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Add Cards flow aimed at a Deck that already exists. Both sources — generated and written
 * by hand — reach both destinations, and the Cards already in the Deck are left alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddToExistingDeckTest {

    private val generator = FakeCardGenerator()
    private val cardRepository = FakeCardRepository()
    private val deckRepository = FakeDeckRepository(cardRepository)

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun restoreDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `entered from the Decks screen, the target is a new Deck`() = runTest {
        assertEquals(GenerateTarget.NewDeck, viewModel().uiState.value.target)
    }

    @Test
    fun `entered from a Deck, that Deck is the target and is named`() = runTest {
        val deckId = deckRepository.createDeck("Git basics")

        val target = viewModel(deckId).uiState.value.target

        assertEquals(GenerateTarget.ExistingDeck(deckId, "Git basics"), target)
    }

    /** The name already exists, and this flow must not be a way to change it. */
    @Test
    fun `an existing Deck is not named again`() = runTest {
        val deckId = deckRepository.createDeck("Git basics")
        val viewModel = generatedViewModel(deckId)

        assertFalse(viewModel.uiState.value.isNamingNewDeck)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `a new Deck is named before it can be saved`() = runTest {
        val viewModel = generatedViewModel()

        assertTrue(viewModel.uiState.value.isNamingNewDeck)
        viewModel.setDeckName("  ")
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `generated Cards are saved into the existing Deck, Kept ones only`() = runTest {
        val deckId = deckRepository.createDeck("Big-O")
        cardRepository.createCard(deckId, "Already here", "From before.")
        val viewModel = generatedViewModel(deckId)

        viewModel.setKept(1, false)
        viewModel.save()

        assertEquals(
            listOf("Already here", "O(1)", "O(log n)"),
            cardRepository.cardsInDeck(deckId).map { it.front },
        )
        assertEquals(listOf(deckId), deckRepository.deckIds())
        assertEquals(deckId, viewModel.savedDeckId.value)
    }

    @Test
    fun `Cards added to an existing Deck start at a Mastery streak of zero`() = runTest {
        val deckId = deckRepository.createDeck("Big-O")
        val viewModel = generatedViewModel(deckId)

        viewModel.save()

        val added = cardRepository.cardsInDeck(deckId)
        assertEquals(3, added.size)
        assertTrue(added.all { it.masteryStreak == 0 && it.lastSeenAt == null })
    }

    /** Adding to a Deck must not cost the user the progress they already made in it. */
    @Test
    fun `the Cards already in the Deck keep their streaks and last-seen times`() = runTest {
        val deckId = deckRepository.createDeck("Big-O")
        val existingId = cardRepository.createCard(deckId, "Already here", "From before.")
        cardRepository.master(existingId)
        val before = cardRepository.cardsInDeck(deckId).single()

        generatedViewModel(deckId).save()

        val after = cardRepository.cardsInDeck(deckId).single { it.id == existingId }
        assertEquals(before, after)
    }

    /** What the Deck's mastery summary is counted from: the Deck is simply bigger now. */
    @Test
    fun `the Deck holds both what it had and what was added`() = runTest {
        val deckId = deckRepository.createDeck("Big-O")
        cardRepository.createCard(deckId, "Already here", "From before.")

        generatedViewModel(deckId).save()

        assertEquals(4, deckRepository.summary(deckId).cardCount)
    }

    @Test
    fun `the target can be changed to another Deck before saving`() = runTest {
        val firstId = deckRepository.createDeck("Git basics")
        val secondId = deckRepository.createDeck("Big-O")
        val viewModel = generatedViewModel(firstId)

        viewModel.addToDeck(secondId)
        viewModel.save()

        assertEquals(GenerateTarget.ExistingDeck(secondId, "Big-O"), viewModel.uiState.value.target)
        assertEquals(3, cardRepository.cardsInDeck(secondId).size)
        assertEquals(0, cardRepository.cardsInDeck(firstId).size)
    }

    @Test
    fun `the target can be changed to a new Deck`() = runTest {
        val deckId = deckRepository.createDeck("Git basics")
        val viewModel = generatedViewModel(deckId)

        viewModel.addToNewDeck()
        viewModel.setDeckName("Complexity")
        viewModel.save()

        assertEquals(listOf("Complexity", "Git basics"), deckRepository.deckNames().sorted())
        assertEquals(0, cardRepository.cardsInDeck(deckId).size)
    }

    /**
     * The Cards are already on their way to the Deck named on screen, so where they are going
     * stops being something that can move. The screen turns this off; this is what it reads.
     */
    @Test
    fun `where the Cards are going cannot move once the save is under way`() = runTest {
        val deckId = deckRepository.createDeck("Git basics")
        val viewModel = generatedViewModel(deckId)

        viewModel.save()

        assertTrue(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.canSave)
    }

    /** Every Deck the user could aim at, so the picker has something to list. */
    @Test
    fun `the Decks that can be added to are offered`() = runTest {
        deckRepository.createDeck("Git basics")
        deckRepository.createDeck("Big-O")

        val decks = viewModel().uiState.value.decks

        assertEquals(listOf("Big-O", "Git basics"), decks.map { it.name })
    }

    /** Deleted from the Deck list behind this screen: there is nothing left to add to. */
    @Test
    fun `a target Deck that is deleted falls back to a new Deck`() = runTest {
        val deckId = deckRepository.createDeck("Git basics")
        val viewModel = generatedViewModel(deckId)

        deckRepository.deleteDeck(deckId)

        assertEquals(GenerateTarget.NewDeck, viewModel.uiState.value.target)
        assertTrue(viewModel.uiState.value.isNamingNewDeck)
    }

    /**
     * The hand-written path into a Deck that exists: there is nothing to create, so the flow
     * simply opens the Deck the Cards are to be written into.
     */
    @Test
    fun `writing them yourself into an existing Deck creates no Deck`() = runTest {
        val deckId = deckRepository.createDeck("Git basics")
        val viewModel = viewModel(deckId)

        viewModel.openTargetDeck()

        assertEquals(deckId, viewModel.savedDeckId.value)
        assertEquals(1, deckRepository.deckIds().size)
    }

    @Test
    fun `there is no target Deck to open when the target is a new Deck`() = runTest {
        val viewModel = viewModel()

        viewModel.openTargetDeck()

        assertNull(viewModel.savedDeckId.value)
        assertEquals(emptyList<Long>(), deckRepository.deckIds())
    }

    private fun viewModel(deckId: Long? = null) = GenerateViewModel(
        savedStateHandle = SavedStateHandle(
            deckId?.let { mapOf(GenerateRoute.DECK_ID_ARG to it) } ?: emptyMap(),
        ),
        cardGenerator = generator,
        deckRepository = deckRepository,
        cardRepository = cardRepository,
        shareInbox = ShareInbox(),
    )

    private suspend fun generatedViewModel(deckId: Long? = null) = viewModel(deckId).also {
        it.setText("Big-O notation describes how work grows with input size.")
        it.generate()
    }

    private suspend fun FakeDeckRepository.summary(id: Long) =
        observeDeckSummaries().first().single { it.deck.id == id }
}
