package dev.memoji.flashcards.core.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import dev.memoji.flashcards.R
import dev.memoji.flashcards.core.data.EncryptedApiKeyRepository
import dev.memoji.flashcards.core.database.DeckEntity
import dev.memoji.flashcards.core.database.FlashcardsDatabase
import dev.memoji.flashcards.core.database.onDiskDatabase
import dev.memoji.flashcards.core.datastore.PreferencesModule
import java.io.File
import javax.crypto.KeyGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/**
 * ADR 0002: Decks and Cards are backed up, the API key is not. The rules say so, and a rule
 * that says so is worth nothing on its own — the file it names has to be the file the app
 * actually writes to. So these tests read the rules that ship in the APK and check them
 * against the paths the app produces when it stores something, rather than against the paths
 * whoever wrote the rules believed it produced.
 *
 * Both rules files are read: `data_extraction_rules` covers Android 12 and above,
 * `backup_rules` everything below, and a change made to one and not the other would mean the
 * credential is excluded on some devices and not on others.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRulesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** What ships: cloud backup and device transfer for 12 and above, and the older rules. */
    private val cloudBackup = includes(R.xml.data_extraction_rules, "cloud-backup")
    private val deviceTransfer = includes(R.xml.data_extraction_rules, "device-transfer")
    private val fullBackup = includes(R.xml.backup_rules, "full-backup-content")

    /**
     * The heart of it. The key is written by the real repository, the file it lands in is
     * found on disk, and that file is checked against every rule — not against the one the
     * rules were written for. A key moved into another file, or a second file added beside it,
     * fails here rather than silently starting to travel with the backup.
     */
    @Test
    fun `the file the API key is actually stored in is backed up by nothing`() = runTest {
        val secretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        EncryptedApiKeyRepository(context) { secretKey }.setApiKey("sk-ant-secret")

        val stored = credentialFiles()
        assertNotEquals("the key was not written anywhere", emptyList<File>(), stored)
        stored.forEach { file ->
            allIncludes().forEach { include ->
                assertTrue(
                    "${include.domain}/${include.path} would back up ${file.name}",
                    !include.covers(file),
                )
            }
        }
    }

    /**
     * The same thing said the other way round, and the reason the rules can be read at a
     * glance: nothing in the domain the key lives in is listed at all. An exclude is not what
     * keeps it out — there is no exclude — so a rule added to the wrong domain later is what
     * this catches.
     */
    @Test
    fun `no rule names the domain preferences are stored in`() {
        assertEquals(emptyList<Include>(), allIncludes().filter { it.domain == "sharedpref" })
    }

    /** Named what goes, so a store added later is out until someone adds it here on purpose. */
    @Test
    fun `what is backed up is the database and the settings, and nothing else`() {
        val expected = listOf(
            Include("database", FlashcardsDatabase.DATABASE_NAME),
            Include("database", "${FlashcardsDatabase.DATABASE_NAME}-wal"),
            Include("database", "${FlashcardsDatabase.DATABASE_NAME}-shm"),
            Include("file", "datastore"),
        )

        assertEquals(expected, cloudBackup)
        assertEquals(expected, deviceTransfer)
        assertEquals(expected, fullBackup)
    }

    /**
     * The inclusion side, checked the same way as the exclusion side: a Deck is written through
     * the real database, and every file Room left on disk for it has to be covered.
     *
     * Room runs in write-ahead logging mode, so the Deck just written may live in `-wal` and
     * not in the database file at all — and an `include` naming a file is a starting point for
     * a walk that a file has nothing below. Naming only `flashcards.db` would back up a
     * database with the newest Decks missing, which is exactly the failure that looks like it
     * worked. This fails if Room's journal mode changes and the rules do not follow.
     */
    @Test
    fun `every file the database is made of is backed up`() = runTest {
        val database = onDiskDatabase()
        database.deckDao().insert(DeckEntity(name = "Big-O notation", createdAt = 0L))

        val files = databaseFiles()
        assertTrue("Room wrote no database", files.isNotEmpty())
        files.forEach { file ->
            assertTrue("${file.name} is backed up by nothing", cloudBackup.any { it.covers(file) })
            assertTrue("${file.name} is backed up by nothing", fullBackup.any { it.covers(file) })
        }
        database.close()
    }

    /**
     * WorkManager keeps its own database beside ours, and a device's pending work is not
     * something to carry onto another device — the reminder is re-registered from the setting,
     * not restored from a queue. Naming the whole domain rather than the files would have
     * swept it in.
     */
    @Test
    fun `nothing backs up a database that is not ours`() {
        val other = context.getDatabasePath("androidx.work.workdb")

        assertTrue(allIncludes().none { it.covers(other) })
    }

    /** A preference is cheap to lose and free to carry over — but only if the rule reaches it. */
    @Test
    fun `the settings rule reaches the file the settings are written to`() {
        val settings = context.preferencesDataStoreFile(PreferencesModule.PREFERENCES_NAME)

        assertTrue(cloudBackup.any { it.covers(settings) })
        assertTrue(fullBackup.any { it.covers(settings) })
    }

    /** Rules only run if backup is on at all. */
    @Test
    fun `backup is enabled`() {
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    private fun allIncludes() = cloudBackup + deviceTransfer + fullBackup

    /** Every file Room left on disk for our database, whatever it chose to call them. */
    private fun databaseFiles(): List<File> {
        val database = context.getDatabasePath(FlashcardsDatabase.DATABASE_NAME)
        return database.parentFile?.listFiles().orEmpty()
            .filter { it.name.startsWith(FlashcardsDatabase.DATABASE_NAME) }
    }

    /** Every file the key could have been written into, whatever it ended up being called. */
    private fun credentialFiles(): List<File> =
        File(context.dataDir, "shared_prefs").listFiles().orEmpty().toList()

    /** One `include` line, as the platform reads it: a storage area and a path inside it. */
    private data class Include(val domain: String, val path: String)

    /**
     * Whether the rule would sweep [file] into a backup. The platform resolves an include
     * against its domain's directory and then walks it, so a rule naming a directory covers
     * what is under it and a rule naming a file covers only that file — which is the whole
     * reason the write-ahead log has to be named as well.
     */
    private fun Include.covers(file: File): Boolean {
        val root = domainRoot() ?: return false
        val included = File(root, path).canonicalFile
        val candidate = file.canonicalFile
        return candidate == included ||
            candidate.path.startsWith("${included.path}${File.separator}")
    }

    /** Where each domain lives on this device, asked of the same Context the app would ask. */
    private fun Include.domainRoot(): File? = when (domain) {
        "root" -> context.dataDir
        "file" -> context.filesDir
        "database" -> context.getDatabasePath("any").parentFile
        "sharedpref" -> File(context.dataDir, "shared_prefs")
        else -> null
    }

    /**
     * The rules as they were compiled into the APK, read back out of it. Parsing the resource
     * rather than the source file is the point: what a device obeys is what shipped.
     */
    private fun includes(rules: Int, section: String): List<Include> {
        val parser = context.resources.getXml(rules)
        val found = mutableListOf<Include>()
        var inSection = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    section -> inSection = true
                    "include" -> if (inSection) {
                        found += Include(
                            domain = parser.getAttributeValue(null, "domain"),
                            path = parser.getAttributeValue(null, "path"),
                        )
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == section) inSection = false
            }
        }
        return found
    }
}
