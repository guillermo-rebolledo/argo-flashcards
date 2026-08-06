package dev.memoji.flashcards.core.share

import android.content.Intent

/**
 * What another app sent into Flashcards, read out of the Intent that carried it.
 *
 * A share is not a new flow. It is a Source arriving from outside instead of through the box,
 * so all that happens here is the reading — whether the text is a link or prose stays
 * `Source.of`'s decision, made in one place for a share exactly as for a paste.
 *
 * Nothing but the text is taken. `EXTRA_SUBJECT` is the page title a browser sends alongside a
 * link and would only dilute what the Deck is generated from, and `EXTRA_STREAM` is a Uri into
 * another app's storage, which this app has no business opening.
 */
internal object SharedText {

    fun of(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND) return null
        // The manifest asks for text/plain. The check stays a subtype wider because what is
        // read below is text whatever the sender labelled it, and a share that reached us is
        // one the user chose this app for.
        if (intent.type?.startsWith(TEXT) != true) return null
        // As a CharSequence rather than a String: a selection shared out of a reader arrives
        // styled, and the styling is not part of the Source.
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        // Whitespace is nothing to generate from, and would open the flow with an empty box.
        return text?.takeIf { it.isNotBlank() }
    }

    private const val TEXT = "text/"
}
