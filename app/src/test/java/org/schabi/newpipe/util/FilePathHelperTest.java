package org.schabi.newpipe.util;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FilePathHelperTest {

    private Path dir;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("dir1");
    }

    @Test
    public void testIsValidDirectoryPathWithEmptyString() {
        assertFalse(FilePathUtils.isValidDirectoryPath(""));
    }

    @Test
    public void testIsValidDirectoryPathWithNullString() {
        assertFalse(FilePathUtils.isValidDirectoryPath(null));
    }

    @Test
    public void testIsValidDirectoryPathWithValidPath() {
        assertTrue(FilePathUtils.isValidDirectoryPath(dir.toAbsolutePath().toString()));
    }

    @Test
    public void testIsValidDirectoryPathWithDeepValidDirectory() throws IOException {
        final File subDir = Files.createDirectory(dir.resolve("subdir")).toFile();
        assertTrue(FilePathUtils.isValidDirectoryPath(subDir.getAbsolutePath()));
    }

    @Test
    public void testIsValidDirectoryPathWithNotExistDirectory() {
        assertFalse(FilePathUtils.isValidDirectoryPath(dir.resolve("not-exists-subdir").
            toFile().getAbsolutePath()));
    }

    @Test
    public void testIsValidDirectoryPathWithFile() throws IOException {
        final File tempFile = Files.createFile(dir.resolve("simple_file")).toFile();
        assertFalse(FilePathUtils.isValidDirectoryPath(tempFile.getAbsolutePath()));
    }

}
