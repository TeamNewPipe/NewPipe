package org.schabi.newpipe.streams.io

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.nononsenseapps.filepicker.AbstractFilePickerActivity
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.util.FilePickerActivityHelper
import us.shandian.giga.util.Utility
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors

class StoredDirectoryHelper(context: Context, path: Uri,
                            private val tag: String?) {
    private var ioTree: Path? = null
    private val docTree: DocumentFile?
    private val context: Context

    init {
        if (ContentResolver.SCHEME_FILE.equals(path.getScheme(), ignoreCase = true)) {
            ioTree = Paths.get(URI.create(path.toString()))
            return
        }
        this.context = context
        try {
            this.context.getContentResolver().takePersistableUriPermission(path, PERMISSION_FLAGS)
        } catch (e: Exception) {
            throw IOException(e)
        }
        docTree = DocumentFile.fromTreeUri(context, path)
        if (docTree == null) {
            throw IOException("Failed to create the tree from Uri")
        }
    }

    fun createFile(filename: String?, mime: String?): StoredFileHelper? {
        return createFile(filename, mime, false)
    }

    fun createUniqueFile(name: String, mime: String?): StoredFileHelper? {
        val matches: MutableList<String> = ArrayList()
        val filename: Array<String> = splitFilename(name)
        val lcFileName: String = filename.get(0).lowercase(Locale.getDefault())
        if (docTree == null) {
            try {
                Files.list(ioTree).use({ stream ->
                    matches.addAll(stream.map(Function({ path: Path -> path.getFileName().toString().lowercase(Locale.getDefault()) }))
                            .filter(Predicate({ fileName: String -> fileName.startsWith(lcFileName) }))
                            .collect(Collectors.toList()))
                })
            } catch (e: IOException) {
                Log.e(TAG, "Exception while traversing " + ioTree, e)
            }
        } else {
            // warning: SAF file listing is very slow
            val docTreeChildren: Uri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    docTree.getUri(), DocumentsContract.getDocumentId(docTree.getUri()))
            val projection: Array<String> = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val selection: String = "(LOWER(" + DocumentsContract.Document.COLUMN_DISPLAY_NAME + ") LIKE ?%"
            val cr: ContentResolver = context.getContentResolver()
            cr.query(docTreeChildren, projection, selection, arrayOf(lcFileName), null).use({ cursor ->
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        addIfStartWith(matches, lcFileName, cursor.getString(0))
                    }
                }
            })
        }
        if (matches.isEmpty()) {
            return createFile(name, mime, true)
        }

        // check if the filename is in use
        var lcName: String? = name.lowercase(Locale.getDefault())
        for (testName: String in matches) {
            if ((testName == lcName)) {
                lcName = null
                break
            }
        }

        // create file if filename not in use
        if (lcName != null) {
            return createFile(name, mime, true)
        }
        Collections.sort(matches, java.util.Comparator({ obj: String, anotherString: String? -> obj.compareTo((anotherString)!!) }))
        for (i in 1..999) {
            if (Collections.binarySearch(matches, makeFileName(lcFileName, i, filename.get(1))) < 0) {
                return createFile(makeFileName(filename.get(0), i, filename.get(1)), mime, true)
            }
        }
        return createFile(System.currentTimeMillis().toString() + filename.get(1), mime,
                false)
    }

    private fun createFile(filename: String?, mime: String?,
                           safe: Boolean): StoredFileHelper? {
        val storage: StoredFileHelper
        try {
            if (docTree == null) {
                storage = StoredFileHelper(ioTree, filename, mime)
            } else {
                storage = StoredFileHelper(context, docTree, filename, mime, safe)
            }
        } catch (e: IOException) {
            return null
        }
        storage.tag = tag
        return storage
    }

    fun getUri(): Uri {
        return if (docTree == null) Uri.fromFile(ioTree!!.toFile()) else docTree.getUri()
    }

    fun exists(): Boolean {
        return if (docTree == null) Files.exists(ioTree) else docTree.exists()
    }

    /**
     * Indicates whether it's using the `java.io` API.
     *
     * @return `true` for Java I/O API, otherwise, `false` for Storage Access Framework
     */
    fun isDirect(): Boolean {
        return docTree == null
    }

    /**
     * Get free memory of the storage partition (root of the directory).
     * @return amount of free memory in the volume of current directory (bytes)
     */
    @RequiresApi(api = Build.VERSION_CODES.N) // Necessary for `getStorageVolume()`
    fun getFreeMemory(): Long {
        val uri: Uri = getUri()
        val storageManager: StorageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volumes: List<StorageVolume> = storageManager.getStorageVolumes()
        val docId: String = DocumentsContract.getDocumentId(uri)
        val split: Array<String> = docId.split(":".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
        if (split.size > 0) {
            val volumeId: String = split.get(0)
            for (volume: StorageVolume in volumes) {
                // if the volume is an internal system volume
                if (volume.isPrimary() && volumeId.equals("primary", ignoreCase = true)) {
                    return Utility.getSystemFreeMemory()
                }

                // if the volume is a removable volume (normally an SD card)
                if (volume.isRemovable() && !volume.isPrimary()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val sdCardUUID: String? = volume.getUuid()
                            return storageManager.getAllocatableBytes(UUID.fromString(sdCardUUID))
                        } catch (e: Exception) {
                            // do nothing
                        }
                    }
                }
            }
        }
        return Long.MAX_VALUE
    }

    /**
     * Only using Java I/O. Creates the directory named by this abstract pathname, including any
     * necessary but nonexistent parent directories.
     * Note that if this operation fails it may have succeeded in creating some of the necessary
     * parent directories.
     *
     * @return `true` if and only if the directory was created,
     * along with all necessary parent directories or already exists; `false`
     * otherwise
     */
    fun mkdirs(): Boolean {
        if (docTree == null) {
            try {
                Files.createDirectories(ioTree)
            } catch (e: IOException) {
                Log.e(TAG, "Error while creating directories at " + ioTree, e)
            }
            return Files.exists(ioTree)
        }
        if (docTree.exists()) {
            return true
        }
        try {
            var parent: DocumentFile?
            var child: String? = docTree.getName()
            while (true) {
                parent = docTree.getParentFile()
                if (parent == null || child == null) {
                    break
                }
                if (parent.exists()) {
                    return true
                }
                parent.createDirectory(child)
                child = parent.getName() // for the next iteration
            }
        } catch (ignored: Exception) {
            // no more parent directories or unsupported by the storage provider
        }
        return false
    }

    fun getTag(): String? {
        return tag
    }

    fun findFile(filename: String?): Uri? {
        if (docTree == null) {
            val res: Path = ioTree!!.resolve(filename)
            return if (Files.exists(res)) Uri.fromFile(res.toFile()) else null
        }
        val res: DocumentFile? = findFileSAFHelper(context, docTree, filename)
        return if (res == null) null else res.getUri()
    }

    fun canWrite(): Boolean {
        return if (docTree == null) Files.isWritable(ioTree) else docTree.canWrite()
    }

    /**
     * @return `false` if the storage is direct, or the SAF storage is valid; `true` if
     * SAF access to this SAF storage is denied (e.g. the user clicked on `Android settings ->
     * Apps & notifications -> NewPipe -> Storage & cache -> Clear access`);
     */
    fun isInvalidSafStorage(): Boolean {
        return docTree != null && docTree.getName() == null
    }

    public override fun toString(): String {
        return (if (docTree == null) Uri.fromFile(ioTree!!.toFile()) else docTree.getUri()).toString()
    }

    companion object {
        private val TAG: String = StoredDirectoryHelper::class.java.getSimpleName()
        val PERMISSION_FLAGS: Int = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        ////////////////////
        //      Utils
        ///////////////////
        private fun addIfStartWith(list: MutableList<String>, base: String,
                                   str: String) {
            if (Utils.isNullOrEmpty(str)) {
                return
            }
            val lowerStr: String = str.lowercase(Locale.getDefault())
            if (lowerStr.startsWith(base)) {
                list.add(lowerStr)
            }
        }

        /**
         * Splits the filename into the name and extension.
         *
         * @param filename The filename to split
         * @return A String array with the name at index 0 and extension at index 1
         */
        private fun splitFilename(filename: String): Array<String> {
            val dotIndex: Int = filename.lastIndexOf('.')
            if (dotIndex < 0 || (dotIndex == filename.length - 1)) {
                return arrayOf(filename, "")
            }
            return arrayOf(filename.substring(0, dotIndex), filename.substring(dotIndex))
        }

        private fun makeFileName(name: String, idx: Int, ext: String): String {
            return name + "(" + idx + ")" + ext
        }

        /**
         * Fast (but not enough) file/directory finder under the storage access framework.
         *
         * @param context  The context
         * @param tree     Directory where search
         * @param filename Target filename
         * @return A [DocumentFile] contain the reference, otherwise, null
         */
        fun findFileSAFHelper(context: Context?, tree: DocumentFile?,
                              filename: String?): DocumentFile? {
            if (context == null) {
                return tree!!.findFile((filename)!!) // warning: this is very slow
            }
            if (!tree!!.canRead()) {
                return null // missing read permission
            }
            val name: Int = 0
            val documentId: Int = 1

            // LOWER() SQL function is not supported
            val selection: String = DocumentsContract.Document.COLUMN_DISPLAY_NAME + " = ?"
            //final String selection = COLUMN_DISPLAY_NAME + " LIKE ?%";
            val childrenUri: Uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree.getUri(),
                    DocumentsContract.getDocumentId(tree.getUri()))
            val projection: Array<String> = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Root.COLUMN_DOCUMENT_ID)
            val contentResolver: ContentResolver = context.getContentResolver()
            val lowerFilename: String = filename!!.lowercase(Locale.getDefault())
            contentResolver.query(childrenUri, projection, selection, arrayOf<String>(lowerFilename), null).use({ cursor ->
                if (cursor == null) {
                    return null
                }
                while (cursor.moveToNext()) {
                    if ((cursor.isNull(name)
                                    || !cursor.getString(name).lowercase(Locale.getDefault()).startsWith(lowerFilename))) {
                        continue
                    }
                    return DocumentFile.fromSingleUri(context,
                            DocumentsContract.buildDocumentUriUsingTree(tree.getUri(),
                                    cursor.getString(documentId)))
                }
            })
            return null
        }

        fun getPicker(ctx: Context?): Intent {
            if (NewPipeSettings.useStorageAccessFramework(ctx)) {
                return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        .putExtra("android.content.extra.SHOW_ADVANCED", true)
                        .addFlags((Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                or PERMISSION_FLAGS))
            } else {
                return Intent(ctx, FilePickerActivityHelper::class.java)
                        .putExtra(AbstractFilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
                        .putExtra(AbstractFilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
                        .putExtra(AbstractFilePickerActivity.EXTRA_MODE,
                                AbstractFilePickerActivity.MODE_DIR)
            }
        }
    }
}
