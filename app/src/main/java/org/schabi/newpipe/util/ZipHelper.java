package org.schabi.newpipe.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
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
    private ZipHelper() { }

    private static final int BUFFER_SIZE = 2048;

    /**
     * This function helps to create zip files.
     * Caution this will override the original file.
     *
     * @param outZip The ZipOutputStream where the data should be stored in
     * @param file   The path of the file that should be added to zip.
     * @param name   The path of the file inside the zip.
     * @throws Exception
     */
    public static void addFileToZip(final ZipOutputStream outZip, final String file,
                                    final String name) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        outZip.putNextEntry(entry);
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            // copy() buffers the input stream internally, so separate buffering is not needed.
            IOUtils.copy(fileInputStream, outZip, BUFFER_SIZE);
        }
    }

    /**
     * This will extract data from Zipfiles.
     * Caution this will override the original file.
     *
     * @param filePath The path of the zip
     * @param file The path of the file on the disk where the data should be extracted to.
     * @param name The path of the file inside the zip.
     * @return will return true if the file was found within the zip file
     * @throws Exception
     */
    public static boolean extractFileFromZip(final String filePath, final String file,
                                             final String name) throws Exception {
        ZipInputStream inZip = new ZipInputStream(new FileInputStream(filePath));

        boolean found = false;

        ZipEntry ze;
        while ((ze = inZip.getNextEntry()) != null) {
            if (ze.getName().equals(name)) {
                found = true;
                File oldFile = new File(file);
                // copyToFile() will overwrite the old file if it already exists.
                // It also calls IOUtils.copy(), so separate buffering is not needed.
                FileUtils.copyToFile(inZip, oldFile);
                inZip.closeEntry();
            }
        }
        return found;
    }
}
