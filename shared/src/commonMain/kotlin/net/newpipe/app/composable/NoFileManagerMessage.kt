/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.runtime.Composable
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.downloads_storage_use_saf_title
import newpipe.shared.generated.resources.no_appropriate_file_manager_message
import newpipe.shared.generated.resources.no_appropriate_file_manager_message_android_10
import org.jetbrains.compose.resources.stringResource

@Composable
fun noFileManagerMessage(storageAccessFrameworkConfigurable: Boolean): String = when {
    // Only Android 9 and below can be told to turn SAF off
    storageAccessFrameworkConfigurable -> {
        stringResource(
            Res.string.no_appropriate_file_manager_message,
            stringResource(Res.string.downloads_storage_use_saf_title)
        )
    }

    else -> {
        stringResource(Res.string.no_appropriate_file_manager_message_android_10)
    }
}
