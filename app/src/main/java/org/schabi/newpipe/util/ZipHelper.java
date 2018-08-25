package org.schabi.newpipe.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by Christian Schabesberger on 28.01.18.
 * Copyright 2018 Christian Schabesberger <chris.schabesberger@mailbox.org>
 * ZipHelper.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

public class ZipHelper {

    private static final int BUFFER_SIZE = 2048;

    /**
     * This function helps to create zip files.
     * Caution this will override the original file.
     * @param outZip The ZipOutputStream where the data should be stored in
     * @param file The path of the file that should be added to zip.
     * @param name The path of the file inside the zip.
     * @throws Exception
     */
    public static void addFileToZip(ZipOutputStream outZip, String file, String name) throws Exception {
        byte data[] = new byte[BUFFER_SIZE];
        FileInputStream fi = new FileInputStream(file);
        BufferedInputStream inputStream = new BufferedInputStream(fi, BUFFER_SIZE);
        ZipEntry entry = new ZipEntry(name);
        outZip.putNextEntry(entry);
        int count;
        while((count = inputStream.read(data, 0, BUFFER_SIZE)) != -1) {
            outZip.write(data, 0, count);
        }
        inputStream.close();
    }

    /**
     * This will extract data from Zipfiles.
     * Caution this will override the original file.
     * @param file The path of the file on the disk where the data should be extracted to.
     * @param name The path of the file inside the zip.
     * @return will return true if the file was found within the zip file
     * @throws Exception
     */
    public static boolean extractFileFromZip(String filePath, String file, String name) throws Exception {

        ZipInputStream inZip = new ZipInputStream(
                new BufferedInputStream(
                        new FileInputStream(filePath)));

        byte data[] = new byte[BUFFER_SIZE];

        boolean found = false;

        ZipEntry ze;
        while((ze = inZip.getNextEntry()) != null) {
            if(ze.getName().equals(name)) {
                found = true;
                // delete old file first
                File oldFile = new File(file);
                if(oldFile.exists()) {
                    if(!oldFile.delete()) {
                        throw new Exception("Could not delete " + file);
                    }
                }

                FileOutputStream outFile = new FileOutputStream(file);
                int count = 0;
                while((count = inZip.read(data)) != -1) {
                    outFile.write(data, 0, count);
                }

                outFile.close();
                inZip.closeEntry();
            }
        }
        return found;
    }
}
