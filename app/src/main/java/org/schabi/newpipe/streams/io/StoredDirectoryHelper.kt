package org.schabi.newpipe.streams.io

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
import android.provider.DocumentsContract.Root.COLUMN_DOCUMENT_ID
import android.system.Os
import android.system.StructStatVfs
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.documentfile.provider.DocumentFile
import org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty
import org.schabi.newpipe.settings.NewPipeSettings
import java.io.FileDescriptor
import okio.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections
import kotlin.streams.toList

class StoredDirectoryHelper(context: Context, path: Uri, val tag: String) {
    private var ioTree: Path? = null
    private var docTree: DocumentFile? = null

    /**
     * Context is `null` for non-SAF files, i.e. files that use `ioTree`.
     */
    private var context: Context? = null

    init {
        if (ContentResolver.SCHEME_FILE.equals(path.scheme, ignoreCase = true)) {
            ioTree = Paths.get(URI.create(path.toString()))
        } else {
            this.context = context
            try {
                (this.context ?: throw IOException("context is null")).contentResolver.takePersistableUriPermission(path, PERMISSION_FLAGS)
            } catch (e: Exception) {
                throw IOException(e)
            }
            this.docTree = DocumentFile.fromTreeUri(context, path)
                ?: throw IOException("Failed to create the tree from Uri")
        }
    }

    fun createFile(filename: String, mime: String?): StoredFileHelper? {
        return createFile(filename, mime, false)
    }

    fun createUniqueFile(name: String, mime: String?): StoredFileHelper? {
        val matches = mutableListOf<String>()
        val filename = splitFilename(name)
        val lcFileName = filename[0].lowercase()

        val currentDocTree = docTree
        if (currentDocTree == null) {
            try {
                Files.list(ioTree).use { stream ->
                    matches.addAll(
                        stream.map { it.fileName.toString().lowercase() }
                            .filter { it.startsWith(lcFileName) }
                            .toList()
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Exception while traversing $ioTree", e)
            }
        } else {
            // warning: SAF file listing is very slow
            val docTreeChildren = DocumentsContract.buildChildDocumentsUriUsingTree(
                currentDocTree.uri, DocumentsContract.getDocumentId(currentDocTree.uri)
            )

            val projection = arrayOf(COLUMN_DISPLAY_NAME)
            val selection = "(LOWER($COLUMN_DISPLAY_NAME) LIKE ?%"
            val cr = (context ?: throw IOException("context is null")).contentResolver

            cr.query(docTreeChildren, projection, selection, arrayOf(lcFileName), null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    addIfStartWith(matches, lcFileName, cursor.getString(0))
                }
            }
        }

        if (matches.isEmpty()) {
            return createFile(name, mime, true)
        }

        // check if the filename is in use
        var lcName: String? = name.lowercase()
        for (testName in matches) {
            if (testName == lcName) {
                lcName = null
                break
            }
        }

        // create file if filename not in use
        if (lcName != null) {
            return createFile(name, mime, true)
        }

        matches.sort()

        for (i in 1..999) {
            val fileNameToTest = makeFileName(lcFileName, i, filename[1])
            if (Collections.binarySearch(matches, fileNameToTest) < 0) {
                return createFile(makeFileName(filename[0], i, filename[1]), mime, true)
            }
        }

        return createFile(System.currentTimeMillis().toString() + filename[1], mime, false)
    }

    private fun createFile(filename: String, mime: String?, safe: Boolean): StoredFileHelper? {
        return try {
            val currentDocTree = docTree
            if (currentDocTree == null) {
                StoredFileHelper((ioTree ?: throw IOException("ioTree is null")), filename, mime).also { it.tag = tag }
            } else {
                StoredFileHelper(context, currentDocTree, filename, mime, safe).also { it.tag = tag }
            }
        } catch (e: IOException) {
            null
        }
    }

    fun getUri(): Uri {
        return if (docTree == null) Uri.fromFile((ioTree ?: throw IOException("ioTree is null")).toFile()) else (docTree ?: throw IOException("docTree is null")).uri
    }

    fun exists(): Boolean {
        return if (docTree == null) Files.exists(ioTree) else (docTree ?: throw IOException("docTree is null")).exists()
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
     * Get free memory of the storage partition this file belongs to (root of the directory).
     *
     * @return amount of free memory in the volume of current directory (bytes), or [Long.MAX_VALUE]
     * if an error occurred
     */
    fun getFreeStorageSpace(): Long {
        return try {
            val stat: StructStatVfs
            val currentIoTree = ioTree
            if (currentIoTree != null) {
                stat = Os.statvfs(currentIoTree.toString())
            } else {
                (context ?: throw IOException("context is null")).contentResolver.openFileDescriptor(getUri(), "r")?.use { pfd ->
                    stat = Os.fstatvfs(pfd.fileDescriptor)
                } ?: return Long.MAX_VALUE
            }
            stat.f_bavail * stat.f_frsize
        } catch (e: Throwable) {
            Log.e(TAG, "Could not get free storage space", e)
            Long.MAX_VALUE
        }
    }

    fun mkdirs(): Boolean {
        val currentDocTree = docTree
        if (currentDocTree == null) {
            try {
                Files.createDirectories(ioTree)
            } catch (e: IOException) {
                Log.e(TAG, "Error while creating directories at $ioTree", e)
            }
            return Files.exists(ioTree)
        }

        if (currentDocTree.exists()) {
            return true
        }

        try {
            var current: DocumentFile? = currentDocTree
            var child: String? = currentDocTree.name

            while (true) {
                val parent = current?.parentFile
                if (parent == null || child == null) {
                    break
                }
                if (parent.exists()) {
                    return true
                }
                parent.createDirectory(child)
                child = parent.name // for the next iteration
                current = parent
            }
        } catch (ignored: Exception) {
            // no more parent directories or unsupported by the storage provider
        }

        return false
    }

    fun findFile(filename: String): Uri? {
        if (docTree == null) {
            val res = (ioTree ?: throw IOException("ioTree is null")).resolve(filename)
            return if (Files.exists(res)) Uri.fromFile(res.toFile()) else null
        }

        val res = findFileSAFHelper(context, (docTree ?: throw IOException("docTree is null")), filename)
        return res?.uri
    }

    fun canWrite(): Boolean {
        return if (docTree == null) Files.isWritable(ioTree) else (docTree ?: throw IOException("docTree is null")).canWrite()
    }

    /**
     * @return `false` if the storage is direct, or the SAF storage is valid; `true` if
     * SAF access to this SAF storage is denied.
     */
    fun isInvalidSafStorage(): Boolean {
        return docTree != null && (docTree ?: throw IOException("docTree is null")).name == null
    }

    override fun toString(): String {
        return (if (docTree == null) Uri.fromFile((ioTree ?: throw IOException("ioTree is null")).toFile()) else (docTree ?: throw IOException("docTree is null")).uri).toString()
    }

    companion object {
        private val TAG = StoredDirectoryHelper::class.java.simpleName
        const val PERMISSION_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        private fun addIfStartWith(list: MutableList<String>, base: String, str: String?) {
            if (isNullOrEmpty(str)) {
                return
            }
            val lowerStr = str!!.lowercase()
            if (lowerStr.startsWith(base)) {
                list.add(lowerStr)
            }
        }

        private fun splitFilename(filename: String): Array<String> {
            val dotIndex = filename.lastIndexOf('.')
            if (dotIndex < 0 || dotIndex == filename.length - 1) {
                return arrayOf(filename, "")
            }
            return arrayOf(filename.substring(0, dotIndex), filename.substring(dotIndex))
        }

        private fun makeFileName(name: String, idx: Int, ext: String): String {
            return "$name($idx)$ext"
        }

        @JvmStatic
        fun findFileSAFHelper(context: Context?, tree: DocumentFile, filename: String): DocumentFile? {
            if (context == null) {
                return tree.findFile(filename)
            }

            if (!tree.canRead()) {
                return null
            }

            val nameIndex = 0
            val documentIdIndex = 1

            val selection = "$COLUMN_DISPLAY_NAME = ?"
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree.uri, DocumentsContract.getDocumentId(tree.uri)
            )
            val projection = arrayOf(COLUMN_DISPLAY_NAME, COLUMN_DOCUMENT_ID)
            val contentResolver = context.contentResolver
            val lowerFilename = filename.lowercase()

            contentResolver.query(
                childrenUri, projection, selection, arrayOf(lowerFilename), null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.isNull(nameIndex) ||
                        !cursor.getString(nameIndex).lowercase().startsWith(lowerFilename)
                    ) {
                        continue
                    }

                    return DocumentFile.fromSingleUri(
                        context,
                        DocumentsContract.buildDocumentUriUsingTree(
                            tree.uri, cursor.getString(documentIdIndex)
                        )
                    )
                }
            }

            return null
        }

        @JvmStatic
        fun getPicker(ctx: Context): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .putExtra("android.content.extra.SHOW_ADVANCED", true)
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or PERMISSION_FLAGS)
        }
    }
}
