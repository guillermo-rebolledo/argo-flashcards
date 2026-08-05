package dev.memoji.flashcards.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.memoji.flashcards.core.model.SessionLength
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Over a real DataStore on a temporary directory rather than a stand-in — the default when
 * nothing has been written is the whole point of this class, and a fake would simply agree
 * with whatever it was told the default was.
 */
class LocalSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `Session length is five before anyone has chosen one`() = runTest {
        val repository = repository()

        assertEquals(SessionLength.STANDARD, repository.observeSessionLength().first())
        assertEquals(5, repository.observeSessionLength().first().cards)
    }

    @Test
    fun `a chosen Session length is what comes back`() = runTest {
        val repository = repository()

        repository.setSessionLength(SessionLength.LONG)

        assertEquals(SessionLength.LONG, repository.observeSessionLength().first())
    }

    @Test
    fun `each Session length the app offers round-trips`() = runTest {
        val repository = repository()

        SessionLength.entries.forEach { length ->
            repository.setSessionLength(length)
            assertEquals(length, repository.observeSessionLength().first())
        }
    }

    private fun TestScope.repository() = LocalSettingsRepository(
        PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        ),
    )
}
