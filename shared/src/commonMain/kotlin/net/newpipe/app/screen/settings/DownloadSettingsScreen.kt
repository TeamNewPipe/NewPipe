/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.composable.EditTextPreference
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceEntry
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.DownloadFolderError
import net.newpipe.app.platform.DownloadStorageActions
import net.newpipe.app.platform.FolderPickResult
import net.newpipe.app.preferences.DownloadPreferences.CHARSET_LETTERS_AND_DIGITS
import net.newpipe.app.preferences.DownloadPreferences.CHARSET_MOST_SPECIAL
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_DOWNLOAD_FOLDER
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_FILE_CHARSET
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_LIMIT_QUEUE
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_MAXIMUM_RETRIES
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_PAUSE_ON_METERED_NETWORK
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_REPLACEMENT_CHARACTER
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_STORAGE_ASK
import net.newpipe.app.preferences.DownloadPreferences.DEFAULT_STORAGE_USE_SAF
import net.newpipe.app.preferences.DownloadPreferences.MAXIMUM_RETRIES_NONE
import net.newpipe.app.preferences.DownloadPreferences.MAXIMUM_RETRY_COUNTS
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.DownloadSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.cancel
import newpipe.shared.generated.resources.charset_letters_and_digits
import newpipe.shared.generated.resources.charset_most_special_characters
import newpipe.shared.generated.resources.download_path_audio_summary
import newpipe.shared.generated.resources.download_path_audio_title
import newpipe.shared.generated.resources.download_path_summary
import newpipe.shared.generated.resources.download_path_title
import newpipe.shared.generated.resources.download_to_sdcard_error_message
import newpipe.shared.generated.resources.download_to_sdcard_error_title
import newpipe.shared.generated.resources.downloads_storage_ask_summary
import newpipe.shared.generated.resources.downloads_storage_ask_summary_no_saf_notice
import newpipe.shared.generated.resources.downloads_storage_ask_title
import newpipe.shared.generated.resources.downloads_storage_use_saf_summary
import newpipe.shared.generated.resources.downloads_storage_use_saf_summary_api_29
import newpipe.shared.generated.resources.downloads_storage_use_saf_title
import newpipe.shared.generated.resources.enable_queue_limit
import newpipe.shared.generated.resources.enable_queue_limit_desc
import newpipe.shared.generated.resources.general_error
import newpipe.shared.generated.resources.invalid_directory
import newpipe.shared.generated.resources.max_retry_desc
import newpipe.shared.generated.resources.max_retry_msg
import newpipe.shared.generated.resources.no_app_to_open_intent
import newpipe.shared.generated.resources.no_appropriate_file_manager_message
import newpipe.shared.generated.resources.no_appropriate_file_manager_message_android_10
import newpipe.shared.generated.resources.no_available_dir
import newpipe.shared.generated.resources.none
import newpipe.shared.generated.resources.ok
import newpipe.shared.generated.resources.pause_downloads_on_mobile
import newpipe.shared.generated.resources.pause_downloads_on_mobile_desc
import newpipe.shared.generated.resources.settings_category_downloads_title
import newpipe.shared.generated.resources.settings_file_charset_title
import newpipe.shared.generated.resources.settings_file_replacement_character_summary
import newpipe.shared.generated.resources.settings_file_replacement_character_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DownloadSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: DownloadSettingsViewModel = koinViewModel(),
    storage: DownloadStorageActions = koinInject()
) {
    val storageAsk by viewModel.storageAsk.collectAsStateWithLifecycle()
    val useStorageAccessFramework by viewModel.useStorageAccessFramework
        .collectAsStateWithLifecycle()
    val videoDownloadFolder by viewModel.videoDownloadFolder.collectAsStateWithLifecycle()
    val audioDownloadFolder by viewModel.audioDownloadFolder.collectAsStateWithLifecycle()
    val fileCharset by viewModel.fileCharset.collectAsStateWithLifecycle()
    val replacementCharacter by viewModel.replacementCharacter.collectAsStateWithLifecycle()
    val maximumRetries by viewModel.maximumRetries.collectAsStateWithLifecycle()
    val pauseOnMeteredNetwork by viewModel.pauseOnMeteredNetwork.collectAsStateWithLifecycle()
    val limitQueue by viewModel.limitQueue.collectAsStateWithLifecycle()

    val storageAvailable = remember { storage.isAvailable }
    val storageAccessFrameworkConfigurable = remember {
        storage.isStorageAccessFrameworkConfigurable
    }

    var folderError by remember { mutableStateOf<DownloadFolderError?>(null) }

    fun handlePick(result: FolderPickResult, onPicked: (String) -> Unit) = when (result) {
        is FolderPickResult.Picked -> onPicked(result.folder)
        is FolderPickResult.Failed -> folderError = result.error
    }

    val pickVideoFolder = storage.rememberFolderPicker { result ->
        handlePick(result, viewModel::setVideoDownloadFolder)
    }
    val pickAudioFolder = storage.rememberFolderPicker { result ->
        handlePick(result, viewModel::setAudioDownloadFolder)
    }

    DownloadSettingsScreenContent(
        storageAsk = storageAsk,
        useStorageAccessFramework = useStorageAccessFramework,
        videoDownloadFolder = storage.displayFolder(videoDownloadFolder),
        audioDownloadFolder = storage.displayFolder(audioDownloadFolder),
        fileCharset = fileCharset,
        replacementCharacter = replacementCharacter,
        maximumRetries = maximumRetries,
        pauseOnMeteredNetwork = pauseOnMeteredNetwork,
        limitQueue = limitQueue,
        storageAvailable = storageAvailable,
        storageAccessFrameworkConfigurable = storageAccessFrameworkConfigurable,
        folderError = folderError,
        onStorageAskChange = viewModel::setStorageAsk,
        onUseStorageAccessFrameworkChange = viewModel::setUseStorageAccessFramework,
        onPickVideoFolder = pickVideoFolder,
        onPickAudioFolder = pickAudioFolder,
        onFileCharsetChange = viewModel::setFileCharset,
        onReplacementCharacterChange = viewModel::setReplacementCharacter,
        onMaximumRetriesChange = viewModel::setMaximumRetries,
        onPauseOnMeteredNetworkChange = viewModel::setPauseOnMeteredNetwork,
        onLimitQueueChange = viewModel::setLimitQueue,
        onDismissFolderError = { folderError = null },
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun DownloadSettingsScreenContent(
    storageAsk: Boolean = DEFAULT_STORAGE_ASK,
    useStorageAccessFramework: Boolean = DEFAULT_STORAGE_USE_SAF,
    videoDownloadFolder: String? = null,
    audioDownloadFolder: String? = null,
    fileCharset: String = DEFAULT_FILE_CHARSET,
    replacementCharacter: String = DEFAULT_REPLACEMENT_CHARACTER,
    maximumRetries: String = DEFAULT_MAXIMUM_RETRIES,
    pauseOnMeteredNetwork: Boolean = DEFAULT_PAUSE_ON_METERED_NETWORK,
    limitQueue: Boolean = DEFAULT_LIMIT_QUEUE,
    storageAvailable: Boolean = true,
    storageAccessFrameworkConfigurable: Boolean = true,
    folderError: DownloadFolderError? = null,
    onStorageAskChange: (Boolean) -> Unit = {},
    onUseStorageAccessFrameworkChange: (Boolean) -> Unit = {},
    onPickVideoFolder: () -> Unit = {},
    onPickAudioFolder: () -> Unit = {},
    onFileCharsetChange: (String) -> Unit = {},
    onReplacementCharacterChange: (String) -> Unit = {},
    onMaximumRetriesChange: (String) -> Unit = {},
    onPauseOnMeteredNetworkChange: (Boolean) -> Unit = {},
    onLimitQueueChange: (Boolean) -> Unit = {},
    onDismissFolderError: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val charsetEntries = listOf(
        ListPreferenceEntry(
            CHARSET_LETTERS_AND_DIGITS,
            stringResource(Res.string.charset_letters_and_digits)
        ),
        ListPreferenceEntry(
            CHARSET_MOST_SPECIAL,
            stringResource(Res.string.charset_most_special_characters)
        )
    )

    val noRetriesLabel = stringResource(Res.string.none)
    val maximumRetryEntries = MAXIMUM_RETRY_COUNTS.map { count ->
        ListPreferenceEntry(
            value = count,
            title = if (count == MAXIMUM_RETRIES_NONE) noRetriesLabel else count
        )
    }

    val storageAskSummary = when {
        storageAccessFrameworkConfigurable -> {
            stringResource(Res.string.downloads_storage_ask_summary)
        }

        else -> {
            stringResource(Res.string.downloads_storage_ask_summary_no_saf_notice)
        }
    }
    val useStorageAccessFrameworkSummary = when {
        storageAccessFrameworkConfigurable -> {
            stringResource(Res.string.downloads_storage_use_saf_summary)
        }

        else -> {
            stringResource(Res.string.downloads_storage_use_saf_summary_api_29)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_downloads_title),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            SwitchPreference(
                title = stringResource(Res.string.downloads_storage_ask_title),
                summary = storageAskSummary,
                checked = storageAsk,
                onCheckedChange = onStorageAskChange
            )

            if (storageAvailable) {
                SwitchPreference(
                    title = stringResource(Res.string.downloads_storage_use_saf_title),
                    summary = useStorageAccessFrameworkSummary,
                    checked = useStorageAccessFramework,
                    onCheckedChange = onUseStorageAccessFrameworkChange,
                    enabled = storageAccessFrameworkConfigurable
                )
                PreferenceRow(
                    title = stringResource(Res.string.download_path_title),
                    summary = videoDownloadFolder
                        ?: stringResource(Res.string.download_path_summary),
                    enabled = !storageAsk,
                    onClick = onPickVideoFolder
                )
                PreferenceRow(
                    title = stringResource(Res.string.download_path_audio_title),
                    summary = audioDownloadFolder
                        ?: stringResource(Res.string.download_path_audio_summary),
                    enabled = !storageAsk,
                    onClick = onPickAudioFolder
                )
            }

            ListPreference(
                title = stringResource(Res.string.settings_file_charset_title),
                entries = charsetEntries,
                selectedValue = fileCharset,
                onValueSelected = onFileCharsetChange
            )
            EditTextPreference(
                title = stringResource(Res.string.settings_file_replacement_character_title),
                summary = stringResource(
                    Res.string.settings_file_replacement_character_summary
                ),
                value = replacementCharacter,
                onValueChange = onReplacementCharacterChange
            )
            ListPreference(
                title = stringResource(Res.string.max_retry_msg),
                entries = maximumRetryEntries,
                selectedValue = maximumRetries,
                onValueSelected = onMaximumRetriesChange,
                summary = stringResource(Res.string.max_retry_desc)
            )
            SwitchPreference(
                title = stringResource(Res.string.pause_downloads_on_mobile),
                summary = stringResource(Res.string.pause_downloads_on_mobile_desc),
                checked = pauseOnMeteredNetwork,
                onCheckedChange = onPauseOnMeteredNetworkChange
            )
            SwitchPreference(
                title = stringResource(Res.string.enable_queue_limit),
                summary = stringResource(Res.string.enable_queue_limit_desc),
                checked = limitQueue,
                onCheckedChange = onLimitQueueChange
            )
        }
    }

    if (folderError != null) {
        DownloadFolderErrorDialog(
            error = folderError,
            storageAccessFrameworkConfigurable = storageAccessFrameworkConfigurable,
            onDismiss = onDismissFolderError
        )
    }
}

/**
 * The folder picker failures the legacy screen reports, each with its own title and message.
 */
@Composable
private fun DownloadFolderErrorDialog(
    error: DownloadFolderError,
    storageAccessFrameworkConfigurable: Boolean,
    onDismiss: () -> Unit
) {
    val safTitle = stringResource(Res.string.downloads_storage_use_saf_title)

    val title = when (error) {
        DownloadFolderError.NO_FILE_MANAGER -> {
            stringResource(Res.string.no_app_to_open_intent)
        }

        DownloadFolderError.EXTERNAL_STORAGE_UNAVAILABLE -> {
            stringResource(Res.string.download_to_sdcard_error_title)
        }

        DownloadFolderError.INVALID_DIRECTORY,
        DownloadFolderError.NOT_WRITABLE -> {
            stringResource(Res.string.general_error)
        }
    }

    // Only Android 9 and below can be told to turn SAF off
    val fileManagerMessage = when {
        storageAccessFrameworkConfigurable -> {
            stringResource(Res.string.no_appropriate_file_manager_message, safTitle)
        }

        else -> {
            stringResource(Res.string.no_appropriate_file_manager_message_android_10)
        }
    }
    val message = when (error) {
        DownloadFolderError.NO_FILE_MANAGER -> {
            fileManagerMessage
        }

        DownloadFolderError.INVALID_DIRECTORY -> {
            stringResource(Res.string.invalid_directory)
        }

        DownloadFolderError.NOT_WRITABLE -> {
            stringResource(Res.string.no_available_dir)
        }

        DownloadFolderError.EXTERNAL_STORAGE_UNAVAILABLE -> {
            stringResource(Res.string.download_to_sdcard_error_message)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(Res.string.ok)) }
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun DownloadSettingsScreenPreview() {
    DownloadSettingsScreenContent()
}
