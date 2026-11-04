/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package org.schabi.newpipe.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.nononsenseapps.filepicker.Utils
import java.io.IOException
import net.newpipe.app.platform.DownloadFolderError
import net.newpipe.app.platform.DownloadStorageHost
import net.newpipe.app.platform.FolderPickResult
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.util.FilePickerActivityHelper

/**
 * Host-side storage handling for the Compose download settings screen
 */
class ComposeDownloadStorage(private val context: Context) : DownloadStorageHost {

    override val useStorageAccessFramework: Boolean
        get() = NewPipeSettings.useStorageAccessFramework(context)

    override fun createFolderPickerIntent(): Intent = StoredDirectoryHelper.getPicker(context)

    override fun acquireFolder(folder: Uri): FolderPickResult {
        if (FilePickerActivityHelper.isOwnFileUri(context, folder)) {
            val target = Utils.getFileForUri(folder)
            return when {
                target.canWrite() -> FolderPickResult.Picked(Uri.fromFile(target).toString())
                else -> FolderPickResult.Failed(DownloadFolderError.EXTERNAL_STORAGE_UNAVAILABLE)
            }
        }

        return try {
            context.grantUriPermission(
                context.packageName,
                folder,
                StoredDirectoryHelper.PERMISSION_FLAGS
            )
            if (!StoredDirectoryHelper(context, folder, null).canWrite()) {
                throw IOException("No write permissions on $folder")
            }
            Log.i(TAG, "Acquiring tree success from $folder")
            FolderPickResult.Picked(folder.toString())
        } catch (error: IOException) {
            Log.e(TAG, "Error acquiring tree from $folder", error)
            FolderPickResult.Failed(DownloadFolderError.NOT_WRITABLE)
        }
    }

    private companion object {
        const val TAG = "ComposeDownloadStorage"
    }
}
