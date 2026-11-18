/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import java.io.IOException
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.MissionState

internal fun interface DownloadReadyListener {
    fun onDownloadReady(storage: StoredFileHelper, directory: StoredDirectoryHelper?)
}

internal fun interface DownloadFailureListener {
    fun onDownloadFailure(@StringRes message: Int)
}

internal enum class DownloadConflictAction {
    REPLACE,
    UNIQUE_NAME,
    REUSE_EXISTING
}

internal data class DownloadConflictPrompt(
    @StringRes val positiveButton: Int,
    @StringRes val message: Int,
    val action: DownloadConflictAction
)

internal object DownloadStorageConflictPolicy {
    @JvmStatic
    fun forMissionState(state: MissionState): DownloadConflictPrompt {
        return when (state) {
            MissionState.Finished -> DownloadConflictPrompt(
                positiveButton = R.string.overwrite,
                message = R.string.overwrite_finished_warning,
                action = DownloadConflictAction.REPLACE
            )

            MissionState.Pending -> DownloadConflictPrompt(
                positiveButton = R.string.overwrite,
                message = R.string.download_already_pending,
                action = DownloadConflictAction.REPLACE
            )

            MissionState.PendingRunning -> DownloadConflictPrompt(
                positiveButton = R.string.generate_unique_name,
                message = R.string.download_already_running,
                action = DownloadConflictAction.UNIQUE_NAME
            )

            MissionState.None -> DownloadConflictPrompt(
                positiveButton = R.string.overwrite,
                message = R.string.overwrite_unrelated_warning,
                action = DownloadConflictAction.REUSE_EXISTING
            )
        }
    }
}

/** Resolves target files and existing-mission conflicts outside the download Fragment. */
internal class DownloadStorageCoordinator(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val readyListener: DownloadReadyListener,
    private val failureListener: DownloadFailureListener
) {
    fun check(
        mainStorage: StoredDirectoryHelper?,
        targetFile: Uri?,
        filename: String,
        mime: String?
    ) {
        val storage = try {
            when {
                mainStorage == null -> StoredFileHelper(
                    context,
                    null,
                    requireNotNull(targetFile),
                    ""
                )

                targetFile == null -> StoredFileHelper(
                    mainStorage.uri,
                    filename,
                    mime,
                    mainStorage.tag
                )

                else -> StoredFileHelper(
                    context,
                    mainStorage.uri,
                    targetFile,
                    mainStorage.tag
                )
            }
        } catch (exception: Exception) {
            ErrorUtil.createNotification(
                context,
                ErrorInfo(exception, UserAction.DOWNLOAD_FAILED, "Getting storage")
            )
            return
        }

        val state = downloadManager.checkForExistingMission(storage)
        if (state == MissionState.None) {
            if (mainStorage == null) {
                if (!storage.existsAsFile() && !storage.create()) {
                    failureListener.onDownloadFailure(R.string.error_file_creation)
                    return
                }
                readyListener.onDownloadReady(storage, null)
                return
            }

            if (targetFile == null) {
                createNewTarget(mainStorage, filename, mime)
                return
            }
        }

        showConflict(
            storage = storage,
            mainStorage = mainStorage,
            targetFile = targetFile,
            filename = filename,
            mime = mime,
            prompt = DownloadStorageConflictPolicy.forMissionState(state)
        )
    }

    private fun createNewTarget(
        mainStorage: StoredDirectoryHelper,
        filename: String,
        mime: String?
    ) {
        if (!mainStorage.mkdirs()) {
            failureListener.onDownloadFailure(R.string.error_path_creation)
            return
        }
        val storage = mainStorage.createFile(filename, mime)
        if (storage == null || !storage.canWrite()) {
            failureListener.onDownloadFailure(R.string.error_file_creation)
            return
        }
        readyListener.onDownloadReady(storage, mainStorage)
    }

    private fun showConflict(
        storage: StoredFileHelper,
        mainStorage: StoredDirectoryHelper?,
        targetFile: Uri?,
        filename: String,
        mime: String?,
        prompt: DownloadConflictPrompt
    ) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.download_dialog_title)
            .setMessage(prompt.message)
            .setNegativeButton(R.string.cancel, null)

        if (mainStorage == null) {
            if (prompt.action == DownloadConflictAction.REPLACE) {
                dialog.setPositiveButton(prompt.positiveButton) { alert, _ ->
                    alert.dismiss()
                    downloadManager.forgetMission(storage)
                    readyListener.onDownloadReady(storage, null)
                }
            }
            dialog.show()
            return
        }

        dialog.setPositiveButton(prompt.positiveButton) { alert, _ ->
            alert.dismiss()
            when (prompt.action) {
                DownloadConflictAction.REPLACE -> {
                    downloadManager.forgetMission(storage)
                    createOrTakeTarget(mainStorage, targetFile, filename, mime)
                }

                DownloadConflictAction.REUSE_EXISTING ->
                    createOrTakeTarget(mainStorage, targetFile, filename, mime)

                DownloadConflictAction.UNIQUE_NAME -> {
                    val uniqueStorage = mainStorage.createUniqueFile(filename, mime)
                    if (uniqueStorage == null) {
                        failureListener.onDownloadFailure(R.string.error_file_creation)
                    } else {
                        readyListener.onDownloadReady(uniqueStorage, mainStorage)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun createOrTakeTarget(
        mainStorage: StoredDirectoryHelper,
        targetFile: Uri?,
        filename: String,
        mime: String?
    ) {
        val replacement = if (targetFile == null) {
            mainStorage.createFile(filename, mime)
        } else {
            try {
                StoredFileHelper(
                    context,
                    mainStorage.uri,
                    targetFile,
                    mainStorage.tag
                )
            } catch (_: IOException) {
                Log.e(TAG, "Failed to take (or steal) the file in $targetFile")
                null
            }
        }

        if (replacement != null && replacement.canWrite()) {
            readyListener.onDownloadReady(replacement, mainStorage)
        } else {
            failureListener.onDownloadFailure(R.string.error_file_creation)
        }
    }

    private companion object {
        const val TAG = "DownloadStorageCoordinator"
    }
}
