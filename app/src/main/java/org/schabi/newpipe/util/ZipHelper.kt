package org.schabi.newpipe.util

import org.schabi.newpipe.streams.io.SharpInputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Created by Christian Schabesberger on 28.01.18.
 * Copyright 2018 Christian Schabesberger <chris.schabesberger></chris.schabesberger>@mailbox.org>
 * ZipHelper.java is part of NewPipe
 *
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
object ZipHelper {
    private const val BUFFER_SIZE = 2048

    /**
     * This function helps to create zip files.
     * Caution this will override the original file.
     *
     * @param outZip The ZipOutputStream where the data should be stored in
     * @param file   The path of the file that should be added to zip.
     * @param name   The path of the file inside the zip.
     * @throws Exception
     */
    @Throws(Exception::class)
    fun addFileToZip(outZip: ZipOutputStream, file: String?,
                     name: String?) {
        val data = ByteArray(BUFFER_SIZE)
        FileInputStream(file).use { fi ->
            BufferedInputStream(fi, BUFFER_SIZE).use { inputStream ->
                val entry = ZipEntry(name)
                outZip.putNextEntry(entry)
                var count: Int
                while (inputStream.read(data, 0, BUFFER_SIZE).also { count = it } != -1) {
                    outZip.write(data, 0, count)
                }
            }
        }
    }

    /**
     * This will extract data from ZipInputStream.
     * Caution this will override the original file.
     *
     * @param zipFile The zip file
     * @param file The path of the file on the disk where the data should be extracted to.
     * @param name The path of the file inside the zip.
     * @return will return true if the file was found within the zip file
     * @throws Exception
     */
    @Throws(Exception::class)
    fun extractFileFromZip(zipFile: StoredFileHelper, file: String,
                           name: String): Boolean {
        ZipInputStream(BufferedInputStream(
                SharpInputStream(zipFile.getStream()))).use { inZip ->
            val data = ByteArray(BUFFER_SIZE)
            var found = false
            var ze: ZipEntry
            while (inZip.getNextEntry().also { ze = it } != null) {
                if (ze.name == name) {
                    found = true
                    // delete old file first
                    val oldFile = File(file)
                    if (oldFile.exists()) {
                        if (!oldFile.delete()) {
                            throw Exception("Could not delete $file")
                        }
                    }
                    FileOutputStream(file).use { outFile ->
                        var count = 0
                        while (inZip.read(data).also { count = it } != -1) {
                            outFile.write(data, 0, count)
                        }
                    }
                    inZip.closeEntry()
                }
            }
            return found
        }
    }

    fun isValidZipFile(file: StoredFileHelper): Boolean {
        try {
            ZipInputStream(BufferedInputStream(
                    SharpInputStream(file.getStream()))).use { ignored -> return true }
        } catch (ioe: IOException) {
            return false
        }
    }
}
