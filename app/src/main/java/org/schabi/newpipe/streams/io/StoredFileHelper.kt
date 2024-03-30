package org.schabi.newpipe.streams.io

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.nononsenseapps.filepicker.AbstractFilePickerActivity
import com.nononsenseapps.filepicker.Utils
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.FilePickerActivityHelper
import us.shandian.giga.io.FileStream
import us.shandian.giga.io.FileStreamSAF
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale

class StoredFileHelper : Serializable {
    @Transient
    private var docFile: DocumentFile? = null

    @Transient
    private var docTree: DocumentFile? = null

    @Transient
    private var ioPath: Path? = null

    @Transient
    private var context: Context? = null
    protected var source: String? = null
    private var sourceTree: String? = null
    var tag: String? = null
    private var srcName: String? = null
    private var srcType: String?

    constructor(context: Context, uri: Uri?, mime: String?) {
        if (FilePickerActivityHelper.Companion.isOwnFileUri(context, (uri)!!)) {
            val ioFile: File = Utils.getFileForUri((uri))
            ioPath = ioFile.toPath()
            source = Uri.fromFile(ioFile).toString()
        } else {
            docFile = DocumentFile.fromSingleUri(context, (uri))
            source = uri.toString()
        }
        this.context = context
        srcType = mime
    }

    constructor(parent: Uri?, filename: String?, mime: String?,
                tag: String?) {
        source = null // this instance will be "invalid" see invalidate()/isInvalid() methods
        srcName = filename
        srcType = if (mime == null) DEFAULT_MIME else mime
        if (parent != null) {
            sourceTree = parent.toString()
        }
        this.tag = tag
    }

    internal constructor(context: Context?, tree: DocumentFile?,
                         filename: String?, mime: String?, safe: Boolean) {
        docTree = tree
        this.context = context
        val res: DocumentFile?
        if (safe) {
            // no conflicts (the filename is not in use)
            res = docTree!!.createFile((mime)!!, (filename)!!)
            if (res == null) {
                throw IOException("Cannot create the file")
            }
        } else {
            res = createSAF(context, mime, filename)
        }
        docFile = res
        source = docFile!!.getUri().toString()
        sourceTree = docTree!!.getUri().toString()
        srcName = docFile!!.getName()
        srcType = docFile!!.getType()
    }

    internal constructor(location: Path?, filename: String?, mime: String?) {
        ioPath = location!!.resolve(filename)
        Files.deleteIfExists(ioPath)
        Files.createFile(ioPath)
        source = Uri.fromFile(ioPath.toFile()).toString()
        sourceTree = Uri.fromFile(location.toFile()).toString()
        srcName = ioPath.getFileName().toString()
        srcType = mime
    }

    constructor(context: Context?, parent: Uri?,
                path: Uri, tag: String?) {
        this.tag = tag
        source = path.toString()
        if ((path.getScheme() == null
                        || path.getScheme().equals(ContentResolver.SCHEME_FILE, ignoreCase = true))) {
            ioPath = Paths.get(URI.create(source))
        } else {
            val file: DocumentFile? = DocumentFile.fromSingleUri((context)!!, path)
            if (file == null) {
                throw IOException("SAF not available")
            }
            this.context = context
            if (file.getName() == null) {
                source = null
                return
            } else {
                docFile = file
                takePermissionSAF()
            }
        }
        if (parent != null) {
            if (!(ContentResolver.SCHEME_FILE == parent.getScheme())) {
                docTree = DocumentFile.fromTreeUri((context)!!, parent)
            }
            sourceTree = parent.toString()
        }
        srcName = getName()
        srcType = getType()
    }

    @Throws(IOException::class)
    fun getStream(): SharpStream {
        assertValid()
        if (docFile == null) {
            return FileStream(ioPath!!.toFile())
        } else {
            return FileStreamSAF(context!!.getContentResolver(), docFile!!.getUri())
        }
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
        return if (docFile == null) Uri.fromFile(ioPath!!.toFile()) else docFile!!.getUri()
    }

    fun getParentUri(): Uri? {
        assertValid()
        return if (sourceTree == null) null else Uri.parse(sourceTree)
    }

    @Throws(IOException::class)
    fun truncate() {
        assertValid()
        getStream().use({ fs -> fs.setLength(0) })
    }

    fun delete(): Boolean {
        if (source == null) {
            return true
        }
        if (docFile == null) {
            try {
                return Files.deleteIfExists(ioPath)
            } catch (e: IOException) {
                Log.e(TAG, "Exception while deleting " + ioPath, e)
                return false
            }
        }
        val res: Boolean = docFile!!.delete()
        try {
            val flags: Int = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            context!!.getContentResolver().releasePersistableUriPermission(docFile!!.getUri(), flags)
        } catch (ex: Exception) {
            // nothing to do
        }
        return res
    }

    fun length(): Long {
        assertValid()
        if (docFile == null) {
            try {
                return Files.size(ioPath)
            } catch (e: IOException) {
                Log.e(TAG, "Exception while getting the size of " + ioPath, e)
                return 0
            }
        } else {
            return docFile!!.length()
        }
    }

    fun canWrite(): Boolean {
        if (source == null) {
            return false
        }
        return if (docFile == null) Files.isWritable(ioPath) else docFile!!.canWrite()
    }

    fun getName(): String? {
        if (source == null) {
            return srcName
        } else if (docFile == null) {
            return ioPath!!.getFileName().toString()
        }
        val name: String? = docFile!!.getName()
        return if (name == null) srcName else name
    }

    fun getType(): String? {
        if (source == null || docFile == null) {
            return srcType
        }
        val type: String? = docFile!!.getType()
        return if (type == null) srcType else type
    }

    fun getTag(): String? {
        return tag
    }

    fun existsAsFile(): Boolean {
        if (source == null || (docFile == null && ioPath == null)) {
            if (DEBUG) {
                Log.d(TAG, ("existsAsFile called but something is null: source = ["
                        + (if (source == null) "null => storage is invalid" else source)
                        + "], docFile = [" + docFile + "], ioPath = [" + ioPath + "]"))
            }
            return false
        }

        // WARNING: DocumentFile.exists() and DocumentFile.isFile() methods are slow
        // docFile.isVirtual() means it is non-physical?
        return if (docFile == null) Files.isRegularFile(ioPath) else (docFile!!.exists() && docFile!!.isFile())
    }

    fun create(): Boolean {
        assertValid()
        val result: Boolean
        if (docFile == null) {
            try {
                Files.createFile(ioPath)
                result = true
            } catch (e: IOException) {
                Log.e(TAG, "Exception while creating " + ioPath, e)
                return false
            }
        } else if (docTree == null) {
            result = false
        } else {
            if (!docTree!!.canRead() || !docTree!!.canWrite()) {
                return false
            }
            try {
                docFile = createSAF(context, srcType, srcName)
                if (docFile!!.getName() == null) {
                    return false
                }
                result = true
            } catch (e: IOException) {
                return false
            }
        }
        if (result) {
            source = (if (docFile == null) Uri.fromFile(ioPath!!.toFile()) else docFile!!.getUri())
                    .toString()
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

    fun equals(storage: StoredFileHelper): Boolean {
        if (this === storage) {
            return true
        }

        // note: do not compare tags, files can have the same parent folder
        //if (stringMismatch(this.tag, storage.tag)) return false;
        if (stringMismatch(getLowerCase(sourceTree), getLowerCase(sourceTree))) {
            return false
        }
        if (isInvalid() || storage.isInvalid()) {
            if ((srcName == null) || (storage.srcName == null) || (srcType == null
                            ) || (storage.srcType == null)) {
                return false
            }
            return (srcName.equals(storage.srcName, ignoreCase = true)
                    && srcType.equals(storage.srcType, ignoreCase = true))
        }
        if (isDirect() != storage.isDirect()) {
            return false
        }
        if (isDirect()) {
            return (ioPath == storage.ioPath)
        }
        return DocumentsContract.getDocumentId(docFile!!.getUri())
                .equals(DocumentsContract.getDocumentId(storage.docFile!!.getUri()), ignoreCase = true)
    }

    public override fun toString(): String {
        if (source == null) {
            return "[Invalid state] name=" + srcName + "  type=" + srcType + "  tag=" + tag
        } else {
            return ("sourceFile=" + source + "  treeSource=" + (if (sourceTree == null) "" else sourceTree)
                    + "  tag=" + tag)
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
            context!!.getContentResolver().takePersistableUriPermission(docFile!!.getUri(),
                    StoredDirectoryHelper.Companion.PERMISSION_FLAGS)
        } catch (e: Exception) {
            if (docFile!!.getName() == null) {
                throw IOException(e)
            }
        }
    }

    @Throws(IOException::class)
    private fun createSAF(ctx: Context?, mime: String?,
                          filename: String?): DocumentFile {
        var res: DocumentFile = (StoredDirectoryHelper.Companion.findFileSAFHelper(ctx, docTree, filename))!!
        if ((res != null) && res.exists() && res.isDirectory()) {
            if (!res.delete()) {
                throw IOException("Directory with the same name found but cannot delete")
            }
            res = null
        }
        if (res == null) {
            res = (docTree!!.createFile((if (srcType == null) DEFAULT_MIME else mime)!!, (filename)!!))!!
            if (res == null) {
                throw IOException("Cannot create the file")
            }
        }
        return res
    }

    private fun getLowerCase(str: String?): String? {
        return if (str == null) null else str.lowercase(Locale.getDefault())
    }

    private fun stringMismatch(str1: String?, str2: String?): Boolean {
        if (str1 == null && str2 == null) {
            return false
        }
        if ((str1 == null) != (str2 == null)) {
            return true
        }
        return !(str1 == str2)
    }

    companion object {
        private val DEBUG: Boolean = MainActivity.Companion.DEBUG
        private val TAG: String = StoredFileHelper::class.java.getSimpleName()
        private val serialVersionUID: Long = 0L
        val DEFAULT_MIME: String = "application/octet-stream"
        @Throws(IOException::class)
        fun deserialize(storage: StoredFileHelper,
                        context: Context?): StoredFileHelper {
            val treeUri: Uri? = if (storage.sourceTree == null) null else Uri.parse(storage.sourceTree)
            if (storage.isInvalid()) {
                return StoredFileHelper(treeUri, storage.srcName, storage.srcType, storage.tag)
            }
            val instance: StoredFileHelper = StoredFileHelper(context, treeUri,
                    Uri.parse(storage.source), storage.tag)

            // under SAF, if the target document is deleted, conserve the filename and mime
            if (instance.srcName == null) {
                instance.srcName = storage.srcName
            }
            if (instance.srcType == null) {
                instance.srcType = storage.srcType
            }
            return instance
        }

        fun getPicker(ctx: Context,
                      mimeType: String): Intent {
            if (NewPipeSettings.useStorageAccessFramework(ctx)) {
                return Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .putExtra("android.content.extra.SHOW_ADVANCED", true)
                        .setType(mimeType)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .addFlags((Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                or StoredDirectoryHelper.Companion.PERMISSION_FLAGS))
            } else {
                return Intent(ctx, FilePickerActivityHelper::class.java)
                        .putExtra(AbstractFilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
                        .putExtra(AbstractFilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
                        .putExtra(AbstractFilePickerActivity.EXTRA_SINGLE_CLICK, true)
                        .putExtra(AbstractFilePickerActivity.EXTRA_MODE,
                                AbstractFilePickerActivity.MODE_FILE)
            }
        }

        fun getPicker(ctx: Context,
                      mimeType: String,
                      initialPath: Uri?): Intent {
            return applyInitialPathToPickerIntent(ctx, getPicker(ctx, mimeType), initialPath, null)
        }

        fun getNewPicker(ctx: Context,
                         filename: String?,
                         mimeType: String,
                         initialPath: Uri?): Intent {
            val i: Intent
            if (NewPipeSettings.useStorageAccessFramework(ctx)) {
                i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .putExtra("android.content.extra.SHOW_ADVANCED", true)
                        .setType(mimeType)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .addFlags((Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                or StoredDirectoryHelper.Companion.PERMISSION_FLAGS))
                if (filename != null) {
                    i.putExtra(Intent.EXTRA_TITLE, filename)
                }
            } else {
                i = Intent(ctx, FilePickerActivityHelper::class.java)
                        .putExtra(AbstractFilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
                        .putExtra(AbstractFilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
                        .putExtra(AbstractFilePickerActivity.EXTRA_ALLOW_EXISTING_FILE, true)
                        .putExtra(AbstractFilePickerActivity.EXTRA_MODE,
                                AbstractFilePickerActivity.MODE_NEW_FILE)
            }
            return applyInitialPathToPickerIntent(ctx, i, initialPath, filename)
        }

        private fun applyInitialPathToPickerIntent(ctx: Context,
                                                   intent: Intent,
                                                   initialPath: Uri?,
                                                   filename: String?): Intent {
            if (NewPipeSettings.useStorageAccessFramework(ctx)) {
                if (initialPath == null) {
                    return intent // nothing to do, no initial path provided
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    return intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialPath)
                } else {
                    return intent // can't set initial path on API < 26
                }
            } else {
                if (initialPath == null && filename == null) {
                    return intent // nothing to do, no initial path and no file name provided
                }
                var file: File?
                if (initialPath == null) {
                    // The only way to set the previewed filename in non-SAF FilePicker is to set a
                    // starting path ending with that filename. So when the initialPath is null but
                    // filename isn't just default to the external storage directory.
                    file = Environment.getExternalStorageDirectory()
                } else {
                    try {
                        file = Utils.getFileForUri(initialPath)
                    } catch (ignored: Throwable) {
                        // getFileForUri() can't decode paths to 'storage', fallback to this
                        file = File(initialPath.toString())
                    }
                }

                // remove any filename at the end of the path (get the parent directory in that case)
                if (!file!!.exists() || !file.isDirectory()) {
                    file = file.getParentFile()
                    if (file == null || !file.exists()) {
                        // default to the external storage directory in case of an invalid path
                        file = Environment.getExternalStorageDirectory()
                    }
                    // else: file is surely a directory
                }
                if (filename != null) {
                    // append a filename so that the non-SAF FilePicker shows it as the preview
                    file = File(file, filename)
                }
                return intent
                        .putExtra(AbstractFilePickerActivity.EXTRA_START_PATH, file!!.getAbsolutePath())
            }
        }
    }
}
