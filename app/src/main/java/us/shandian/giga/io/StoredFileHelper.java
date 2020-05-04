package us.shandian.giga.io;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;

public class StoredFileHelper implements Serializable {
    private static final long serialVersionUID = 0L;
    public static final String DEFAULT_MIME = "application/octet-stream";

    private transient DocumentFile docFile;
    private transient DocumentFile docTree;
    private transient File ioFile;
    private transient Context context;

    protected String source;
    private String sourceTree;

    protected String tag;

    private String srcName;
    private String srcType;

    public StoredFileHelper(@Nullable Uri parent, String filename, String mime, String tag) {
        this.source = null;// this instance will be "invalid" see invalidate()/isInvalid() methods

        this.srcName = filename;
        this.srcType = mime == null ? DEFAULT_MIME : mime;
        if (parent != null) this.sourceTree = parent.toString();

        this.tag = tag;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    StoredFileHelper(@Nullable Context context, DocumentFile tree, String filename, String mime, boolean safe) throws IOException {
        this.docTree = tree;
        this.context = context;

        DocumentFile res;

        if (safe) {
            // no conflicts (the filename is not in use)
            res = this.docTree.createFile(mime, filename);
            if (res == null) throw new IOException("Cannot create the file");
        } else {
            res = createSAF(context, mime, filename);
        }

        this.docFile = res;

        this.source = docFile.getUri().toString();
        this.sourceTree = docTree.getUri().toString();

        this.srcName = this.docFile.getName();
        this.srcType = this.docFile.getType();
    }

    StoredFileHelper(File location, String filename, String mime) throws IOException {
        this.ioFile = new File(location, filename);

        if (this.ioFile.exists()) {
            if (!this.ioFile.isFile() && !this.ioFile.delete())
                throw new IOException("The filename is already in use by non-file entity and cannot overwrite it");
        } else {
            if (!this.ioFile.createNewFile())
                throw new IOException("Cannot create the file");
        }

        this.source = Uri.fromFile(this.ioFile).toString();
        this.sourceTree = Uri.fromFile(location).toString();

        this.srcName = ioFile.getName();
        this.srcType = mime;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public StoredFileHelper(Context context, @Nullable Uri parent, @NonNull Uri path, String tag) throws IOException {
        this.tag = tag;
        this.source = path.toString();

        if (path.getScheme() == null || path.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE)) {
            this.ioFile = new File(URI.create(this.source));
        } else {
            DocumentFile file = DocumentFile.fromSingleUri(context, path);

            if (file == null) throw new RuntimeException("SAF not available");

            this.context = context;

            if (file.getName() == null) {
                this.source = null;
                return;
            } else {
                this.docFile = file;
                takePermissionSAF();
            }
        }

        if (parent != null) {
            if (!ContentResolver.SCHEME_FILE.equals(parent.getScheme()))
                this.docTree = DocumentFile.fromTreeUri(context, parent);

            this.sourceTree = parent.toString();
        }

        this.srcName = getName();
        this.srcType = getType();
    }


    public static StoredFileHelper deserialize(@NonNull StoredFileHelper storage, Context context) throws IOException {
        Uri treeUri = storage.sourceTree == null ? null : Uri.parse(storage.sourceTree);

        if (storage.isInvalid())
            return new StoredFileHelper(treeUri, storage.srcName, storage.srcType, storage.tag);

        StoredFileHelper instance = new StoredFileHelper(context, treeUri, Uri.parse(storage.source), storage.tag);

        // under SAF, if the target document is deleted, conserve the filename and mime
        if (instance.srcName == null) instance.srcName = storage.srcName;
        if (instance.srcType == null) instance.srcType = storage.srcType;

        return instance;
    }

    public static void requestSafWithFileCreation(@NonNull Fragment who, int requestCode, String filename, String mime) {
        // SAF notes:
        //           ACTION_OPEN_DOCUMENT       Do not let you create the file, useful for overwrite files
        //           ACTION_CREATE_DOCUMENT     No overwrite support, useless the file provider resolve the conflict

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime)
                .putExtra(Intent.EXTRA_TITLE, filename)
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | StoredDirectoryHelper.PERMISSION_FLAGS)
                .putExtra("android.content.extra.SHOW_ADVANCED", true);// hack, show all storage disks

        who.startActivityForResult(intent, requestCode);
    }


    public SharpStream getStream() throws IOException {
        invalid();

        if (docFile == null)
            return new FileStream(ioFile);
        else
            return new FileStreamSAF(context.getContentResolver(), docFile.getUri());
    }

    /**
     * Indicates whatever if is possible access using the {@code java.io} API
     *
     * @return {@code true} for Java I/O API, otherwise, {@code false} for Storage Access Framework
     */
    public boolean isDirect() {
        invalid();

        return docFile == null;
    }

    public boolean isInvalid() {
        return source == null;
    }

    public Uri getUri() {
        invalid();

        return docFile == null ? Uri.fromFile(ioFile) : docFile.getUri();
    }

    public Uri getParentUri() {
        invalid();

        return sourceTree == null ? null : Uri.parse(sourceTree);
    }

    public void truncate() throws IOException {
        invalid();

        try (SharpStream fs = getStream()) {
            fs.setLength(0);
        }
    }

    public boolean delete() {
        if (source == null) return true;
        if (docFile == null) return ioFile.delete();


        boolean res = docFile.delete();

        try {
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            context.getContentResolver().releasePersistableUriPermission(docFile.getUri(), flags);
        } catch (Exception ex) {
            // nothing to do
        }

        return res;
    }

    public long length() {
        invalid();

        return docFile == null ? ioFile.length() : docFile.length();
    }

    public boolean canWrite() {
        if (source == null) return false;
        return docFile == null ? ioFile.canWrite() : docFile.canWrite();
    }

    public String getName() {
        if (source == null)
            return srcName;
        else if (docFile == null)
            return ioFile.getName();

        String name = docFile.getName();
        return name == null ? srcName : name;
    }

    public String getType() {
        if (source == null || docFile == null)
            return srcType;

        String type = docFile.getType();
        return type == null ? srcType : type;
    }

    public String getTag() {
        return tag;
    }

    public boolean existsAsFile() {
        if (source == null) return false;

        // WARNING: DocumentFile.exists() and DocumentFile.isFile() methods are slow
        boolean exists = docFile == null ? ioFile.exists() : docFile.exists();
        boolean isFile = docFile == null ? ioFile.isFile() : docFile.isFile();// ¿docFile.isVirtual() means is no-physical?

        return exists && isFile;
    }

    public boolean create() {
        invalid();
        boolean result;

        if (docFile == null) {
            try {
                result = ioFile.createNewFile();
            } catch (IOException e) {
                return false;
            }
        } else if (docTree == null) {
            result = false;
        } else {
            if (!docTree.canRead() || !docTree.canWrite()) return false;
            try {
                docFile = createSAF(context, srcType, srcName);
                if (docFile == null || docFile.getName() == null) return false;
                result = true;
            } catch (IOException e) {
                return false;
            }
        }

        if (result) {
            source = (docFile == null ? Uri.fromFile(ioFile) : docFile.getUri()).toString();
            srcName = getName();
            srcType = getType();
        }

        return result;
    }

    public void invalidate() {
        if (source == null) return;

        srcName = getName();
        srcType = getType();

        source = null;

        docTree = null;
        docFile = null;
        ioFile = null;
        context = null;
    }

    public boolean equals(StoredFileHelper storage) {
        if (this == storage) return true;

        // note: do not compare tags, files can have the same parent folder
        //if (stringMismatch(this.tag, storage.tag)) return false;

        if (stringMismatch(getLowerCase(this.sourceTree), getLowerCase(this.sourceTree)))
            return false;

        if (this.isInvalid() || storage.isInvalid()) {
            return this.srcName.equalsIgnoreCase(storage.srcName) && this.srcType.equalsIgnoreCase(storage.srcType);
        }

        if (this.isDirect() != storage.isDirect()) return false;

        if (this.isDirect())
            return this.ioFile.getPath().equalsIgnoreCase(storage.ioFile.getPath());

        return DocumentsContract.getDocumentId(
                this.docFile.getUri()
        ).equalsIgnoreCase(DocumentsContract.getDocumentId(
                storage.docFile.getUri()
        ));
    }

    @NonNull
    @Override
    public String toString() {
        if (source == null)
            return "[Invalid state] name=" + srcName + "  type=" + srcType + "  tag=" + tag;
        else
            return "sourceFile=" + source + "  treeSource=" + (sourceTree == null ? "" : sourceTree) + "  tag=" + tag;
    }


    private void invalid() {
        if (source == null)
            throw new IllegalStateException("In invalid state");
    }

    private void takePermissionSAF() throws IOException {
        try {
            context.getContentResolver().takePersistableUriPermission(docFile.getUri(), StoredDirectoryHelper.PERMISSION_FLAGS);
        } catch (Exception e) {
            if (docFile.getName() == null) throw new IOException(e);
        }
    }

    private DocumentFile createSAF(@Nullable Context context, String mime, String filename) throws IOException {
        DocumentFile res = StoredDirectoryHelper.findFileSAFHelper(context, docTree, filename);

        if (res != null && res.exists() && res.isDirectory()) {
            if (!res.delete())
                throw new IOException("Directory with the same name found but cannot delete");
            res = null;
        }

        if (res == null) {
            res = this.docTree.createFile(srcType == null ? DEFAULT_MIME : mime, filename);
            if (res == null) throw new IOException("Cannot create the file");
        }

        return res;
    }

    private String getLowerCase(String str) {
        return str == null ? null : str.toLowerCase();
    }

    private boolean stringMismatch(String str1, String str2) {
        if (str1 == null && str2 == null) return false;
        if ((str1 == null) != (str2 == null)) return true;

        return !str1.equals(str2);
    }
}
