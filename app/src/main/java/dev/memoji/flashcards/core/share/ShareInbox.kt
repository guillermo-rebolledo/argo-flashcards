package dev.memoji.flashcards.core.share

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where a share waits between arriving at the activity and being picked up by the Add Cards
 * flow. It holds one text, because a share is an instruction to generate from this — a queue
 * of them would mean opening the flow again for material the user has moved on from.
 *
 * It exists so that the payload never travels as a navigation argument: a shared article can
 * be long, and a route is not a place to put it.
 */
@Singleton
internal class ShareInbox @Inject constructor() {

    private val _shared = MutableStateFlow<String?>(null)

    /** The text waiting to be generated from, or nothing waiting. */
    val shared: StateFlow<String?> = _shared.asStateFlow()

    fun offer(text: String) {
        _shared.value = text
    }

    /**
     * Taken by the flow that filled its box with it. Only that text is cleared, so a second
     * share arriving in the moment between the two is still waiting afterwards.
     */
    fun take(text: String) {
        _shared.compareAndSet(text, null)
    }
}
