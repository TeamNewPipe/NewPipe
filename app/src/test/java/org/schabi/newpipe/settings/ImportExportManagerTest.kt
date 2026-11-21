package org.schabi.newpipe.settings

import android.content.SharedPreferences
import com.grack.nanojson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.ObjectOutputStream
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnitRunner
import org.schabi.newpipe.database.Migrations
import org.schabi.newpipe.local.feed.quickadd.QuickAddPreference
import org.schabi.newpipe.settings.export.BackupFileLocator
import org.schabi.newpipe.settings.export.ImportExportManager
import org.schabi.newpipe.settings.export.ImportExportManager.BackupSource
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.io.FileStream

@RunWith(MockitoJUnitRunner::class)
class ImportExportManagerTest {

    companion object {
        private val classloader = ImportExportManager::class.java.classLoader!!
    }

    private lateinit var fileLocator: BackupFileLocator
    private lateinit var storedFileHelper: StoredFileHelper

    @Before
    fun setupFileLocator() {
        fileLocator = Mockito.mock(BackupFileLocator::class.java, withSettings().stubOnly())
        storedFileHelper = Mockito.mock(StoredFileHelper::class.java, withSettings().stubOnly())
    }

    @Test
    fun `The settings must be exported successfully in the correct format`() {
        val db = Paths.get(classloader.getResource("settings/newpipe.db")!!.toURI())
        `when`(fileLocator.db).thenReturn(db)

        val expectedPreferences = mapOf("such pref" to "much wow")
        val sharedPreferences =
            Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())
        `when`(sharedPreferences.all).thenReturn(expectedPreferences)

        val output = File.createTempFile("newpipe_", "")
        `when`(storedFileHelper.openAndTruncateStream()).thenReturn(FileStream(output))
        ImportExportManager(fileLocator).exportDatabase(sharedPreferences, storedFileHelper)

        ZipFile(output).use { zipFile ->
            val entries = zipFile.entries().toList()
            val entryNames = entries.map { it.name }.toSet()

            assertEquals(
                setOf(
                    BackupFileLocator.FILE_NAME_DB,
                    BackupFileLocator.FILE_NAME_JSON_PREFS,
                    BackupFileLocator.FILE_NAME_MANIFEST
                ),
                entryNames
            )
            assertFalse(entryNames.contains(BackupFileLocator.FILE_NAME_SERIALIZED_PREFS))

            zipFile.getInputStream(
                entries.first { it.name == BackupFileLocator.FILE_NAME_DB }
            ).use { actual ->
                db.inputStream().use { expected ->
                    assertEquals(expected.reader().readText(), actual.reader().readText())
                }
            }

            zipFile.getInputStream(
                entries.first { it.name == BackupFileLocator.FILE_NAME_JSON_PREFS }
            ).use { actual ->
                val actualPreferences = JsonParser.`object`().from(actual)
                assertEquals(expectedPreferences, actualPreferences)
            }

            zipFile.getInputStream(
                entries.first { it.name == BackupFileLocator.FILE_NAME_MANIFEST }
            ).use { actual ->
                val manifest = JsonParser.`object`().from(actual)
                assertEquals("WizeStream", manifest.getString("appName"))
                assertTrue(manifest.containsKey("backupFormatVersion"))
                assertTrue(manifest.containsKey("createdTimestamp"))
                assertTrue(manifest.getBoolean("includesDatabase"))
                assertTrue(manifest.getBoolean("includesPreferences"))
                assertFalse(manifest.getBoolean("includesLocalRecommendations"))
                assertFalse(manifest.getBoolean("includesSponsorBlockSettings"))
            }
        }
    }

    @Test
    fun `Complete recommendation models are included when requested`() {
        val db = Paths.get(classloader.getResource("settings/newpipe.db")!!.toURI())

        val preferences = Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())
        `when`(preferences.all).thenReturn(mapOf("theme" to "dark"))
        val localRecommendations =
            Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())
        val recommendationModel = mapOf(
            "profile:model" to """{"watched":["private-video"],"weights":{"0:music":2.0}}""",
            "profile:paused" to true
        )
        `when`(localRecommendations.all).thenReturn(recommendationModel)

        val output = File.createTempFile("wizestream_taste_", ".zip")
        `when`(storedFileHelper.openAndTruncateStream()).thenReturn(FileStream(output))
        ImportExportManager(fileLocator).exportDatabase(
            preferences,
            storedFileHelper,
            db,
            localRecommendations
        )

        ZipFile(output).use { zipFile ->
            val taste = zipFile.getInputStream(
                zipFile.getEntry(BackupFileLocator.FILE_NAME_LOCAL_RECOMMENDATIONS)
            ).use { JsonParser.`object`().from(it) }
            assertEquals(recommendationModel, taste)

            val manifest = zipFile.getInputStream(
                zipFile.getEntry(BackupFileLocator.FILE_NAME_MANIFEST)
            ).use { JsonParser.`object`().from(it) }
            assertTrue(manifest.getBoolean("includesLocalRecommendations"))
        }
    }

    @Test
    fun `A supported WizeStream manifest must be accepted for full restore`() {
        val zip = backupWithManifest(
            """{"appName":"WizeStream","backupFormatVersion":1}"""
        )
        `when`(storedFileHelper.stream).thenReturn(FileStream(zip.toFile()))

        val contents = ImportExportManager(fileLocator).inspectBackup(storedFileHelper)

        assertEquals(BackupSource.WIZESTREAM, contents.source)
        assertEquals(1, contents.backupFormatVersion)
    }

    @Test
    fun `A foreign manifest must not be accepted as a WizeStream backup`() {
        val zip = backupWithManifest(
            """{"appName":"PipePipe","backupFormatVersion":1}"""
        )
        `when`(storedFileHelper.stream).thenReturn(FileStream(zip.toFile()))

        val contents = ImportExportManager(fileLocator).inspectBackup(storedFileHelper)

        assertEquals(BackupSource.FOREIGN, contents.source)
    }

    @Test
    fun `A manifest-less archive must remain untrusted`() {
        val zip = createTempFile("legacy_backup_", ".zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(BackupFileLocator.FILE_NAME_DB))
            output.write("legacy database".toByteArray())
            output.closeEntry()
        }
        `when`(storedFileHelper.stream).thenReturn(FileStream(zip.toFile()))

        val contents = ImportExportManager(fileLocator).inspectBackup(storedFileHelper)

        assertEquals(BackupSource.LEGACY_OR_UNKNOWN, contents.source)
    }

    private fun backupWithManifest(manifest: String) = createTempFile(
        "backup_manifest_",
        ".zip"
    ).also { zip ->
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(BackupFileLocator.FILE_NAME_DB))
            output.write("database".toByteArray())
            output.closeEntry()
            output.putNextEntry(ZipEntry(BackupFileLocator.FILE_NAME_MANIFEST))
            output.write(manifest.toByteArray())
            output.closeEntry()
        }
    }

    @Test
    fun `Ensuring db directory existence must work`() {
        val path = createTempDirectory("newpipe_") / BackupFileLocator.FILE_NAME_DB
        Assume.assumeTrue(path.parent.deleteIfExists())
        `when`(fileLocator.db).thenReturn(path)

        ImportExportManager(fileLocator).ensureDbDirectoryExists()
        assertTrue(path.parent.exists())
    }

    @Test
    fun `Ensuring db directory existence must work when the directory already exists`() {
        val path = createTempDirectory("newpipe_") / BackupFileLocator.FILE_NAME_DB
        `when`(fileLocator.db).thenReturn(path)

        ImportExportManager(fileLocator).ensureDbDirectoryExists()
        assertTrue(path.parent.exists())
    }

    @Test
    fun `Current database version can be staged for full restore`() {
        val db = createTempDirectory("current_backup_") / BackupFileLocator.FILE_NAME_DB
        `when`(fileLocator.db).thenReturn(db)
        val zip = databaseBackup(Migrations.DB_VER_CURRENT)
        `when`(storedFileHelper.stream).thenReturn(FileStream(zip.toFile()))

        val manager = ImportExportManager(fileLocator)
        val staged = manager.stageDb(storedFileHelper)
        try {
            assertTrue(staged.exists())
            assertTrue(staged.fileSize() >= 64)
        } finally {
            manager.discardStagedDb(staged)
        }
    }

    @Test
    fun `Unsupported database version reports the actual staging failure`() {
        val db = createTempDirectory("future_backup_") / BackupFileLocator.FILE_NAME_DB
        `when`(fileLocator.db).thenReturn(db)
        val futureVersion = Migrations.DB_VER_CURRENT + 1
        val zip = databaseBackup(futureVersion)
        `when`(storedFileHelper.stream).thenReturn(FileStream(zip.toFile()))

        val error = assertThrows(IOException::class.java) {
            ImportExportManager(fileLocator).stageDb(storedFileHelper)
        }

        assertTrue(error.message!!.contains("Unsupported backup database version $futureVersion"))
        assertFalse(db.resolveSibling("${db.fileName}.import").exists())
    }

    @Test
    fun `SAF stream failure is propagated instead of converted to null`() {
        val db = createTempDirectory("failed_backup_") / BackupFileLocator.FILE_NAME_DB
        `when`(fileLocator.db).thenReturn(db)
        `when`(storedFileHelper.stream).thenThrow(IOException("content provider read failed"))

        val error = assertThrows(IOException::class.java) {
            ImportExportManager(fileLocator).stageDb(storedFileHelper)
        }

        assertEquals("content provider read failed", error.message)
    }

    @Test
    fun `The database must be extracted from the zip file`() {
        val db = createTempFile("newpipe_", "")
        val dbJournal = createTempFile("newpipe_", "")
        val dbWal = createTempFile("newpipe_", "")
        val dbShm = createTempFile("newpipe_", "")
        `when`(fileLocator.db).thenReturn(db)
        `when`(fileLocator.dbJournal).thenReturn(dbJournal)
        `when`(fileLocator.dbShm).thenReturn(dbShm)
        `when`(fileLocator.dbWal).thenReturn(dbWal)

        val zip = File(classloader.getResource("settings/db_ser_json.zip")?.file!!)
        `when`(storedFileHelper.stream).thenReturn(FileStream(zip))
        val success = ImportExportManager(fileLocator).extractDb(storedFileHelper)

        assertTrue(success)
        assertFalse(dbJournal.exists())
        assertFalse(dbWal.exists())
        assertFalse(dbShm.exists())
        assertTrue("database file size is zero", db.fileSize() > 0)
    }

    @Test
    fun `Extracting the database from an empty zip must not work`() {
        val db = createTempFile("newpipe_", "")
        val dbJournal = createTempFile("newpipe_", "")
        val dbWal = createTempFile("newpipe_", "")
        val dbShm = createTempFile("newpipe_", "")
        `when`(fileLocator.db).thenReturn(db)

        val emptyZip = File(classloader.getResource("settings/nodb_noser_nojson.zip")?.file!!)
        `when`(storedFileHelper.stream).thenReturn(FileStream(emptyZip))
        val success = ImportExportManager(fileLocator).extractDb(storedFileHelper)

        assertFalse(success)
        assertTrue(dbJournal.exists())
        assertTrue(dbWal.exists())
        assertTrue(dbShm.exists())
        assertEquals(0, db.fileSize())
    }

    @Test
    fun `An incompatible foreign database must not replace the current database`() {
        val db = createTempFile("newpipe_", "")
        db.writeText("current WizeStream database")
        `when`(fileLocator.db).thenReturn(db)

        val foreignDatabase = ByteArray(100)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            .copyInto(foreignDatabase)
        ByteBuffer.wrap(foreignDatabase, 60, Int.SIZE_BYTES).putInt(901)

        val zip = createTempFile("pipepipe_", ".zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(BackupFileLocator.FILE_NAME_DB))
            output.write(foreignDatabase)
            output.closeEntry()
        }
        `when`(storedFileHelper.stream).thenReturn(FileStream(zip.toFile()))

        assertFalse(ImportExportManager(fileLocator).extractDb(storedFileHelper))
        assertEquals("current WizeStream database", db.readText())
    }

    @Suppress("DEPRECATION")
    @Test
    fun `Contains setting must return true if a settings file exists in the zip`() {
        val zip = File(classloader.getResource("settings/db_ser_json.zip")?.file!!)
        `when`(storedFileHelper.stream).thenReturn(FileStream(zip))
        assertTrue(ImportExportManager(fileLocator).exportHasSerializedPrefs(storedFileHelper))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `Contains setting must return false if no settings file exists in the zip`() {
        val emptyZip = File(classloader.getResource("settings/nodb_noser_nojson.zip")?.file!!)
        `when`(storedFileHelper.stream).thenReturn(FileStream(emptyZip))
        assertFalse(ImportExportManager(fileLocator).exportHasSerializedPrefs(storedFileHelper))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `Preferences must be set from the settings file`() {
        val zip = File(classloader.getResource("settings/db_ser_json.zip")?.file!!)
        `when`(storedFileHelper.stream).thenReturn(FileStream(zip))

        val preferences = Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        `when`(preferences.edit()).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)

        ImportExportManager(fileLocator).loadSerializedPrefs(storedFileHelper, preferences)

        verify(editor, atLeastOnce()).putBoolean(anyString(), anyBoolean())
        verify(editor, atLeastOnce()).putString(anyString(), anyString())
        verify(editor, atLeastOnce()).putInt(anyString(), anyInt())
    }

    @Test
    fun `JSON backup normalizes small quick-add playlist IDs to Long`() {
        val targetKey = QuickAddPreference.targetKey("00000000-0000-0000-0000-000000000000")
        val zip = createTempFile("quick_add_restore_", ".zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(BackupFileLocator.FILE_NAME_JSON_PREFS))
            output.write("""{"$targetKey":12,"other_integer":7}""".toByteArray())
            output.closeEntry()
        }
        `when`(storedFileHelper.stream).thenReturn(FileStream(zip.toFile()))

        val preferences = Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        `when`(preferences.edit()).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)

        ImportExportManager(fileLocator).loadJsonPrefs(storedFileHelper, preferences)

        verify(editor).clear()
        verify(editor).putLong(targetKey, 12L)
        verify(editor).putInt("other_integer", 7)
        verify(editor).commit()
    }

    @Test
    fun `Legacy serialized backup normalizes quick-add target without changing other numbers`() {
        val targetKey = QuickAddPreference.targetKey("other-profile")
        val preferences = Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        `when`(preferences.edit()).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)

        ImportExportManager(fileLocator).replacePreferences(
            preferences,
            mapOf<String, Any>(targetKey to 23, "other_integer" to 15, "already_long" to 18L)
        )

        verify(editor).putLong(targetKey, 23L)
        verify(editor).putInt("other_integer", 15)
        verify(editor).putLong("already_long", 18L)
        verify(editor).commit()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `Importing preferences with a serialization injected class should fail`() {
        val emptyZip = File(classloader.getResource("settings/db_vulnser_json.zip")?.file!!)
        `when`(storedFileHelper.stream).thenReturn(FileStream(emptyZip))

        val preferences = Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())

        assertThrows(ClassNotFoundException::class.java) {
            ImportExportManager(fileLocator).loadSerializedPrefs(storedFileHelper, preferences)
        }
    }

    @Test
    fun `PipePipe serialized preferences require explicit source approval`() {
        val serialized = ByteArrayOutputStream().use { bytes ->
            ObjectOutputStream(bytes).use { output ->
                output.writeObject(
                    hashMapOf(
                        "show_comments" to false,
                        "sponsor_block_enable" to true
                    )
                )
            }
            bytes.toByteArray()
        }
        val zip = createTempFile("pipepipe_preferences_", ".zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(BackupFileLocator.FILE_NAME_SERIALIZED_PREFS))
            output.write(serialized)
            output.closeEntry()
        }
        `when`(storedFileHelper.stream).thenAnswer { FileStream(zip.toFile()) }
        val manager = ImportExportManager(fileLocator)

        assertTrue(manager.readMigrationPrefs(storedFileHelper, false).isEmpty())
        assertEquals(
            mapOf("show_comments" to false, "sponsor_block_enable" to true),
            manager.readMigrationPrefs(storedFileHelper, true)
        )
    }

    private fun databaseBackup(version: Int) = createTempFile(
        "database_backup_",
        ".zip"
    ).also { zip ->
        val database = ByteArray(100)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(database)
        ByteBuffer.wrap(database, 60, Int.SIZE_BYTES).putInt(version)
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(BackupFileLocator.FILE_NAME_DB))
            output.write(database)
            output.closeEntry()
        }
    }

    @Test
    fun `Oversized PipePipe serialized preferences must be rejected`() {
        val zip = createTempFile("oversized_pipepipe_preferences_", ".zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(BackupFileLocator.FILE_NAME_SERIALIZED_PREFS))
            output.write(ByteArray(1_048_577))
            output.closeEntry()
        }
        `when`(storedFileHelper.stream).thenAnswer { FileStream(zip.toFile()) }

        assertThrows(IOException::class.java) {
            ImportExportManager(fileLocator).readMigrationPrefs(storedFileHelper, true)
        }
    }
}
