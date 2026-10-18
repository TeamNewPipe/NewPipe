/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.content.Intent
import android.net.Uri

/**
 * Hooks into the storage helpers that still live in the current Android app
 */
interface DownloadStorageHost {

    val useStorageAccessFramework: Boolean

    fun createFolderPickerIntent(): Intent

    fun acquireFolder(folder: Uri): FolderPickResult
}
