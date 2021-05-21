package org.schabi.newpipe.util;

import java.io.File;

public final class FilePathUtils {
    private FilePathUtils() { }


    /**
     * Check that the path is a valid directory path and it exists.
     *
     * @param path full path of directory,
     * @return is path valid or not
     */
    public static boolean isValidDirectoryPath(final String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        final File file = new File(path);
        return file.exists() && file.isDirectory();
    }
}
