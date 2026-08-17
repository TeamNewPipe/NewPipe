/*
 * SPDX-FileCopyrightText: 2018-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import org.schabi.newpipe.streams.io.SharpInputStream
import org.schabi.newpipe.streams.io.StoredFileHelper
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import okio.Buffer
import okio.IOException

object ZipHelper {
    fun interface InputStreamConsumer {
        @Throws(IOException::class)
        fun acceptStream(inputStream: InputStream)
    }

    fun interface OutputStreamConsumer {
        @Throws(IOException::class)
        fun acceptStream(outputStream: OutputStream)
    }

    /**
     * This function helps to create zip files. Caution, this will overwrite the original file.
     *
     * @param outZip     the ZipOutputStream where the data should be stored in
     * @param nameInZip  the path of the file inside the zip
     * @param path       the path of the file on the disk that should be added to zip
     */
    @JvmStatic
    @Throws(IOException::class)
    fun addFileToZip(
        outZip: ZipOutputStream,
        nameInZip: String,
        path: Path
    ) {
        Files.newInputStream(path).use { inputStream ->
            addFileToZip(outZip, nameInZip, inputStream)
        }
    }

    /**
     * This function helps to create zip files. Caution this will overwrite the original file.
     *
     * @param outZip         the ZipOutputStream where the data should be stored in
     * @param nameInZip      the path of the file inside the zip
     * @param streamConsumer will be called with an output stream that will go to the output file
     */
    @JvmStatic
    @Throws(IOException::class)
    fun addFileToZip(
        outZip: ZipOutputStream,
        nameInZip: String,
        streamConsumer: OutputStreamConsumer
    ) {
        val buffer = Buffer()
        streamConsumer.acceptStream(buffer.outputStream())
        addFileToZip(outZip, nameInZip, buffer.inputStream())
    }

    /**
     * This function helps to create zip files. Caution this will overwrite the original file.
     *
     * @param outZip      the ZipOutputStream where the data should be stored in
     * @param nameInZip   the path of the file inside the zip
     * @param inputStream the content to put inside the file
     */
    @Throws(IOException::class)
    private fun addFileToZip(
        outZip: ZipOutputStream,
        nameInZip: String,
        inputStream: InputStream
    ) {
        outZip.putNextEntry(ZipEntry(nameInZip))
        inputStream.transferTo(outZip)
    }

    /**
     * This will extract data from ZipInputStream. Caution, this will overwrite the original file.
     *
     * @param zipFile    the zip file to extract from
     * @param nameInZip  the path of the file inside the zip
     * @param path       the path of the file on the disk where the data should be extracted to
     * @return will return true if the file was found within the zip file
     */
    @JvmStatic
    @Throws(IOException::class)
    fun extractFileFromZip(
        zipFile: StoredFileHelper,
        nameInZip: String,
        path: Path
    ): Boolean {
        return extractFileFromZip(zipFile, nameInZip) { input ->
            Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * This will extract data from ZipInputStream.
     *
     * @param zipFile        the zip file to extract from
     * @param nameInZip      the path of the file inside the zip
     * @param streamConsumer will be called with the input stream from the file inside the zip
     * @return will return true if the file was found within the zip file
     */
    @JvmStatic
    @Throws(IOException::class)
    fun extractFileFromZip(
        zipFile: StoredFileHelper,
        nameInZip: String,
        streamConsumer: InputStreamConsumer
    ): Boolean {
        ZipInputStream(
            BufferedInputStream(
                SharpInputStream(zipFile.getStream())
            )
        ).use { inZip ->
            var ze: ZipEntry?
            while (inZip.nextEntry.also { ze = it } != null) {
                if (ze?.name == nameInZip) {
                    streamConsumer.acceptStream(inZip)
                    return true
                }
            }

            return false
        }
    }

    /**
     * @param zipFile   the zip file
     * @param fileInZip the filename to check
     * @return whether the provided filename is in the zip; only the first level is checked
     */
    @JvmStatic
    @Throws(Exception::class)
    fun zipContainsFile(zipFile: StoredFileHelper, fileInZip: String): Boolean {
        ZipInputStream(
            BufferedInputStream(
                SharpInputStream(zipFile.getStream())
            )
        ).use { inZip ->
            var ze: ZipEntry?
            while (inZip.nextEntry.also { ze = it } != null) {
                if (ze?.name == fileInZip) {
                    return true
                }
            }
            return false
        }
    }

    @JvmStatic
    fun isValidZipFile(file: StoredFileHelper): Boolean {
        return try {
            ZipInputStream(
                BufferedInputStream(
                    SharpInputStream(file.getStream())
                )
            ).use { }
            true
        } catch (ioe: IOException) {
            false
        }
    }
}
