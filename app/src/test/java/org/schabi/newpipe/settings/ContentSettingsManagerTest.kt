package org.schabi.newpipe.settings

import android.content.SharedPreferences
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnitRunner
import org.schabi.newpipe.settings.ContentSettingsManagerTest.*
import java.io.File
import java.io.ObjectInputStream
import java.nio.file.Files
import java.util.zip.ZipFile

@RunWith(Suite::class)
@Suite.SuiteClasses(ExportTest::class, ImportTest::class)
class ContentSettingsManagerTest {

    @RunWith(MockitoJUnitRunner::class)
    class ExportTest {

        companion object {
            private lateinit var fileLocator: NewPipeFileLocator
            private lateinit var newpipeDb: File
            private lateinit var newpipeSettings: File

            @JvmStatic
            @BeforeClass
            fun setupFiles() {
                val dbPath = ExportTest::class.java.classLoader?.getResource("settings/newpipe.db")?.file
                val settingsPath = ExportTest::class.java.classLoader?.getResource("settings/newpipe.settings")?.path
                Assume.assumeNotNull(dbPath)
                Assume.assumeNotNull(settingsPath)

                newpipeDb = File(dbPath!!)
                newpipeSettings = File(settingsPath!!)

                fileLocator = Mockito.mock(NewPipeFileLocator::class.java, withSettings().stubOnly())
                `when`(fileLocator.db).thenReturn(newpipeDb)
                `when`(fileLocator.settings).thenReturn(newpipeSettings)
            }
        }

        private lateinit var preferences: SharedPreferences

        @Before
        fun setupMocks() {
            preferences = Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())
        }

        @Test
        fun `The settings must be exported successfully in the correct format`() {
            val expectedPreferences = mapOf("such pref" to "much wow")
            `when`(preferences.all).thenReturn(expectedPreferences)

            val manager = ContentSettingsManager(fileLocator)

            val output = File.createTempFile("newpipe_", "")
            manager.exportDatabase(preferences, output.absolutePath)

            val zipFile = ZipFile(output.absoluteFile)
            val entries = zipFile.entries().toList()
            assertEquals(2, entries.size)

            zipFile.getInputStream(entries.first { it.name == "newpipe.db" }).use { actual ->
                newpipeDb.inputStream().use { expected ->
                    assertEquals(expected.reader().readText(), actual.reader().readText())
                }
            }

            zipFile.getInputStream(entries.first { it.name == "newpipe.settings" }).use { actual ->
                val actualPreferences = ObjectInputStream(actual).readObject()
                assertEquals(expectedPreferences, actualPreferences)
            }
        }
    }

    @RunWith(MockitoJUnitRunner::class)
    class ImportTest {

        companion object {
            private lateinit var fileLocator: NewPipeFileLocator
            private lateinit var zip: File
            private lateinit var emptyZip: File
            private lateinit var db: File
            private lateinit var dbJournal: File
            private lateinit var dbWal: File
            private lateinit var dbShm: File
            private lateinit var settings: File

            @JvmStatic
            @BeforeClass
            fun setupReadOnlyFiles() {
                val zipPath = ImportTest::class.java.classLoader?.getResource("settings/newpipe.zip")?.file
                val emptyZipPath = ImportTest::class.java.classLoader?.getResource("settings/empty.zip")?.file
                Assume.assumeNotNull(zipPath)
                Assume.assumeNotNull(emptyZipPath)

                zip = File(zipPath!!)
                emptyZip = File(emptyZipPath!!)
            }
        }

        @Before
        fun setupWriteFiles() {
            db = File.createTempFile("newpipe_", "")
            dbJournal = File.createTempFile("newpipe_", "")
            dbWal = File.createTempFile("newpipe_", "")
            dbShm = File.createTempFile("newpipe_", "")
            settings = File.createTempFile("newpipe_", "")

            fileLocator = Mockito.mock(NewPipeFileLocator::class.java, withSettings().stubOnly())
            `when`(fileLocator.db).thenReturn(db)
            `when`(fileLocator.dbJournal).thenReturn(dbJournal)
            `when`(fileLocator.dbShm).thenReturn(dbShm)
            `when`(fileLocator.dbWal).thenReturn(dbWal)
            `when`(fileLocator.settings).thenReturn(settings)
        }

        @Test
        fun `The database must be extracted from the zip file`() {
            val success = ContentSettingsManager(fileLocator).extractDb(zip.path)

            assertTrue(success)
            assertFalse(dbJournal.exists())
            assertFalse(dbWal.exists())
            assertFalse(dbShm.exists())
            assertTrue("database file size is zero", Files.size(db.toPath()) > 0)
        }

        @Test
        fun `Extracting the database from an empty zip must not work`() {
            val success = ContentSettingsManager(fileLocator).extractDb(emptyZip.path)

            assertFalse(success)
            assertTrue(dbJournal.exists())
            assertTrue(dbWal.exists())
            assertTrue(dbShm.exists())
            assertEquals(0, Files.size(db.toPath()))
        }

        @Test
        fun `Contain setting must return true, if a settings file exists in the zip`() {
            val contains = ContentSettingsManager(fileLocator).containSettings(zip.path)

            assertTrue(contains)
        }

        @Test
        fun `Contain setting must return false, if a no settings file exists in the zip`() {
            val contains = ContentSettingsManager(fileLocator).containSettings(emptyZip.path)

            assertFalse(contains)
        }

        @Test
        fun `Preferences must be set from the settings file`() {
            val preferences = Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())
            val editor = Mockito.mock(SharedPreferences.Editor::class.java)
            `when`(preferences.edit()).thenReturn(editor)


            val manager = ContentSettingsManager(fileLocator)
            manager.containSettings(zip.path)
            manager.loadSharedPreferences(preferences)

            verify(editor, atLeastOnce()).putBoolean(anyString(), anyBoolean())
        }


    }


}
