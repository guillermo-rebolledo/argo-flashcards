package dev.memoji.flashcards.core.coroutines

import javax.inject.Qualifier

/**
 * A scope that lives as long as the app, for writes that must finish even though whatever
 * started them has gone. A Grade is the case this exists for: it is written as the user moves
 * to the next Card, and they may well leave the Session before the write lands.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
