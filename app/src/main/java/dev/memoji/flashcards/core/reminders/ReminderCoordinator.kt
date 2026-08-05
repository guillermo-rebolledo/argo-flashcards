package dev.memoji.flashcards.core.reminders

import dev.memoji.flashcards.core.coroutines.ApplicationScope
import dev.memoji.flashcards.core.data.SettingsRepository
import dev.memoji.flashcards.core.model.ReminderTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps what is scheduled equal to what is stored.
 *
 * The alternative — scheduling from the Settings screen, next to the write — leaves the two
 * able to disagree: a write that lands while the screen is going away, or a setting restored
 * from a backup, would change the stored answer without changing the diary. Watching the
 * stored answer instead means there is only ever one thing to be right about.
 */
@Singleton
internal class ReminderCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduler: ReminderScheduler,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    /**
     * Runs for as long as the app process does. The first value arrives moments after launch
     * and re-enqueues what should already be there, which costs nothing and repairs the case
     * where something else cleared the app's work.
     */
    fun start() {
        scope.launch {
            settingsRepository.observeSettings()
                .map { Reminder(enabled = it.remindersEnabled, time = it.reminderTime) }
                // Every other setting comes down the same flow; only these two mean anything
                // here, and rescheduling on a theme change would move the reminder for free.
                .distinctUntilChanged()
                .collect { reminder ->
                    if (reminder.enabled) scheduler.schedule(reminder.time) else scheduler.cancel()
                }
        }
    }

    private data class Reminder(val enabled: Boolean, val time: ReminderTime)
}
