/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import java.io.File
import org.koin.core.annotation.Singleton

@Singleton(binds = [DownloadStorageActions::class])
class AndroidDownloadStorageActions(context: Context) : DownloadStorageActions {

    private val host: DownloadStorageHost? = loadHost(context)

    override val isAvailable: Boolean
        get() = host != null

    override val useStorageAccessFramework: Boolean
        get() = host?.useStorageAccessFramework ?: true

    override val isStorageAccessFrameworkConfigurable: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun defaultDownloadFolders() = DownloadFolders(
        video = newPipeFolderIn(Environment.DIRECTORY_MOVIES),
        audio = newPipeFolderIn(Environment.DIRECTORY_MUSIC)
    )

    override fun displayFolder(storedValue: String): String? {
        val folder = storedValue.toUri()
        if (folder == Uri.EMPTY) {
            return null
        }
        return when (folder.scheme) {
            ContentResolver.SCHEME_FILE -> folder.path
            else -> folder.toString()
        }
    }

    @Composable
    override fun rememberFolderPicker(onResult: (FolderPickResult) -> Unit): () -> Unit {
        val currentOnResult by rememberUpdatedState(onResult)

        val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@rememberLauncherForActivityResult
            }
            val folder = result.data?.data
            val bridge = host
            currentOnResult(
                when {
                    folder == null || bridge == null -> {
                        FolderPickResult.Failed(DownloadFolderError.INVALID_DIRECTORY)
                    }

                    else -> {
                        bridge.acquireFolder(folder)
                    }
                }
            )
        }

        return remember(launcher) {
            {
                val bridge = host
                if (bridge != null) {
                    try {
                        launcher.launch(bridge.createFolderPickerIntent())
                    } catch (exception: ActivityNotFoundException) {
                        Logger.w(
                            messageString = "Unable to launch the download folder picker",
                            throwable = exception
                        )
                        currentOnResult(
                            FolderPickResult.Failed(DownloadFolderError.NO_FILE_MANAGER)
                        )
                    }
                }
            }
        }
    }

    private fun newPipeFolderIn(directoryName: String): String {
        val directory = File(Environment.getExternalStorageDirectory(), directoryName)
        return File(directory, NEWPIPE_FOLDER).toURI().toString()
    }

    private fun loadHost(context: Context): DownloadStorageHost? = runCatching {
        Class.forName(HOST_CLASS)
            .getDeclaredConstructor(Context::class.java)
            .newInstance(context) as DownloadStorageHost
    }.getOrElse { error ->
        Logger.w(messageString = "No host download storage found", throwable = error)
        null
    }

    private companion object {
        const val HOST_CLASS = "org.schabi.newpipe.settings.ComposeDownloadStorage"
        const val NEWPIPE_FOLDER = "NewPipe"
    }
}
