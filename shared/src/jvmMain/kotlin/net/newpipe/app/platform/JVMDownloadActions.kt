/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import javax.swing.JFileChooser
import org.koin.core.annotation.Singleton

/**
 * JVM (desktop) implementation of [DownloadActions].
 *
 * Uses Swing's [JFileChooser] in directories-only mode.
 * Returns the absolute file path as the URI string.
 */
@Singleton(binds = [DownloadActions::class])
class JVMDownloadActions : DownloadActions {
    override fun pickDirectory(onPicked: (String) -> Unit) {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose download folder"
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile ?: run {
                Logger.w(messageString = "JFileChooser approved but returned null file")
                    return
            }
            onPicked(file.absolutePath)
        }
    }
}
