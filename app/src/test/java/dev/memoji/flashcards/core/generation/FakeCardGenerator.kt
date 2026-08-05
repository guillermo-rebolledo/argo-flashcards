package dev.memoji.flashcards.core.generation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * The fake that makes every Generation flow testable with no key and no network — the reason
 * [CardGenerator] is an interface at all.
 *
 * It answers with whatever [result] is set to, and can be made to hang so a test can look at
 * the screen mid-Generation, or leave the flow while one is in flight.
 */
internal class FakeCardGenerator : CardGenerator {

    var result: GenerationResult = GenerationResult.Generated(
        deckName = "Big-O notation",
        cards = listOf(
            GeneratedCard("O(1)", "Constant time — the input size does not matter."),
            GeneratedCard("O(n)", "Linear time — work grows with the input."),
            GeneratedCard("O(log n)", "Logarithmic time — each step halves what is left."),
        ),
    )

    var lastSource: Source? = null
        private set

    var generateCount = 0
        private set

    /** True once a Generation was cancelled rather than allowed to finish. */
    var wasCancelled = false
        private set

    private var gate: CompletableDeferred<Unit>? = null

    /** Makes the next Generation hang until [finish] is called. */
    fun holdOpen() {
        gate = CompletableDeferred()
    }

    fun finish() {
        gate?.complete(Unit)
        gate = null
    }

    fun failWith(failure: GenerationFailure) {
        result = GenerationResult.Failed(failure)
    }

    override suspend fun generate(source: Source): GenerationResult {
        lastSource = source
        generateCount++
        gate?.let { open ->
            try {
                open.await()
            } catch (e: CancellationException) {
                wasCancelled = true
                throw e
            }
        }
        return result
    }
}
