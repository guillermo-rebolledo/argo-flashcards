package dev.memoji.flashcards.core.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.memoji.flashcards.core.model.ReminderTime
import dev.memoji.flashcards.core.testing.MutableClock
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Against a real WorkManager on its test harness, because the promise being checked — one
 * reminder under one name, whatever order things are called in — is WorkManager's to keep.
 */
@RunWith(RobolectricTestRunner::class)
class WorkManagerReminderSchedulerTest {

    private val clock = MutableClock()
    private lateinit var workManager: WorkManager

    @Before
    fun initialiseWorkManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `scheduling puts one reminder in the diary`() {
        scheduler().schedule(ReminderTime(20, 0))

        assertEquals(1, pendingReminders().size)
    }

    /** The acceptance criterion in one test: a new time replaces, it does not add. */
    @Test
    fun `changing the time leaves one reminder, not two`() {
        val scheduler = scheduler()
        scheduler.schedule(ReminderTime(20, 0))

        scheduler.schedule(ReminderTime(7, 30))

        assertEquals(1, pendingReminders().size)
    }

    @Test
    fun `scheduling the same time twice still leaves one reminder`() {
        val scheduler = scheduler()
        scheduler.schedule(ReminderTime(20, 0))

        scheduler.schedule(ReminderTime(20, 0))

        assertEquals(1, pendingReminders().size)
    }

    @Test
    fun `turning reminders off leaves nothing scheduled`() {
        val scheduler = scheduler()
        scheduler.schedule(ReminderTime(20, 0))

        scheduler.cancel()

        assertEquals(emptyList<WorkInfo>(), pendingReminders())
    }

    /** Cancelling something that was never scheduled is a no-op, not a crash. */
    @Test
    fun `cancelling nothing is allowed`() {
        scheduler().cancel()

        assertEquals(emptyList<WorkInfo>(), pendingReminders())
    }

    /**
     * The difference between the two ways in. A settings change replaces what is there — the
     * work it found is gone afterwards.
     */
    @Test
    fun `scheduling replaces the reminder that was there`() {
        val scheduler = scheduler()
        scheduler.schedule(ReminderTime(20, 0))
        val replaced = pendingReminders().single().id

        scheduler.schedule(ReminderTime(7, 30))

        assertEquals(emptyList<Any>(), pendingReminders().map { it.id }.filter { it == replaced })
    }

    /**
     * A reminder that has just fired books tomorrow's without cancelling itself. It is the run
     * holding the name, so replacing would be it asking WorkManager to stop it mid-notify —
     * this queues behind it instead, and the run finishing is what makes tomorrow's the only
     * one left.
     */
    @Test
    fun `a reminder booking the next one does not cancel itself`() {
        val scheduler = scheduler()
        scheduler.schedule(ReminderTime(20, 0))
        val firing = pendingReminders().single().id

        scheduler.scheduleAfterFiring(ReminderTime(20, 0))

        assertTrue(pendingReminders().any { it.id == firing })
        assertEquals(2, pendingReminders().size)
    }

    /** The delay is worked out from the clock, not from a fixed day. */
    @Test
    fun `the reminder is scheduled for the next time it is that hour`() {
        // The fake clock reads 09:00 UTC, so 20:00 is eleven hours off.
        scheduler().schedule(ReminderTime(20, 0))

        val delay = pendingReminders().single().initialDelayMillis

        assertEquals(Duration.ofHours(11).toMillis(), delay)
    }

    @Test
    fun `a time already past today is scheduled for tomorrow`() {
        scheduler().schedule(ReminderTime(7, 30))

        val delay = pendingReminders().single().initialDelayMillis

        assertTrue(delay > Duration.ofHours(22).toMillis())
        assertTrue(delay <= Duration.ofDays(1).toMillis())
    }

    private fun scheduler() = WorkManagerReminderScheduler({ workManager }, clock)

    private fun pendingReminders(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork("daily-reminder").get()
            .filterNot { it.state == WorkInfo.State.CANCELLED }
}
