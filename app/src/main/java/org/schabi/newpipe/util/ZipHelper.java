package org.schabi.newpipe.util;

import org.schabi.newpipe.streams.io.SharpInputStream;
import org.schabi.newpipe.streams.io.StoredFileHelper;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by Christian Schabesberger on 28.01.18.
 * Copyright 2018 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * ZipHelper.java is part of NewPipe
 * <p>
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

public final class ZipHelper {
    @FunctionalInterface
    public interface InputStreamConsumer {
        void acceptStream(InputStream inputStream) throws IOException;
    }

    @FunctionalInterface
    public interface OutputStreamConsumer {
        void acceptStream(OutputStream outputStream) throws IOException;
    }


    private ZipHelper() { }


    /**
     * This function helps to create zip files. Caution, this will overwrite the original file.
     *
     * @param outZip     the ZipOutputStream where the data should be stored in
     * @param nameInZip  the path of the file inside the zip
     * @param path       the path of the file on the disk that should be added to zip
     */
    public static void addFileToZip(final ZipOutputStream outZip,
                                    final String nameInZip,
                                    final Path path) throws IOException {
        try (var inputStream = Files.newInputStream(path)) {
            addFileToZip(outZip, nameInZip, inputStream);
        }
    }

    /**
     * This function helps to create zip files. Caution this will overwrite the original file.
     *
     * @param outZip         the ZipOutputStream where the data should be stored in
     * @param nameInZip      the path of the file inside the zip
     * @param streamConsumer will be called with an output stream that will go to the output file
     */
    public static void addFileToZip(final ZipOutputStream outZip,
                                    final String nameInZip,
                                    final OutputStreamConsumer streamConsumer) throws IOException {
        final byte[] bytes;
        try (var byteOutput = new ByteArrayOutputStream()) {
            streamConsumer.acceptStream(byteOutput);
            bytes = byteOutput.toByteArray();
        }

        try (var byteInput = new ByteArrayInputStream(bytes)) {
            addFileToZip(outZip, nameInZip, byteInput);
        }
    }

    /**
     * This function helps to create zip files. Caution this will overwrite the original file.
     *
     * @param outZip      the ZipOutputStream where the data should be stored in
     * @param nameInZip   the path of the file inside the zip
     * @param inputStream the content to put inside the file
     */
    private static void addFileToZip(final ZipOutputStream outZip,
                                     final String nameInZip,
                                     final InputStream inputStream) throws IOException {
        outZip.putNextEntry(new ZipEntry(nameInZip));
        inputStream.transferTo(outZip);
    }

    /**
     * This will extract data from ZipInputStream. Caution, this will overwrite the original file.
     *
     * @param zipFile    the zip file to extract from
     * @param nameInZip  the path of the file inside the zip
     * @param path       the path of the file on the disk where the data should be extracted to
     * @return will return true if the file was found within the zip file
     */
    public static boolean extractFileFromZip(final StoredFileHelper zipFile,
                                             final String nameInZip,
                                             final Path path) throws IOException {
        return extractFileFromZip(zipFile, nameInZip, input ->
                Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING));
    }

    /**
     * This will extract data from ZipInputStream.
     *
     * @param zipFile        the zip file to extract from
     * @param nameInZip      the path of the file inside the zip
     * @param streamConsumer will be called with the input stream from the file inside the zip
     * @return will return true if the file was found within the zip file
     */
    public static boolean extractFileFromZip(final StoredFileHelper zipFile,
                                             final String nameInZip,
                                             final InputStreamConsumer streamConsumer)
            throws IOException {
        try (ZipInputStream inZip = new ZipInputStream(new BufferedInputStream(
                new SharpInputStream(zipFile.getStream())))) {
            ZipEntry ze;
            while ((ze = inZip.getNextEntry()) != null) {
                if (ze.getName().equals(nameInZip)) {
                    streamConsumer.acceptStream(inZip);
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * @param zipFile   the zip file
     * @param fileInZip the filename to check
     * @return whether the provided filename is in the zip; only the first level is checked
     */
    public static boolean zipContainsFile(final StoredFileHelper zipFile, final String fileInZip)
            throws Exception {
        try (ZipInputStream inZip = new ZipInputStream(new BufferedInputStream(
                new SharpInputStream(zipFile.getStream())))) {
            ZipEntry ze;

            while ((ze = inZip.getNextEntry()) != null) {
                if (ze.getName().equals(fileInZip)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean isValidZipFile(final StoredFileHelper file) {
        try (ZipInputStream ignored = new ZipInputStream(new BufferedInputStream(
                new SharpInputStream(file.getStream())))) {
            return true;
        } catch (final IOException ioe) {
            return false;
        }
    }
}
