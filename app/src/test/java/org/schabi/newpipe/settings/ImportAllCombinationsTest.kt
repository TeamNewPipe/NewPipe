package org.schabi.newpipe.settings

import android.content.SharedPreferences
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import org.schabi.newpipe.settings.export.BackupFileLocator
import org.schabi.newpipe.settings.export.ImportExportManager
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.io.FileStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ImportAllCombinationsTest {

    companion object {
        private val classloader = ImportExportManager::class.java.classLoader!!
    }

    private enum class Ser(val id: String) {
        YES("ser"),
        VULNERABLE("vulnser"),
        NO("noser");
    }

    private data class FailData(
        val containsDb: Boolean,
        val containsSer: Ser,
        val containsJson: Boolean,
        val filename: String,
        val throwable: Throwable,
    )

    private fun testZipCombination(
        containsDb: Boolean,
        containsSer: Ser,
        containsJson: Boolean,
        filename: String,
        runTest: (test: () -> Unit) -> Unit,
    ) {
        val zipFile = File(classloader.getResource(filename)?.file!!)
        val zip = Mockito.mock(StoredFileHelper::class.java, Mockito.withSettings().stubOnly())
        Mockito.`when`(zip.stream).then { FileStream(zipFile) }

        val fileLocator = Mockito.mock(
            BackupFileLocator::class.java,
            Mockito.withSettings().stubOnly()
        )
        val db = File.createTempFile("newpipe_", "")
        val dbJournal = File.createTempFile("newpipe_", "")
        val dbWal = File.createTempFile("newpipe_", "")
        val dbShm = File.createTempFile("newpipe_", "")
        Mockito.`when`(fileLocator.db).thenReturn(db)
        Mockito.`when`(fileLocator.dbJournal).thenReturn(dbJournal)
        Mockito.`when`(fileLocator.dbShm).thenReturn(dbShm)
        Mockito.`when`(fileLocator.dbWal).thenReturn(dbWal)

        if (containsDb) {
            runTest {
                Assert.assertTrue(ImportExportManager(fileLocator).extractDb(zip))
                Assert.assertFalse(dbJournal.exists())
                Assert.assertFalse(dbWal.exists())
                Assert.assertFalse(dbShm.exists())
                Assert.assertTrue("database file size is zero", Files.size(db.toPath()) > 0)
            }
        } else {
            runTest {
                Assert.assertFalse(ImportExportManager(fileLocator).extractDb(zip))
                Assert.assertTrue(dbJournal.exists())
                Assert.assertTrue(dbWal.exists())
                Assert.assertTrue(dbShm.exists())
                Assert.assertEquals(0, Files.size(db.toPath()))
            }
        }

        val preferences = Mockito.mock(SharedPreferences::class.java, Mockito.withSettings().stubOnly())
        var editor = Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(preferences.edit()).thenReturn(editor)
        Mockito.`when`(editor.commit()).thenReturn(true)

        when (containsSer) {
            Ser.YES -> runTest {
                Assert.assertTrue(ImportExportManager(fileLocator).exportHasSerializedPrefs(zip))
                ImportExportManager(fileLocator).loadSerializedPrefs(zip, preferences)

                Mockito.verify(editor, Mockito.times(1)).clear()
                Mockito.verify(editor, Mockito.times(1)).commit()
                Mockito.verify(editor, Mockito.atLeastOnce())
                    .putBoolean(Mockito.anyString(), Mockito.anyBoolean())
                Mockito.verify(editor, Mockito.atLeastOnce())
                    .putString(Mockito.anyString(), Mockito.anyString())
                Mockito.verify(editor, Mockito.atLeastOnce())
                    .putInt(Mockito.anyString(), Mockito.anyInt())
            }
            Ser.VULNERABLE -> runTest {
                Assert.assertTrue(ImportExportManager(fileLocator).exportHasSerializedPrefs(zip))
                Assert.assertThrows(ClassNotFoundException::class.java) {
                    ImportExportManager(fileLocator).loadSerializedPrefs(zip, preferences)
                }

                Mockito.verify(editor, Mockito.never()).clear()
                Mockito.verify(editor, Mockito.never()).commit()
            }
            Ser.NO -> runTest {
                Assert.assertFalse(ImportExportManager(fileLocator).exportHasSerializedPrefs(zip))
                Assert.assertThrows(IOException::class.java) {
                    ImportExportManager(fileLocator).loadSerializedPrefs(zip, preferences)
                }

                Mockito.verify(editor, Mockito.never()).clear()
                Mockito.verify(editor, Mockito.never()).commit()
            }
        }

        // recreate editor mock so verify() behaves correctly
        editor = Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(preferences.edit()).thenReturn(editor)
        Mockito.`when`(editor.commit()).thenReturn(true)

        if (containsJson) {
            runTest {
                Assert.assertTrue(ImportExportManager(fileLocator).exportHasJsonPrefs(zip))
                ImportExportManager(fileLocator).loadJsonPrefs(zip, preferences)

                Mockito.verify(editor, Mockito.times(1)).clear()
                Mockito.verify(editor, Mockito.times(1)).commit()
                Mockito.verify(editor, Mockito.atLeastOnce())
                    .putBoolean(Mockito.anyString(), Mockito.anyBoolean())
                Mockito.verify(editor, Mockito.atLeastOnce())
                    .putString(Mockito.anyString(), Mockito.anyString())
                Mockito.verify(editor, Mockito.atLeastOnce())
                    .putInt(Mockito.anyString(), Mockito.anyInt())
            }
        } else {
            runTest {
                Assert.assertFalse(ImportExportManager(fileLocator).exportHasJsonPrefs(zip))
                Assert.assertThrows(IOException::class.java) {
                    ImportExportManager(fileLocator).loadJsonPrefs(zip, preferences)
                }

                Mockito.verify(editor, Mockito.never()).clear()
                Mockito.verify(editor, Mockito.never()).commit()
            }
        }
    }

    @Test
    fun `Importing all possible combinations of zip files`() {
        val failedAssertions = mutableListOf<FailData>()
        for (containsDb in listOf(true, false)) {
            for (containsSer in Ser.entries) {
                for (containsJson in listOf(true, false)) {
                    val filename = "settings/${if (containsDb) "db" else "nodb"}_${
                    containsSer.id}_${if (containsJson) "json" else "nojson"}.zip"
                    testZipCombination(containsDb, containsSer, containsJson, filename) { test ->
                        try {
                            test()
                        } catch (e: Throwable) {
                            failedAssertions.add(
                                FailData(
                                    containsDb, containsSer, containsJson,
                                    filename, e
                                )
                            )
                        }
                    }
                }
            }
        }

        if (failedAssertions.isNotEmpty()) {
            for (a in failedAssertions) {
                println(
                    "Assertion failed with containsDb=${a.containsDb}, containsSer=${
                    a.containsSer}, containsJson=${a.containsJson}, filename=${a.filename}:"
                )
                a.throwable.printStackTrace()
                println()
            }
            Assert.fail("${failedAssertions.size} assertions failed")
        }
    }
}
