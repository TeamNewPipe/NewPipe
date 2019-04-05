package us.shandian.giga.io;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class StoredDirectoryHelper {
    public final static int PERMISSION_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private File ioTree;
    private DocumentFile docTree;

    private ContentResolver contentResolver;

    private String tag;

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public StoredDirectoryHelper(@NonNull Context context, @NonNull Uri path, String tag) throws IOException {
        this.contentResolver = context.getContentResolver();
        this.tag = tag;
        this.docTree = DocumentFile.fromTreeUri(context, path);

        if (this.docTree == null)
            throw new IOException("Failed to create the tree from Uri");
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public StoredDirectoryHelper(@NonNull String location, String tag) {
        ioTree = new File(location);
        this.tag = tag;
    }

    @Nullable
    public StoredFileHelper createFile(String filename, String mime) {
        StoredFileHelper storage;

        try {
            if (docTree == null) {
                storage = new StoredFileHelper(ioTree, filename, tag);
                storage.sourceTree = Uri.fromFile(ioTree).toString();
            } else {
                storage = new StoredFileHelper(docTree, contentResolver, filename, mime, tag);
                storage.sourceTree = docTree.getUri().toString();
            }
        } catch (IOException e) {
            return null;
        }

        storage.tag = tag;

        return storage;
    }

    public StoredFileHelper createUniqueFile(String filename, String mime) {
        ArrayList<String> existingNames = new ArrayList<>(50);

        String ext;

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || (dotIndex == filename.length() - 1)) {
            ext = "";
        } else {
            ext = filename.substring(dotIndex);
            filename = filename.substring(0, dotIndex - 1);
        }

        String name;
        if (docTree == null) {
            for (File file : ioTree.listFiles()) {
                name = file.getName().toLowerCase();
                if (name.startsWith(filename)) existingNames.add(name);
            }
        } else {
            for (DocumentFile file : docTree.listFiles()) {
                name = file.getName();
                if (name == null) continue;
                name = name.toLowerCase();
                if (name.startsWith(filename)) existingNames.add(name);
            }
        }

        boolean free = true;
        String lwFilename = filename.toLowerCase();
        for (String testName : existingNames) {
            if (testName.equals(lwFilename)) {
                free = false;
                break;
            }
        }

        if (free) return createFile(filename, mime);

        String[] sortedNames = existingNames.toArray(new String[0]);
        Arrays.sort(sortedNames);

        String newName;
        int downloadIndex = 0;
        do {
            newName = filename + " (" + downloadIndex + ")" + ext;
            ++downloadIndex;
            if (downloadIndex == 1000) {  // Probably an error on our side
                newName = System.currentTimeMillis() + ext;
                break;
            }
        } while (Arrays.binarySearch(sortedNames, newName) >= 0);


        return createFile(newName, mime);
    }

    public boolean isDirect() {
        return docTree == null;
    }

    public Uri getUri() {
        return docTree == null ? Uri.fromFile(ioTree) : docTree.getUri();
    }

    public boolean exists() {
        return docTree == null ? ioTree.exists() : docTree.exists();
    }

    public String getTag() {
        return tag;
    }

    public void acquirePermissions() throws IOException {
        if (docTree == null) return;

        try {
            contentResolver.takePersistableUriPermission(docTree.getUri(), PERMISSION_FLAGS);
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    public void revokePermissions() throws IOException {
        if (docTree == null) return;

        try {
            contentResolver.releasePersistableUriPermission(docTree.getUri(), PERMISSION_FLAGS);
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    public Uri findFile(String filename) {
        if (docTree == null)
            return Uri.fromFile(new File(ioTree, filename));

        // findFile() method is very slow
        DocumentFile file = docTree.findFile(filename);

        return file == null ? null : file.getUri();
    }

    @NonNull
    @Override
    public String toString() {
        return docTree == null ? Uri.fromFile(ioTree).toString() : docTree.getUri().toString();
    }

}
