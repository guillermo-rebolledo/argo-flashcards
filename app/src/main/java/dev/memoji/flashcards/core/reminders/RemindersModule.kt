package dev.memoji.flashcards.core.reminders

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RemindersModule {

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(scheduler: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindReminderNotifier(notifier: AndroidReminderNotifier): ReminderNotifier

    companion object {
        /**
         * Asked for lazily by everything that uses it: the first call is what initialises
         * WorkManager, and that has to happen after the app has its worker factory rather
         * than while the graph is still being built.
         */
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)
    }
}
