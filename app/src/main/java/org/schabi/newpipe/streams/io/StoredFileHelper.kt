package org.schabi.newpipe.streams.io

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.documentfile.provider.DocumentFile
import org.schabi.newpipe.DebugConstants
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.util.FilePickerActivityHelper
import org.schabi.newpipe.util.Utils
import us.shandian.giga.io.FileStream
import us.shandian.giga.io.FileStreamSAF
import java.io.File
import java.io.Serializable
import okio.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class StoredFileHelper : Serializable {

    @Transient
    private var docFile: DocumentFile? = null

    @Transient
    private var docTree: DocumentFile? = null

    @Transient
    private var ioPath: Path? = null

    @Transient
    private var context: Context? = null

    var source: String? = null
        protected set
    private var sourceTree: String? = null

    var tag: String? = null

    private var srcName: String? = null
    private var srcType: String? = null

    constructor(context: Context, uri: Uri, mime: String?) {
        if (FilePickerActivityHelper.isOwnFileUri(context, uri)) {
            val ioFile = Utils.getFileForUri(uri)
            ioPath = ioFile.toPath()
            source = Uri.fromFile(ioFile).toString()
        } else {
            docFile = DocumentFile.fromSingleUri(context, uri)
            source = uri.toString()
        }

        this.context = context
        this.srcType = mime
    }

    constructor(parent: Uri?, filename: String?, mime: String?, tag: String?) {
        this.source = null // this instance will be "invalid" see invalidate()/isInvalid() methods

        this.srcName = filename
        this.srcType = mime ?: DEFAULT_MIME
        if (parent != null) {
            this.sourceTree = parent.toString()
        }

        this.tag = tag
    }

    @Throws(IOException::class)
    internal constructor(context: Context?, tree: DocumentFile, filename: String, mime: String?, safe: Boolean) {
        this.docTree = tree
        this.context = context

        val res: DocumentFile? = if (safe) {
            // no conflicts (the filename is not in use)
            (docTree ?: throw IOException("docTree is null")).createFile(mime ?: "application/octet-stream", filename)
        } else {
            createSAF(context, mime, filename)
        }

        this.docFile = res ?: throw IOException("Cannot create the file")

        this.source = (docFile ?: throw IOException("docFile is null")).uri.toString()
        this.sourceTree = (docTree ?: throw IOException("docTree is null")).uri.toString()

        this.srcName = (docFile ?: throw IOException("docFile is null")).name
        this.srcType = (docFile ?: throw IOException("docFile is null")).type
    }

    @Throws(IOException::class)
    internal constructor(location: Path, filename: String, mime: String?) {
        ioPath = location.resolve(filename)

        Files.deleteIfExists(ioPath)
        Files.createFile(ioPath)

        source = Uri.fromFile((ioPath ?: throw IOException("ioPath is null")).toFile()).toString()
        sourceTree = Uri.fromFile(location.toFile()).toString()

        srcName = (ioPath ?: throw IOException("ioPath is null")).fileName.toString()
        srcType = mime
    }

    @Throws(IOException::class)
    constructor(context: Context, parent: Uri?, path: Uri, tag: String?) {
        this.tag = tag
        this.source = path.toString()

        if (path.scheme == null || (path.scheme ?: throw IOException("scheme is null")).equals(ContentResolver.SCHEME_FILE, ignoreCase = true)) {
            this.ioPath = Paths.get(URI.create(this.source))
        } else {
            val file = DocumentFile.fromSingleUri(context, path) ?: throw IOException("SAF not available")

            this.context = context

            if (file.name == null) {
                this.source = null
                return
            } else {
                this.docFile = file
                takePermissionSAF()
            }
        }

        if (parent != null) {
            if (ContentResolver.SCHEME_FILE != parent.scheme) {
                this.docTree = DocumentFile.fromTreeUri(context, parent)
            }

            this.sourceTree = parent.toString()
        }

        this.srcName = getName()
        this.srcType = getType()
    }

    @Throws(IOException::class)
    fun getStream(): SharpStream {
        assertValid()

        return if (docFile == null) {
            FileStream((ioPath ?: throw IOException("ioPath is null")).toFile())
        } else {
            FileStreamSAF((context ?: throw IOException("context is null")).contentResolver, (docFile ?: throw IOException("docFile is null")).uri)
        }
    }

    @Throws(IOException::class)
    fun openAndTruncateStream(): SharpStream {
        val sharpStream = getStream()
        try {
            sharpStream.setLength(0)
        } catch (e: Throwable) {
            // we can't use try-with-resources here, since we only want to close the stream if an
            // exception occurs, but leave it open if everything goes well
            sharpStream.close()
            throw e
        }
        return sharpStream
    }

    /**
     * Indicates whether it's using the `java.io` API.
     *
     * @return `true` for Java I/O API, otherwise, `false` for Storage Access Framework
     */
    fun isDirect(): Boolean {
        assertValid()
        return docFile == null
    }

    fun isInvalid(): Boolean {
        return source == null
    }

    fun getUri(): Uri {
        assertValid()
        return if (docFile == null) Uri.fromFile((ioPath ?: throw IOException("ioPath is null")).toFile()) else (docFile ?: throw IOException("docFile is null")).uri
    }

    fun getParentUri(): Uri? {
        assertValid()
        return if (sourceTree == null) null else Uri.parse(sourceTree)
    }

    @Throws(IOException::class)
    fun truncate() {
        assertValid()
        getStream().use { fs ->
            fs.setLength(0)
        }
    }

    fun delete(): Boolean {
        if (source == null) {
            return true
        }
        if (docFile == null) {
            return try {
                Files.deleteIfExists(ioPath)
            } catch (e: IOException) {
                Log.e(TAG, "Exception while deleting $ioPath", e)
                false
            }
        }

        val res = (docFile ?: throw IOException("docFile is null")).delete()

        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            (context ?: throw IOException("context is null")).contentResolver.releasePersistableUriPermission((docFile ?: throw IOException("docFile is null")).uri, flags)
        } catch (ex: Exception) {
            // nothing to do
        }

        return res
    }

    fun length(): Long {
        assertValid()

        return if (docFile == null) {
            try {
                Files.size(ioPath)
            } catch (e: IOException) {
                Log.e(TAG, "Exception while getting the size of $ioPath", e)
                0
            }
        } else {
            (docFile ?: throw IOException("docFile is null")).length()
        }
    }

    fun canWrite(): Boolean {
        if (source == null) {
            return false
        }
        return if (docFile == null) Files.isWritable(ioPath) else (docFile ?: throw IOException("docFile is null")).canWrite()
    }

    fun getName(): String? {
        if (source == null) {
            return srcName
        } else if (docFile == null) {
            return (ioPath ?: throw IOException("ioPath is null")).fileName.toString()
        }

        val name = (docFile ?: throw IOException("docFile is null")).name
        return name ?: srcName
    }

    fun getType(): String? {
        if (source == null || docFile == null) {
            return srcType
        }

        val type = (docFile ?: throw IOException("docFile is null")).getType()
        return type ?: srcType
    }

    fun existsAsFile(): Boolean {
        if (source == null || (docFile == null && ioPath == null)) {
            if (false) {
                Log.d(TAG, "existsAsFile called but something is null: source = ["
                        + (if (source == null) "null => storage is invalid" else source)
                        + "], docFile = [" + docFile + "], ioPath = [" + ioPath + "]")
            }
            return false
        }

        // WARNING: DocumentFile.exists() and DocumentFile.isFile() methods are slow
        return if (docFile == null) Files.isRegularFile(ioPath) else (docFile ?: throw IOException("docFile is null")).exists() && (docFile ?: throw IOException("docFile is null")).isFile
    }

    fun create(): Boolean {
        assertValid()
        val result: Boolean

        if (docFile == null) {
            result = try {
                Files.createFile(ioPath)
                true
            } catch (e: IOException) {
                Log.e(TAG, "Exception while creating $ioPath", e)
                false
            }
        } else if (docTree == null) {
            result = false
        } else {
            if (!(docTree ?: throw IOException("docTree is null")).canRead() || !(docTree ?: throw IOException("docTree is null")).canWrite()) {
                return false
            }
            result = try {
                docFile = createSAF(context, srcType, srcName)
                (docFile ?: throw IOException("docFile is null")).name != null
            } catch (e: IOException) {
                false
            }
        }

        if (result) {
            source = (if (docFile == null) Uri.fromFile((ioPath ?: throw IOException("ioPath is null")).toFile()) else (docFile ?: throw IOException("docFile is null")).uri).toString()
            srcName = getName()
            srcType = getType()
        }

        return result
    }

    fun invalidate() {
        if (source == null) {
            return
        }

        srcName = getName()
        srcType = getType()

        source = null

        docTree = null
        docFile = null
        ioPath = null
        context = null
    }

    fun equals(storage: StoredFileHelper?): Boolean {
        if (this === storage) {
            return true
        }
        if (storage == null) return false

        if (stringMismatch(getLowerCase(this.sourceTree), getLowerCase(storage.sourceTree))) {
            return false
        }

        if (this.isInvalid() || storage.isInvalid()) {
            if (this.srcName == null || storage.srcName == null || this.srcType == null || storage.srcType == null) {
                return false
            }

            return (srcName ?: throw IOException("srcName is null")).equals(storage.srcName, ignoreCase = true) &&
                    (srcType ?: throw IOException("srcType is null")).equals(storage.srcType, ignoreCase = true)
        }

        if (this.isDirect() != storage.isDirect()) {
            return false
        }

        if (this.isDirect()) {
            return this.ioPath == storage.ioPath
        }

        return DocumentsContract.getDocumentId((docFile ?: throw IOException("docFile is null")).uri)
            .equals(DocumentsContract.getDocumentId((storage.docFile ?: throw IOException("docFile is null")).uri), ignoreCase = true)
    }

    override fun toString(): String {
        return if (source == null) {
            "[Invalid state] name=$srcName  type=$srcType  tag=$tag"
        } else {
            "sourceFile=$source  treeSource=${sourceTree ?: ""}  tag=$tag"
        }
    }

    private fun assertValid() {
        if (source == null) {
            throw IllegalStateException("In invalid state")
        }
    }

    @Throws(IOException::class)
    private fun takePermissionSAF() {
        try {
            (context ?: throw IOException("context is null")).contentResolver.takePersistableUriPermission((docFile ?: throw IOException("docFile is null")).uri, StoredDirectoryHelper.PERMISSION_FLAGS)
        } catch (e: Exception) {
            if ((docFile ?: throw IOException("docFile is null")).name == null) {
                throw IOException(e)
            }
        }
    }

    @Throws(IOException::class)
    private fun createSAF(ctx: Context?, mime: String?, filename: String?): DocumentFile {
        var res = StoredDirectoryHelper.findFileSAFHelper(ctx, (docTree ?: throw IOException("docTree is null")), (filename ?: throw IOException("filename is null")))

        if (res != null && res.exists() && res.isDirectory) {
            if (!res.delete()) {
                throw IOException("Directory with the same name found but cannot delete")
            }
            res = null
        }

        if (res == null) {
            res = (docTree ?: throw IOException("docTree is null")).createFile(srcType ?: DEFAULT_MIME, filename)
                ?: throw IOException("Cannot create the file")
        }

        return res
    }

    private fun getLowerCase(str: String?): String? {
        return str?.lowercase()
    }

    private fun stringMismatch(str1: String?, str2: String?): Boolean {
        if (str1 == null && str2 == null) {
            return false
        }
        return if (str1 == null || str2 == null) {
            true
        } else str1 != str2
    }

    companion object {
        private val DEBUG = DebugConstants.DEBUG
        private val TAG = StoredFileHelper::class.java.simpleName

        private const val serialVersionUID = 0L
        const val DEFAULT_MIME = "application/octet-stream"

        @JvmStatic
        @Throws(IOException::class)
        fun deserialize(storage: StoredFileHelper, context: Context): StoredFileHelper {
            val treeUri = if (storage.sourceTree == null) null else Uri.parse(storage.sourceTree)

            if (storage.isInvalid()) {
                return StoredFileHelper(treeUri, storage.srcName, storage.srcType, storage.tag)
            }

            val instance = StoredFileHelper(context, treeUri, Uri.parse(storage.source), storage.tag)

            // under SAF, if the target document is deleted, conserve the filename and mime
            if (instance.srcName == null) {
                instance.srcName = storage.srcName
            }
            if (instance.srcType == null) {
                instance.srcType = storage.srcType
            }

            return instance
        }

        @JvmStatic
        fun getPicker(ctx: Context, mimeType: String): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT)
                .putExtra("android.content.extra.SHOW_ADVANCED", true)
                .setType(mimeType)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or StoredDirectoryHelper.PERMISSION_FLAGS)
        }

        @JvmStatic
        fun getPicker(ctx: Context, mimeType: String, initialPath: Uri?): Intent {
            return applyInitialPathToPickerIntent(ctx, getPicker(ctx, mimeType), initialPath, null)
        }

        @JvmStatic
        fun getNewPicker(ctx: Context, filename: String?, mimeType: String, initialPath: Uri?): Intent {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .putExtra("android.content.extra.SHOW_ADVANCED", true)
                .setType(mimeType)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or StoredDirectoryHelper.PERMISSION_FLAGS)
            if (filename != null) {
                i.putExtra(Intent.EXTRA_TITLE, filename)
            }
            return applyInitialPathToPickerIntent(ctx, i, initialPath, filename)
        }

        private fun applyInitialPathToPickerIntent(
            ctx: Context,
            intent: Intent,
            initialPath: Uri?,
            filename: String?
        ): Intent {
            if (NewPipeSettings.useStorageAccessFramework(ctx)) {
                if (initialPath == null) {
                    return intent
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    return intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialPath)
                } else {
                    return intent
                }
            } else {
                if (initialPath == null && filename == null) {
                    return intent
                }

                var file: File
                if (initialPath == null) {
                    file = Environment.getExternalStorageDirectory()
                } else {
                    try {
                        file = Utils.getFileForUri(initialPath)
                    } catch (ignored: Throwable) {
                        file = File(initialPath.toString())
                    }
                }

                if (!file.exists() || !file.isDirectory) {
                    file = file.parentFile ?: Environment.getExternalStorageDirectory()
                    if (!file.exists()) {
                        file = Environment.getExternalStorageDirectory()
                    }
                }

                if (filename != null) {
                    file = File(file, filename)
                }

                // return intent.putExtra(AbstractFilePickerActivity.EXTRA_START_PATH, file.absolutePath)
                return intent
            }
        }
    }
}
