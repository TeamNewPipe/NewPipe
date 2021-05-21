package org.schabi.newpipe.util;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FilePathHelperTest {
    @Test
    public void testIsValidDirectoryPath() throws IOException {
        // path that exists
        final File dir1 = Files.createTempDirectory("dir1").toFile();
        assertTrue(FilePathUtils.isValidDirectoryPath(dir1.getAbsolutePath()));

        // a directory in above path that exists
        final File subDir = Files.createDirectory(dir1.toPath().resolve("subdir")).toFile();
        assertTrue(FilePathUtils.isValidDirectoryPath(subDir.getAbsolutePath()));

        // a directory in above path that doesn't exist
        assertFalse(FilePathUtils.isValidDirectoryPath(dir1.toPath().resolve("not-exists-subdir").
                toFile().getAbsolutePath()));

        // file is not a valid direcotry path
        final File tempFile = Files.createFile(dir1.toPath().resolve("simple_file")).toFile();
        assertFalse(FilePathUtils.isValidDirectoryPath(tempFile.getAbsolutePath()));
    }

}
