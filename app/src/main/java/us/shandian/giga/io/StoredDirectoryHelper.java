package us.shandian.giga.io;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;

import static android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME;
import static android.provider.DocumentsContract.Root.COLUMN_DOCUMENT_ID;


public class StoredDirectoryHelper {
    public final static int PERMISSION_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private File ioTree;
    private DocumentFile docTree;

    private Context context;

    private String tag;

    public StoredDirectoryHelper(@NonNull Context context, @NonNull Uri path, String tag) throws IOException {
        this.tag = tag;

        if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(path.getScheme())) {
            this.ioTree = new File(URI.create(path.toString()));
            return;
        }

        this.context = context;

        try {
            this.context.getContentResolver().takePersistableUriPermission(path, PERMISSION_FLAGS);
        } catch (Exception e) {
            throw new IOException(e);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            throw new IOException("Storage Access Framework with Directory API is not available");

        this.docTree = DocumentFile.fromTreeUri(context, path);

        if (this.docTree == null)
            throw new IOException("Failed to create the tree from Uri");
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public StoredDirectoryHelper(@NonNull URI location, String tag) {
        ioTree = new File(location);
        this.tag = tag;
    }

    public StoredFileHelper createFile(String filename, String mime) {
        return createFile(filename, mime, false);
    }

    public StoredFileHelper createUniqueFile(String name, String mime) {
        ArrayList<String> matches = new ArrayList<>();
        String[] filename = splitFilename(name);
        String lcFilename = filename[0].toLowerCase();

        if (docTree == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            for (File file : ioTree.listFiles())
                addIfStartWith(matches, lcFilename, file.getName());
        } else {
            // warning: SAF file listing is very slow
            Uri docTreeChildren = DocumentsContract.buildChildDocumentsUriUsingTree(
                    docTree.getUri(), DocumentsContract.getDocumentId(docTree.getUri())
            );

            String[] projection = new String[]{COLUMN_DISPLAY_NAME};
            String selection = "(LOWER(" + COLUMN_DISPLAY_NAME + ") LIKE ?%";
            ContentResolver cr = context.getContentResolver();

            try (Cursor cursor = cr.query(docTreeChildren, projection, selection, new String[]{lcFilename}, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext())
                        addIfStartWith(matches, lcFilename, cursor.getString(0));
                }
            }
        }

        if (matches.size() < 1) {
            return createFile(name, mime, true);
        } else {
            // check if the filename is in use
            String lcName = name.toLowerCase();
            for (String testName : matches) {
                if (testName.equals(lcName)) {
                    lcName = null;
                    break;
                }
            }

            // check if not in use
            if (lcName != null) return createFile(name, mime, true);
        }

        Collections.sort(matches, String::compareTo);

        for (int i = 1; i < 1000; i++) {
            if (Collections.binarySearch(matches, makeFileName(lcFilename, i, filename[1])) < 0)
                return createFile(makeFileName(filename[0], i, filename[1]), mime, true);
        }

        return createFile(String.valueOf(System.currentTimeMillis()).concat(filename[1]), mime, false);
    }

    private StoredFileHelper createFile(String filename, String mime, boolean safe) {
        StoredFileHelper storage;

        try {
            if (docTree == null)
                storage = new StoredFileHelper(ioTree, filename, mime);
            else
                storage = new StoredFileHelper(context, docTree, filename, mime, safe);
        } catch (IOException e) {
            return null;
        }

        storage.tag = tag;

        return storage;
    }

    public Uri getUri() {
        return docTree == null ? Uri.fromFile(ioTree) : docTree.getUri();
    }

    public boolean exists() {
        return docTree == null ? ioTree.exists() : docTree.exists();
    }

    /**
     * Indicates whatever if is possible access using the {@code java.io} API
     *
     * @return {@code true} for Java I/O API, otherwise, {@code false} for Storage Access Framework
     */
    public boolean isDirect() {
        return docTree == null;
    }

    /**
     * Only using Java I/O. Creates the directory named by this abstract pathname, including any
     * necessary but nonexistent parent directories.  Note that if this
     * operation fails it may have succeeded in creating some of the necessary
     * parent directories.
     *
     * @return <code>true</code> if and only if the directory was created,
     * along with all necessary parent directories or already exists; <code>false</code>
     * otherwise
     */
    public boolean mkdirs() {
        if (docTree == null) {
            return ioTree.exists() || ioTree.mkdirs();
        }

        if (docTree.exists()) return true;

        try {
            DocumentFile parent;
            String child = docTree.getName();

            while (true) {
                parent = docTree.getParentFile();
                if (parent == null || child == null) break;
                if (parent.exists()) return true;

                parent.createDirectory(child);

                child = parent.getName();// for the next iteration
            }
        } catch (Exception e) {
            // no more parent directories or unsupported by the storage provider
        }

        return false;
    }

    public String getTag() {
        return tag;
    }

    public Uri findFile(String filename) {
        if (docTree == null) {
            File res = new File(ioTree, filename);
            return res.exists() ? Uri.fromFile(res) : null;
        }

        DocumentFile res = findFileSAFHelper(context, docTree, filename);
        return res == null ? null : res.getUri();
    }

    public boolean canWrite() {
        return docTree == null ? ioTree.canWrite() : docTree.canWrite();
    }

    @NonNull
    @Override
    public String toString() {
        return docTree == null ? Uri.fromFile(ioTree).toString() : docTree.getUri().toString();
    }


    ////////////////////
    //      Utils
    ///////////////////

    private static void addIfStartWith(ArrayList<String> list, @NonNull String base, String str) {
        if (str == null || str.isEmpty()) return;
        str = str.toLowerCase();
        if (str.startsWith(base)) list.add(str);
    }

    private static String[] splitFilename(@NonNull String filename) {
        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex < 0 || (dotIndex == filename.length() - 1))
            return new String[]{filename, ""};

        return new String[]{filename.substring(0, dotIndex), filename.substring(dotIndex)};
    }

    private static String makeFileName(String name, int idx, String ext) {
        return name.concat(" (").concat(String.valueOf(idx)).concat(")").concat(ext);
    }

    /**
     * Fast (but not enough) file/directory finder under the storage access framework
     *
     * @param context  The context
     * @param tree     Directory where search
     * @param filename Target filename
     * @return A {@link DocumentFile} contain the reference, otherwise, null
     */
    static DocumentFile findFileSAFHelper(@Nullable Context context, DocumentFile tree, String filename) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return tree.findFile(filename);// warning: this is very slow
        }

        if (!tree.canRead()) return null;// missing read permission

        final int name = 0;
        final int documentId = 1;

        // LOWER() SQL function is not supported
        String selection = COLUMN_DISPLAY_NAME + " = ?";
        //String selection = COLUMN_DISPLAY_NAME + " LIKE ?%";

        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree.getUri(), DocumentsContract.getDocumentId(tree.getUri())
        );
        String[] projection = {COLUMN_DISPLAY_NAME, COLUMN_DOCUMENT_ID};
        ContentResolver contentResolver = context.getContentResolver();

        filename = filename.toLowerCase();

        try (Cursor cursor = contentResolver.query(childrenUri, projection, selection, new String[]{filename}, null)) {
            if (cursor == null) return null;

            while (cursor.moveToNext()) {
                if (cursor.isNull(name) || !cursor.getString(name).toLowerCase().startsWith(filename))
                    continue;

                return DocumentFile.fromSingleUri(
                        context, DocumentsContract.buildDocumentUriUsingTree(
                                tree.getUri(), cursor.getString(documentId)
                        )
                );
            }
        }

        return null;
    }

}
