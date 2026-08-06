package dev.memoji.flashcards.feature.generate

import androidx.lifecycle.SavedStateHandle
import dev.memoji.flashcards.core.data.FakeCardRepository
import dev.memoji.flashcards.core.data.FakeDeckRepository
import dev.memoji.flashcards.core.generation.FakeCardGenerator
import dev.memoji.flashcards.core.generation.Source
import dev.memoji.flashcards.core.share.ShareInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A share is the Add Cards flow entered from outside: the text another app sent lands in the
 * box, at the start of the flow, with nothing else about the flow different. The routing into
 * the flow is the graph's; what is tested here is what the flow does once the text arrives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedIntoGenerationTest {

    private val generator = FakeCardGenerator()
    private val cardRepository = FakeCardRepository()
    private val deckRepository = FakeDeckRepository(cardRepository)
    private val shareInbox = ShareInbox()

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun restoreDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a share waiting when the flow opens fills the box`() = runTest {
        shareInbox.offer("Big-O notation describes how work grows with input size.")

        val viewModel = viewModel()

        assertEquals(
            "Big-O notation describes how work grows with input size.",
            viewModel.entry().text,
        )
    }

    @Test
    fun `a share arriving while the flow is open fills the box`() = runTest {
        val viewModel = viewModel()

        shareInbox.offer("A selection worth remembering.")

        assertEquals("A selection worth remembering.", viewModel.entry().text)
    }

    /** The same decision a paste gets, so a shared link is generated from as a link. */
    @Test
    fun `a shared link is read as a link`() = runTest {
        val viewModel = viewModel()

        shareInbox.offer("https://example.com/big-o")

        assertEquals(Source.Url("https://example.com/big-o"), viewModel.entry().source)
        assertTrue(viewModel.entry().isUrl)
    }

    /** Picked up once. A share left waiting would fill the box again the next time in. */
    @Test
    fun `a share is taken once the flow has it`() = runTest {
        viewModel()

        shareInbox.offer("A selection worth remembering.")

        assertNull(shareInbox.shared.value)
    }

    /**
     * Sharing into a flow that is already showing proposed Cards starts that flow again from
     * the box, rather than stacking a second Generation on top of the first. The Cards on
     * screen were never written, so nothing is lost that was kept.
     */
    @Test
    fun `a share arriving on a finished Generation starts again from the box`() = runTest {
        val viewModel = generatedViewModel()

        shareInbox.offer("https://example.com/big-o")

        assertEquals("https://example.com/big-o", viewModel.entry().text)
    }

    /**
     * The Cards are already on their way to a Deck, and that save is about to take this screen
     * off the stack. Emptying the box now would strand the share on a screen that is leaving,
     * so it stays waiting for the flow the save opens.
     */
    @Test
    fun `a share arriving while the Cards are being written waits`() = runTest {
        val viewModel = generatedViewModel()
        viewModel.save()

        shareInbox.offer("https://example.com/big-o")

        assertTrue(viewModel.uiState.value.isSaving)
        assertEquals("https://example.com/big-o", shareInbox.shared.value)
    }

    /** A share names no Deck, so the Cards are headed for a new one until the user says otherwise. */
    @Test
    fun `a share is headed for a new Deck`() = runTest {
        shareInbox.offer("A selection worth remembering.")

        assertEquals(GenerateTarget.NewDeck, viewModel().uiState.value.target)
    }

    /**
     * The same answer when the flow was already aimed at a Deck. An article shared in from a
     * browser has nothing to do with the Deck the user happened to be adding to, and filing it
     * there by default would put Cards in a Deck nobody chose for them.
     */
    @Test
    fun `a share arriving on a flow aimed at a Deck is still headed for a new Deck`() = runTest {
        val deckId = deckRepository.createDeck("Git basics")
        val viewModel = viewModel(deckId)

        shareInbox.offer("https://example.com/big-o")

        assertEquals(GenerateTarget.NewDeck, viewModel.uiState.value.target)
    }

    /**
     * A Generation still running is a Generation of the Source the share has just replaced.
     * Left to finish it would write its Cards over the share, and the user would be looking at
     * Cards about something they had moved on from with no sign of what they sent in.
     */
    @Test
    fun `a share arriving mid-Generation stops it and keeps the box`() = runTest {
        val viewModel = viewModel()
        viewModel.setText("Big-O notation describes how work grows with input size.")
        generator.holdOpen()
        viewModel.generate()

        shareInbox.offer("https://example.com/dijkstra")
        generator.finish()

        assertTrue(generator.wasCancelled)
        assertEquals("https://example.com/dijkstra", viewModel.entry().text)
    }

    private fun viewModel(deckId: Long? = null) = GenerateViewModel(
        savedStateHandle = SavedStateHandle(
            deckId?.let { mapOf(GenerateRoute.DECK_ID_ARG to it) } ?: emptyMap(),
        ),
        cardGenerator = generator,
        deckRepository = deckRepository,
        cardRepository = cardRepository,
        shareInbox = shareInbox,
    )

    private suspend fun generatedViewModel() = viewModel().also {
        it.setText("Big-O notation describes how work grows with input size.")
        it.generate()
    }

    private fun GenerateViewModel.entry() = uiState.value.step as GenerateStep.Entry
}
