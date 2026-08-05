package dev.memoji.flashcards.core.reminders

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.memoji.flashcards.core.model.ReminderTime
import java.time.Clock
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * One WorkManager job at a time, under one name.
 *
 * WorkManager rather than [android.app.AlarmManager]: it already re-registers its work after a
 * reboot, and its deliberately inexact delivery is what the reminder wants anyway. See ADR
 * 0004 for why exact alarms are the wrong ask here.
 *
 * Each run schedules only the next one, never a repeating job, so the delay is recomputed from
 * the wall clock every day — see [delayUntilNextReminder].
 */
internal class WorkManagerReminderScheduler @Inject constructor(
    private val workManager: dagger.Lazy<WorkManager>,
    private val clock: Clock,
) : ReminderScheduler {

    override fun schedule(time: ReminderTime) = enqueue(time, ExistingWorkPolicy.REPLACE)

    /**
     * Appended rather than replacing: the caller is the run holding this very name, and
     * replacing would have it cancel itself. Appending puts tomorrow's behind today's, which
     * is finishing as this returns.
     */
    override fun scheduleAfterFiring(time: ReminderTime) =
        enqueue(time, ExistingWorkPolicy.APPEND_OR_REPLACE)

    override fun cancel() {
        workManager.get().cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun enqueue(time: ReminderTime, policy: ExistingWorkPolicy) {
        val delay = delayUntilNextReminder(time, ZonedDateTime.now(clock))
        workManager.get().enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            policy,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    private companion object {
        /**
         * The one name every reminder is enqueued under. Changing the time replaces what is
         * under this name, which is what keeps a second nudge from appearing beside the first.
         */
        const val UNIQUE_WORK_NAME = "daily-reminder"
    }
}
