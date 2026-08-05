package dev.memoji.flashcards

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.memoji.flashcards.core.reminders.ReminderCoordinator
import javax.inject.Inject

/**
 * WorkManager is initialised from here rather than by its own startup provider, because the
 * reminder worker is built by Hilt and needs the factory below to exist first. The default
 * provider is removed in the manifest to match.
 */
@HiltAndroidApp
class FlashcardsApplication : Application(), Configuration.Provider {

    @Inject
    internal lateinit var workerFactory: HiltWorkerFactory

    @Inject
    internal lateinit var reminderCoordinator: ReminderCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Started here rather than from the Settings screen: what is scheduled has to follow
        // what is stored even on the launches where nobody opens Settings at all.
        reminderCoordinator.start()
    }
}
