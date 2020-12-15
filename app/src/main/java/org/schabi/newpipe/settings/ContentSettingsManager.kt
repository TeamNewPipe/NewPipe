package org.schabi.newpipe.settings

import android.content.SharedPreferences
import org.schabi.newpipe.util.ZipHelper
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.lang.Exception
import java.util.zip.ZipOutputStream

class ContentSettingsManager(
    private val newpipeDb: File,
    private val newpipeSettings: File
) {

    constructor(homeDir: String) : this(
        File("$homeDir/databases/newpipe.db"),
        File("$homeDir/databases/newpipe.settings")
    )

    /**
     * Exports given [SharedPreferences] to the file in given outputPath.
     * It also creates the file.
     */
    @Throws(Exception::class)
    fun exportDatabase(preferences: SharedPreferences, outputPath: String) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputPath)))
            .use { outZip ->
                ZipHelper.addFileToZip(outZip, newpipeDb.path, "newpipe.db")

                try {
                    ObjectOutputStream(FileOutputStream(newpipeSettings)).use { output ->
                        output.writeObject(preferences.all)
                        output.flush()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                ZipHelper.addFileToZip(outZip, newpipeSettings.path, "newpipe.settings")
            }
    }
}
