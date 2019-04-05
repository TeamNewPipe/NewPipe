package us.shandian.giga.io;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.provider.DocumentFile;

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
    private transient ContentResolver contentResolver;

    protected String source;
    String sourceTree;

    protected String tag;

    private String srcName;
    private String srcType;

    public StoredFileHelper(String filename, String mime, String tag) {
        this.source = null;// this instance will be "invalid" see invalidate()/isInvalid() methods

        this.srcName = filename;
        this.srcType = mime == null ? DEFAULT_MIME : mime;

        this.tag = tag;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    StoredFileHelper(DocumentFile tree, ContentResolver contentResolver, String filename, String mime, String tag) throws IOException {
        this.docTree = tree;
        this.contentResolver = contentResolver;

        // this is very slow, because SAF does not allow overwrite
        DocumentFile res = this.docTree.findFile(filename);

        if (res != null && res.exists() && res.isDirectory()) {
            if (!res.delete())
                throw new IOException("Directory with the same name found but cannot delete");
            res = null;
        }

        if (res == null) {
            res = this.docTree.createFile(mime == null ? DEFAULT_MIME : mime, filename);
            if (res == null) throw new IOException("Cannot create the file");
        }

        this.docFile = res;
        this.source = res.getUri().toString();
        this.srcName = getName();
        this.srcType = getType();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public StoredFileHelper(Context context, @NonNull Uri path, String tag) throws IOException {
        this.source = path.toString();
        this.tag = tag;

        if (path.getScheme() == null || path.getScheme().equalsIgnoreCase("file")) {
            this.ioFile = new File(URI.create(this.source));
        } else {
            DocumentFile file = DocumentFile.fromSingleUri(context, path);
            if (file == null)
                throw new UnsupportedOperationException("Cannot get the file via SAF");

            this.contentResolver = context.getContentResolver();
            this.docFile = file;

            try {
                this.contentResolver.takePersistableUriPermission(docFile.getUri(), StoredDirectoryHelper.PERMISSION_FLAGS);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        this.srcName = getName();
        this.srcType = getType();
    }

    public StoredFileHelper(File location, String filename, String tag) throws IOException {
        this.ioFile = new File(location, filename);
        this.tag = tag;

        if (this.ioFile.exists()) {
            if (!this.ioFile.isFile() && !this.ioFile.delete())
                throw new IOException("The filename is already in use by non-file entity and cannot overwrite it");
        } else {
            if (!this.ioFile.createNewFile())
                throw new IOException("Cannot create the file");
        }

        this.source = Uri.fromFile(this.ioFile).toString();
        this.srcName = getName();
        this.srcType = getType();
    }

    public static StoredFileHelper deserialize(@NonNull StoredFileHelper storage, Context context) throws IOException {
        if (storage.isInvalid())
            return new StoredFileHelper(storage.srcName, storage.srcType, storage.tag);

        StoredFileHelper instance = new StoredFileHelper(context, Uri.parse(storage.source), storage.tag);

        if (storage.sourceTree != null) {
            instance.docTree = DocumentFile.fromTreeUri(context, Uri.parse(instance.sourceTree));

            if (instance.docTree == null)
                throw new IOException("Cannot deserialize the tree, ¿revoked permissions?");
        }

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
            return new FileStreamSAF(contentResolver, docFile.getUri());
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

    public void truncate() throws IOException {
        invalid();

        try (SharpStream fs = getStream()) {
            fs.setLength(0);
        }
    }

    public boolean delete() {
        invalid();

        if (docFile == null) return ioFile.delete();

        boolean res = docFile.delete();

        try {
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            contentResolver.releasePersistableUriPermission(docFile.getUri(), flags);
        } catch (Exception ex) {
            // ¿what happen?
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

    public File getIOFile() {
        return ioFile;
    }

    public String getName() {
        if (source == null) return srcName;
        return docFile == null ? ioFile.getName() : docFile.getName();
    }

    public String getType() {
        if (source == null) return srcType;
        return docFile == null ? DEFAULT_MIME : docFile.getType();// not obligatory for Java IO
    }

    public String getTag() {
        return tag;
    }

    public boolean existsAsFile() {
        if (source == null) return false;

        boolean exists = docFile == null ? ioFile.exists() : docFile.exists();
        boolean asFile = docFile == null ? ioFile.isFile() : docFile.isFile();// ¿docFile.isVirtual() means is no-physical?

        return exists && asFile;
    }

    public boolean create() {
        invalid();

        if (docFile == null) {
            try {
                return ioFile.createNewFile();
            } catch (IOException e) {
                return false;
            }
        }

        if (docTree == null || docFile.getName() == null) return false;

        DocumentFile res = docTree.createFile(docFile.getName(), docFile.getType() == null ? DEFAULT_MIME : docFile.getType());
        if (res == null) return false;

        docFile = res;
        return true;
    }

    public void invalidate() {
        if (source == null) return;

        srcName = getName();
        srcType = getType();

        source = null;

        sourceTree = null;
        docTree = null;
        docFile = null;
        ioFile = null;
        contentResolver = null;
    }

    private void invalid() {
        if (source == null)
            throw new IllegalStateException("In invalid state");
    }

    public boolean equals(StoredFileHelper storage) {
        if (this.isInvalid() != storage.isInvalid()) return false;
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
}
