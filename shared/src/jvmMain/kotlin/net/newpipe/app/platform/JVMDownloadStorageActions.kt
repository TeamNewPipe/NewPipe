/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import androidx.compose.runtime.Composable
import org.koin.core.annotation.Singleton

@Singleton(binds = [DownloadStorageActions::class])
class JVMDownloadStorageActions : DownloadStorageActions {

    override val isAvailable = false

    override val useStorageAccessFramework = false

    override val isStorageAccessFrameworkConfigurable = false

    override fun defaultDownloadFolders() = DownloadFolders(video = "", audio = "")

    override fun displayFolder(storedValue: String): String? = storedValue.ifEmpty { null }

    @Composable
    override fun rememberFolderPicker(onResult: (FolderPickResult) -> Unit): () -> Unit = {}
}
