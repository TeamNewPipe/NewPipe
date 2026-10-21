/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import androidx.compose.runtime.Composable

data class DownloadFolders(val video: String, val audio: String)

enum class DownloadFolderError {
    NO_FILE_MANAGER,
    INVALID_DIRECTORY,
    NOT_WRITABLE,
    EXTERNAL_STORAGE_UNAVAILABLE
}

sealed interface FolderPickResult {

    data class Picked(val folder: String) : FolderPickResult

    data class Failed(val error: DownloadFolderError) : FolderPickResult
}

/**
 * Platform-specific storage handling for the Download settings screen.
 */
interface DownloadStorageActions {

    val isAvailable: Boolean

    val useStorageAccessFramework: Boolean

    val isStorageAccessFrameworkConfigurable: Boolean

    fun defaultDownloadFolders(): DownloadFolders

    fun displayFolder(storedValue: String): String?

    @Composable
    fun rememberFolderPicker(onResult: (FolderPickResult) -> Unit): () -> Unit
}
