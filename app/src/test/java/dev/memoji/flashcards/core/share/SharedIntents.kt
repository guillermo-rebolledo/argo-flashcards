package dev.memoji.flashcards.core.share

import android.content.Intent

/**
 * The Intent a browser or a reader builds when the user picks Flashcards out of the share
 * sheet. Written once because two test classes ask the same question of it — whether the app
 * is offered for it, and what is read out of it.
 */
internal fun sharedTextIntent(text: String): Intent = Intent(Intent.ACTION_SEND)
    .setType("text/plain")
    .putExtra(Intent.EXTRA_TEXT, text)
