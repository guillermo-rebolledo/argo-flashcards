package dev.memoji.flashcards.core.reminders

import dev.memoji.flashcards.core.data.FakeSettingsRepository
import dev.memoji.flashcards.core.model.ReminderTime
import dev.memoji.flashcards.core.model.ThemePreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderCoordinatorTest {

    private val settings = FakeSettingsRepository()
    private val scheduler = RecordingReminderScheduler()

    @Test
    fun `nothing is scheduled before the user asks for reminders`() = runTest {
        coordinate()

        assertEquals(emptyList<ReminderTime>(), scheduler.scheduled)
    }

    @Test
    fun `turning reminders on schedules one`() = runTest {
        coordinate()

        settings.setRemindersEnabled(true)
        runCurrent()

        assertEquals(listOf(ReminderTime.DEFAULT), scheduler.scheduled)
    }

    /** The one the ticket is most concerned with: a new time replaces, never adds. */
    @Test
    fun `changing the time reschedules rather than adding a second reminder`() = runTest {
        settings.setRemindersEnabled(true)
        coordinate()

        settings.setReminderTime(ReminderTime(7, 30))
        runCurrent()

        assertEquals(listOf(ReminderTime.DEFAULT, ReminderTime(7, 30)), scheduler.scheduled)
        assertEquals(0, scheduler.cancellations)
    }

    @Test
    fun `turning reminders off cancels the scheduled work`() = runTest {
        settings.setRemindersEnabled(true)
        coordinate()

        settings.setRemindersEnabled(false)
        runCurrent()

        assertEquals(1, scheduler.cancellations)
    }

    /**
     * A time set while reminders are off is kept but not acted on — the user picking an hour
     * before turning the switch on must not start the nudges early.
     */
    @Test
    fun `a time chosen while reminders are off schedules nothing`() = runTest {
        coordinate()

        settings.setReminderTime(ReminderTime(7, 30))
        runCurrent()

        assertEquals(emptyList<ReminderTime>(), scheduler.scheduled)
    }

    /** Every setting comes down one flow; the reminder must not move when the theme does. */
    @Test
    fun `an unrelated setting change leaves the reminder where it is`() = runTest {
        settings.setRemindersEnabled(true)
        coordinate()

        settings.setTheme(ThemePreference.DARK)
        runCurrent()

        assertEquals(listOf(ReminderTime.DEFAULT), scheduler.scheduled)
    }

    /**
     * Every launch re-enqueues what should already be there. It is the same reminder at the
     * same time, and it repairs a device that lost the app's work — a backup restore, or a
     * battery optimiser that cleared it.
     */
    @Test
    fun `a restart schedules the stored reminder again`() = runTest {
        settings.setRemindersEnabled(true)
        settings.setReminderTime(ReminderTime(7, 30))

        coordinate()

        assertEquals(listOf(ReminderTime(7, 30)), scheduler.scheduled)
    }

    private fun TestScope.coordinate() {
        ReminderCoordinator(
            settingsRepository = settings,
            scheduler = scheduler,
            scope = backgroundScope,
        ).start()
        runCurrent()
    }
}

/** Says what was asked of it and nothing more — WorkManager itself is not what is being tested. */
internal class RecordingReminderScheduler : ReminderScheduler {
    val scheduled = mutableListOf<ReminderTime>()
    var cancellations = 0
        private set

    override fun schedule(time: ReminderTime) {
        scheduled += time
    }

    override fun scheduleAfterFiring(time: ReminderTime) {
        scheduled += time
    }

    override fun cancel() {
        cancellations++
    }
}
