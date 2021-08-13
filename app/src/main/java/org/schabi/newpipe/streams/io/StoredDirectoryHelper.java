package org.schabi.newpipe.streams.io;

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

import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.util.FilePickerActivityHelper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;

import static android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME;
import static android.provider.DocumentsContract.Root.COLUMN_DOCUMENT_ID;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class StoredDirectoryHelper {
    public static final int PERMISSION_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private File ioTree;
    private DocumentFile docTree;

    private Context context;

    private final String tag;

    public StoredDirectoryHelper(@NonNull final Context context, @NonNull final Uri path,
                                 final String tag) throws IOException {
        this.tag = tag;

        if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(path.getScheme())) {
            this.ioTree = new File(URI.create(path.toString()));
            return;
        }

        this.context = context;

        try {
            this.context.getContentResolver().takePersistableUriPermission(path, PERMISSION_FLAGS);
        } catch (final Exception e) {
            throw new IOException(e);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            throw new IOException("Storage Access Framework with Directory API is not available");
        }

        this.docTree = DocumentFile.fromTreeUri(context, path);

        if (this.docTree == null) {
            throw new IOException("Failed to create the tree from Uri");
        }
    }

    public StoredFileHelper createFile(final String filename, final String mime) {
        return createFile(filename, mime, false);
    }

    public StoredFileHelper createUniqueFile(final String name, final String mime) {
        final ArrayList<String> matches = new ArrayList<>();
        final String[] filename = splitFilename(name);
        final String lcFilename = filename[0].toLowerCase();

        if (docTree == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            for (final File file : ioTree.listFiles()) {
                addIfStartWith(matches, lcFilename, file.getName());
            }
        } else {
            // warning: SAF file listing is very slow
            final Uri docTreeChildren = DocumentsContract.buildChildDocumentsUriUsingTree(
                    docTree.getUri(), DocumentsContract.getDocumentId(docTree.getUri()));

            final String[] projection = new String[]{COLUMN_DISPLAY_NAME};
            final String selection = "(LOWER(" + COLUMN_DISPLAY_NAME + ") LIKE ?%";
            final ContentResolver cr = context.getContentResolver();

            try (Cursor cursor = cr.query(docTreeChildren, projection, selection,
                    new String[]{lcFilename}, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        addIfStartWith(matches, lcFilename, cursor.getString(0));
                    }
                }
            }
        }

        if (matches.size() < 1) {
            return createFile(name, mime, true);
        } else {
            // check if the filename is in use
            String lcName = name.toLowerCase();
            for (final String testName : matches) {
                if (testName.equals(lcName)) {
                    lcName = null;
                    break;
                }
            }

            // check if not in use
            if (lcName != null) {
                return createFile(name, mime, true);
            }
        }

        Collections.sort(matches, String::compareTo);

        for (int i = 1; i < 1000; i++) {
            if (Collections.binarySearch(matches, makeFileName(lcFilename, i, filename[1])) < 0) {
                return createFile(makeFileName(filename[0], i, filename[1]), mime, true);
            }
        }

        return createFile(String.valueOf(System.currentTimeMillis()).concat(filename[1]), mime,
                false);
    }

    private StoredFileHelper createFile(final String filename, final String mime,
                                        final boolean safe) {
        final StoredFileHelper storage;

        try {
            if (docTree == null) {
                storage = new StoredFileHelper(ioTree, filename, mime);
            } else {
                storage = new StoredFileHelper(context, docTree, filename, mime, safe);
            }
        } catch (final IOException e) {
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
     * Indicates whether it's using the {@code java.io} API.
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

        if (docTree.exists()) {
            return true;
        }

        try {
            DocumentFile parent;
            String child = docTree.getName();

            while (true) {
                parent = docTree.getParentFile();
                if (parent == null || child == null) {
                    break;
                }
                if (parent.exists()) {
                    return true;
                }

                parent.createDirectory(child);

                child = parent.getName(); // for the next iteration
            }
        } catch (final Exception ignored) {
            // no more parent directories or unsupported by the storage provider
        }

        return false;
    }

    public String getTag() {
        return tag;
    }

    public Uri findFile(final String filename) {
        if (docTree == null) {
            final File res = new File(ioTree, filename);
            return res.exists() ? Uri.fromFile(res) : null;
        }

        final DocumentFile res = findFileSAFHelper(context, docTree, filename);
        return res == null ? null : res.getUri();
    }

    public boolean canWrite() {
        return docTree == null ? ioTree.canWrite() : docTree.canWrite();
    }

    /**
     * @return {@code false} if the storage is direct, or the SAF storage is valid; {@code true} if
     * SAF access to this SAF storage is denied (e.g. the user clicked on {@code Android settings ->
     * Apps & notifications -> NewPipe -> Storage & cache -> Clear access});
     */
    public boolean isInvalidSafStorage() {
        return docTree != null && docTree.getName() == null;
    }

    @NonNull
    @Override
    public String toString() {
        return (docTree == null ? Uri.fromFile(ioTree) : docTree.getUri()).toString();
    }

    ////////////////////
    //      Utils
    ///////////////////

    private static void addIfStartWith(final ArrayList<String> list, @NonNull final String base,
                                       final String str) {
        if (isNullOrEmpty(str)) {
            return;
        }
        final String lowerStr = str.toLowerCase();
        if (lowerStr.startsWith(base)) {
            list.add(lowerStr);
        }
    }

    private static String[] splitFilename(@NonNull final String filename) {
        final int dotIndex = filename.lastIndexOf('.');

        if (dotIndex < 0 || (dotIndex == filename.length() - 1)) {
            return new String[]{filename, ""};
        }

        return new String[]{filename.substring(0, dotIndex), filename.substring(dotIndex)};
    }

    private static String makeFileName(final String name, final int idx, final String ext) {
        return name.concat(" (").concat(String.valueOf(idx)).concat(")").concat(ext);
    }

    /**
     * Fast (but not enough) file/directory finder under the storage access framework.
     *
     * @param context  The context
     * @param tree     Directory where search
     * @param filename Target filename
     * @return A {@link DocumentFile} contain the reference, otherwise, null
     */
    static DocumentFile findFileSAFHelper(@Nullable final Context context, final DocumentFile tree,
                                          final String filename) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return tree.findFile(filename); // warning: this is very slow
        }

        if (!tree.canRead()) {
            return null; // missing read permission
        }

        final int name = 0;
        final int documentId = 1;

        // LOWER() SQL function is not supported
        final String selection = COLUMN_DISPLAY_NAME + " = ?";
        //final String selection = COLUMN_DISPLAY_NAME + " LIKE ?%";

        final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree.getUri(),
                DocumentsContract.getDocumentId(tree.getUri()));
        final String[] projection = {COLUMN_DISPLAY_NAME, COLUMN_DOCUMENT_ID};
        final ContentResolver contentResolver = context.getContentResolver();

        final String lowerFilename = filename.toLowerCase();

        try (Cursor cursor = contentResolver.query(childrenUri, projection, selection,
                new String[]{lowerFilename}, null)) {
            if (cursor == null) {
                return null;
            }

            while (cursor.moveToNext()) {
                if (cursor.isNull(name)
                        || !cursor.getString(name).toLowerCase().startsWith(lowerFilename)) {
                    continue;
                }

                return DocumentFile.fromSingleUri(context,
                        DocumentsContract.buildDocumentUriUsingTree(tree.getUri(),
                                cursor.getString(documentId)));
            }
        }

        return null;
    }

    public static Intent getPicker(final Context ctx) {
        if (NewPipeSettings.useStorageAccessFramework(ctx)) {
            return new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .putExtra("android.content.extra.SHOW_ADVANCED", true)
                    .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | StoredDirectoryHelper.PERMISSION_FLAGS);
        } else {
            return new Intent(ctx, FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE,
                            FilePickerActivityHelper.MODE_DIR);
        }
    }
}
