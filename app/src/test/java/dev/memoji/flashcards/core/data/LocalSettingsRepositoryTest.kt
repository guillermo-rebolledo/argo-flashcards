package dev.memoji.flashcards.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.memoji.flashcards.core.model.SessionLength
import dev.memoji.flashcards.core.model.ThemePreference
import dev.memoji.flashcards.core.model.UserSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Over a real DataStore on a temporary directory rather than a stand-in — the defaults when
 * nothing has been written are the whole point of this class, and a fake would simply agree
 * with whatever it was told the defaults were.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `settings are the defaults before anyone has chosen anything`() = runTest {
        withSettings { settings ->
            assertEquals(UserSettings.DEFAULT, settings.observeSettings().first())
        }
    }

    @Test
    fun `Session length is five before anyone has chosen one`() = runTest {
        withSettings { settings ->
            assertEquals(5, settings.observeSettings().first().sessionLength.cards)
        }
    }

    @Test
    fun `each Session length the app offers round-trips`() = runTest {
        withSettings { settings ->
            SessionLength.entries.forEach { length ->
                settings.setSessionLength(length)
                assertEquals(length, settings.observeSettings().first().sessionLength)
            }
        }
    }

    @Test
    fun `each theme preference round-trips`() = runTest {
        withSettings { settings ->
            ThemePreference.entries.forEach { theme ->
                settings.setTheme(theme)
                assertEquals(theme, settings.observeSettings().first().theme)
            }
        }
    }

    @Test
    fun `the reduced-motion override round-trips both ways`() = runTest {
        withSettings { settings ->
            settings.setReducedMotion(true)
            assertTrue(settings.observeSettings().first().reducedMotion)

            settings.setReducedMotion(false)
            assertFalse(settings.observeSettings().first().reducedMotion)
        }
    }

    /** Writing one setting must not quietly reset the ones the user set earlier. */
    @Test
    fun `settings written one at a time all survive together`() = runTest {
        withSettings { settings ->
            settings.setSessionLength(SessionLength.LONG)
            settings.setTheme(ThemePreference.LIGHT)
            settings.setReducedMotion(true)

            assertEquals(
                UserSettings(
                    sessionLength = SessionLength.LONG,
                    theme = ThemePreference.LIGHT,
                    reducedMotion = true,
                ),
                settings.observeSettings().first(),
            )
        }
    }

    @Test
    fun `settings survive the app being restarted`() = runTest {
        withSettings { settings ->
            settings.setSessionLength(SessionLength.SHORT)
            settings.setTheme(ThemePreference.DARK)
            settings.setReducedMotion(true)
        }

        withSettings { settings ->
            assertEquals(
                UserSettings(
                    sessionLength = SessionLength.SHORT,
                    theme = ThemePreference.DARK,
                    reducedMotion = true,
                ),
                settings.observeSettings().first(),
            )
        }
    }

    /** A write reaches a reader that was already watching, without it asking again. */
    @Test
    fun `a change reaches a collector that is already watching`() = runTest {
        withSettings { settings ->
            val seen = mutableListOf<SessionLength>()
            val watching = launch {
                settings.observeSettings().collect { seen += it.sessionLength }
            }
            runCurrent()

            settings.setSessionLength(SessionLength.LONG)
            runCurrent()
            watching.cancel()

            assertEquals(listOf(SessionLength.DEFAULT, SessionLength.LONG), seen)
        }
    }

    /**
     * A repository over the file for as long as the block runs, closed on the way out the way
     * the process ending closes it — which is what makes two blocks in a row a restart.
     */
    private suspend fun <T> TestScope.withSettings(
        block: suspend (SettingsRepository) -> T,
    ): T {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val repository = LocalSettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
            ),
        )
        try {
            return block(repository)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }
}
