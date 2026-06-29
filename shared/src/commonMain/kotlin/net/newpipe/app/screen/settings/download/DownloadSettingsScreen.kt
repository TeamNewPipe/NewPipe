/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TextPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.download.DownloadSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.download_path_audio_title
import newpipe.shared.generated.resources.download_path_title
import newpipe.shared.generated.resources.downloads_storage_ask_summary
import newpipe.shared.generated.resources.downloads_storage_ask_title
import newpipe.shared.generated.resources.downloads_storage_use_saf_summary
import newpipe.shared.generated.resources.downloads_storage_use_saf_title
import newpipe.shared.generated.resources.settings_category_downloads_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DownloadSettingsScreen(
    navigator: Navigator = koinInject(),
    vm: DownloadSettingsViewModel = koinViewModel()
) {
    val storageAsk by vm.storageAsk.collectAsState()
    val useSaf by vm.useSaf.collectAsState()
    val videoPath by vm.videoPath.collectAsState()
    val audioPath by vm.audioPath.collectAsState()

    DownloadSettingsContent(
        storageAsk = storageAsk,
        useSaf = useSaf,
        videoPath = videoPath,
        audioPath = audioPath,
        onNavigateUp = navigator::navigateUp,
        onToggleStorageAsk = vm::toggleStorageAsk,
        onToggleUseSaf = vm::toggleUseSaf,
        onPickVideoPath = vm::pickVideoPath,
        onPickAudioPath = vm::pickAudioPath
    )
}

@Composable
private fun DownloadSettingsContent(
    storageAsk: Boolean,
    useSaf: Boolean,
    videoPath: String,
    audioPath: String,
    onNavigateUp: () -> Unit,
    onToggleStorageAsk: (Boolean) -> Unit,
    onToggleUseSaf: (Boolean) -> Unit,
    onPickVideoPath: () -> Unit,
    onPickAudioPath: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title =
                    stringResource(Res.string.settings_category_downloads_title),
                onNavigateUp = onNavigateUp
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SwitchPreference(
                title = stringResource(Res.string.downloads_storage_ask_title),
                summary = stringResource(Res.string.downloads_storage_ask_summary),
                isChecked = storageAsk,
                onCheckedChange = onToggleStorageAsk
            )
            SwitchPreference(
                title = stringResource(Res.string.downloads_storage_use_saf_title),
                summary = stringResource(Res.string.downloads_storage_use_saf_summary),
                isChecked = useSaf,
                onCheckedChange = onToggleUseSaf
            )
            TextPreference(
                title = stringResource(Res.string.download_path_title),
                summary = videoPath.takeIf { it.isNotEmpty() },
                onClick = onPickVideoPath
            )
            TextPreference(
                title = stringResource(Res.string.download_path_audio_title),
                summary = audioPath.takeIf { it.isNotEmpty() },
                onClick = onPickAudioPath
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun DownloadSettingsScreenPreview() {
    DownloadSettingsContent(
        storageAsk = false,
        useSaf = true,
        videoPath = "content://com.android.externalstorage/tree/primary%3AMovies",
        audioPath = "",
        onNavigateUp = {},
        onToggleStorageAsk = {},
        onToggleUseSaf = {},
        onPickVideoPath = {},
        onPickAudioPath = {}
    )
}