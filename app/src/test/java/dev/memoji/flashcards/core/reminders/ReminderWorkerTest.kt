package dev.memoji.flashcards.core.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.memoji.flashcards.core.data.FakeSettingsRepository
import dev.memoji.flashcards.core.model.ReminderTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The worker with WorkManager's own test harness around it and stand-ins for everything else —
 * what is being checked is what one run decides, not that WorkManager can run it.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderWorkerTest {

    private val settings = FakeSettingsRepository()
    private val notifier = FakeReminderNotifier()
    private val scheduler = RecordingReminderScheduler()

    @Test
    fun `a run posts the reminder`() = runTest {
        settings.setRemindersEnabled(true)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, notifier.delivered)
    }

    /**
     * Each run books the next one. Nothing else does — the app may not be opened for a week,
     * and the nudge has to keep arriving anyway.
     */
    @Test
    fun `a run schedules tomorrow's reminder`() = runTest {
        settings.setRemindersEnabled(true)
        settings.setReminderTime(ReminderTime(7, 30))

        runWorker()

        assertEquals(listOf(ReminderTime(7, 30)), scheduler.scheduled)
    }

    /**
     * A cancel and a run can cross. When they do, the setting wins: nothing is posted, and
     * nothing is booked, so the chain stops here rather than running on for ever.
     */
    @Test
    fun `a run that finds reminders turned off posts nothing and books nothing`() = runTest {
        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, notifier.asked)
        assertEquals(emptyList<ReminderTime>(), scheduler.scheduled)
    }

    /**
     * Notifications turned off for the app is the system's business, not a failure of the
     * run — tomorrow's is still booked, so turning them back on needs nothing from the user
     * beyond turning them back on.
     */
    @Test
    fun `a blocked notification does not stop tomorrow's reminder`() = runTest {
        settings.setRemindersEnabled(true)
        notifier.allowed = false

        runWorker()

        assertEquals(0, notifier.delivered)
        assertEquals(listOf(ReminderTime.DEFAULT), scheduler.scheduled)
    }

    private suspend fun runWorker(): ListenableWorker.Result {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder<ReminderWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = ReminderWorker(appContext, workerParameters, settings, notifier, scheduler)
                },
            )
            .build()
            .doWork()
    }
}
