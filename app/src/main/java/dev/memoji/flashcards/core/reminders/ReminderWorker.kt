package dev.memoji.flashcards.core.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.memoji.flashcards.core.data.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * One day's reminder: post it, then line up tomorrow's.
 *
 * Tomorrow's is enqueued here rather than by a repeating job so that a device left alone for a
 * week still has exactly one reminder pending, and so the app being opened is not what keeps
 * the chain alive.
 */
@HiltWorker
internal class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        // Read again rather than carried in the work's input data: work enqueued before a
        // reboot comes back with whatever it was given, and the settings are the truth.
        val settings = settingsRepository.observeSettings().first()

        // Turning reminders off cancels this work, so arriving here switched off means the
        // cancel and the run crossed. Stop, and do not schedule another.
        if (!settings.remindersEnabled) return Result.success()

        notifier.notifyReminder()
        scheduler.scheduleAfterFiring(settings.reminderTime)
        return Result.success()
    }
}
