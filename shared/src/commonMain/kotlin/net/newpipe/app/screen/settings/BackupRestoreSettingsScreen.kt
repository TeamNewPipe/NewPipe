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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.composable.noFileManagerMessage
import net.newpipe.app.model.BackupFile
import net.newpipe.app.model.SettingsFormat
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.BackupRestoreActions
import net.newpipe.app.platform.DownloadStorageActions
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.screen.settings.model.BackupRestoreConfirmation
import net.newpipe.app.screen.settings.model.BackupRestoreMessage
import net.newpipe.app.screen.settings.model.SettingsImport
import net.newpipe.app.viewmodel.settings.BackupRestoreSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.cancel
import newpipe.shared.generated.resources.could_not_import_all_files
import newpipe.shared.generated.resources.error_snackbar_action
import newpipe.shared.generated.resources.export_complete_toast
import newpipe.shared.generated.resources.export_data_summary
import newpipe.shared.generated.resources.export_data_title
import newpipe.shared.generated.resources.export_subscriptions_summary
import newpipe.shared.generated.resources.export_subscriptions_title
import newpipe.shared.generated.resources.import_data_summary
import newpipe.shared.generated.resources.import_data_title
import newpipe.shared.generated.resources.import_network_expensive_warning
import newpipe.shared.generated.resources.import_settings
import newpipe.shared.generated.resources.import_settings_vulnerable_format
import newpipe.shared.generated.resources.import_subscriptions_summary
import newpipe.shared.generated.resources.import_subscriptions_title
import newpipe.shared.generated.resources.no_app_to_open_intent
import newpipe.shared.generated.resources.no_valid_zip_file
import newpipe.shared.generated.resources.ok
import newpipe.shared.generated.resources.override_current_data
import newpipe.shared.generated.resources.reset_all_settings
import newpipe.shared.generated.resources.reset_settings_summary
import newpipe.shared.generated.resources.reset_settings_title
import newpipe.shared.generated.resources.settings_category_backup_restore_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BackupRestoreSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: BackupRestoreSettingsViewModel = koinViewModel(),
    backupRestoreActions: BackupRestoreActions = koinInject(),
    storage: DownloadStorageActions = koinInject()
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pendingSettingsImport by viewModel.pendingSettingsImport.collectAsStateWithLifecycle()
    val storageAccessFrameworkConfigurable = remember { storage.isStorageAccessFrameworkConfigurable }

    var pendingConfirmation by remember { mutableStateOf<BackupRestoreConfirmation?>(null) }
    var noFileManager by remember { mutableStateOf(false) }
    val showNoFileManager = { noFileManager = true }

    val pickDataImport = backupRestoreActions.rememberFilePicker(
        BackupFile.DATA_IMPORT,
        showNoFileManager
    ) { file ->
        pendingConfirmation = BackupRestoreConfirmation.DataImport(file)
    }
    val pickDataExport = backupRestoreActions.rememberFilePicker(
        BackupFile.DATA_EXPORT,
        showNoFileManager,
        viewModel::exportData
    )
    val pickSubscriptionsExport = backupRestoreActions.rememberFilePicker(
        BackupFile.SUBSCRIPTIONS_EXPORT,
        showNoFileManager,
        backupRestoreActions::exportSubscriptions
    )
    val pickSubscriptionsImport = backupRestoreActions.rememberFilePicker(
        BackupFile.SUBSCRIPTIONS_IMPORT,
        showNoFileManager
    ) { file ->
        pendingConfirmation = BackupRestoreConfirmation.SubscriptionsImport(file)
    }

    BackupRestoreSettingsScreenContent(
        message = message,
        pendingConfirmation = pendingConfirmation,
        pendingSettingsImport = pendingSettingsImport,
        noFileManager = noFileManager,
        storageAccessFrameworkConfigurable = storageAccessFrameworkConfigurable,
        onImportData = pickDataImport,
        onExportData = pickDataExport,
        onResetSettings = { pendingConfirmation = BackupRestoreConfirmation.ResetSettings },
        onExportSubscriptions = pickSubscriptionsExport,
        onImportSubscriptions = pickSubscriptionsImport,
        onConfirm = { confirmation ->
            when (confirmation) {
                BackupRestoreConfirmation.ResetSettings -> backupRestoreActions.resetSettings()
                is BackupRestoreConfirmation.DataImport -> viewModel.importData(confirmation.file)
                is BackupRestoreConfirmation.SubscriptionsImport -> backupRestoreActions.importSubscriptions(confirmation.file)
            }
        },
        onDismissConfirmation = { pendingConfirmation = null },
        onImportSettings = viewModel::importSettings,
        onSkipSettingsImport = viewModel::skipSettingsImport,
        onDismissNoFileManager = { noFileManager = false },
        onMessageShown = viewModel::onMessageShown,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun BackupRestoreSettingsScreenContent(
    message: BackupRestoreMessage? = null,
    pendingConfirmation: BackupRestoreConfirmation? = null,
    pendingSettingsImport: SettingsImport? = null,
    noFileManager: Boolean = false,
    storageAccessFrameworkConfigurable: Boolean = false,
    onImportData: () -> Unit = {},
    onExportData: () -> Unit = {},
    onResetSettings: () -> Unit = {},
    onExportSubscriptions: () -> Unit = {},
    onImportSubscriptions: () -> Unit = {},
    onConfirm: (BackupRestoreConfirmation) -> Unit = {},
    onDismissConfirmation: () -> Unit = {},
    onImportSettings: (SettingsImport) -> Unit = {},
    onSkipSettingsImport: (SettingsImport) -> Unit = {},
    onDismissNoFileManager: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val reportText = stringResource(Res.string.error_snackbar_action)

    val messageText = message?.let { pending ->
        when (pending) {
            BackupRestoreMessage.ExportComplete -> {
                stringResource(Res.string.export_complete_toast)
            }

            BackupRestoreMessage.InvalidFile -> {
                stringResource(Res.string.no_valid_zip_file)
            }

            BackupRestoreMessage.PartialImport -> {
                stringResource(Res.string.could_not_import_all_files)
            }

            is BackupRestoreMessage.Failed -> {
                pending.error.message
            }
        }
    }

    LaunchedEffect(message) {
        if (message == null || messageText == null) {
            return@LaunchedEffect
        }
        val error = (message as? BackupRestoreMessage.Failed)?.error
        // The old screen showed these as long toasts and snackbars, and the rest as short toasts
        val duration = when (message) {
            BackupRestoreMessage.PartialImport,
            is BackupRestoreMessage.Failed -> SnackbarDuration.Long

            else -> SnackbarDuration.Short
        }
        // Showing the snackbar from the surrounding scope instead of this effect, so that marking
        // the message as seen below cannot cancel it by restarting the effect
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = messageText,
                actionLabel = error?.let { reportText },
                duration = duration
            )
            if (result == SnackbarResult.ActionPerformed) {
                error?.open()
            }
        }
        onMessageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_backup_restore_title),
                onNavigateUp = onNavigateUp
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            PreferenceRow(
                title = stringResource(Res.string.import_data_title),
                summary = stringResource(Res.string.import_data_summary),
                onClick = onImportData
            )
            PreferenceRow(
                title = stringResource(Res.string.export_data_title),
                summary = stringResource(Res.string.export_data_summary),
                onClick = onExportData
            )
            PreferenceRow(
                title = stringResource(Res.string.reset_settings_title),
                summary = stringResource(Res.string.reset_settings_summary),
                onClick = onResetSettings
            )
            PreferenceRow(
                title = stringResource(Res.string.export_subscriptions_title),
                summary = stringResource(Res.string.export_subscriptions_summary),
                onClick = onExportSubscriptions
            )
            PreferenceRow(
                title = stringResource(Res.string.import_subscriptions_title),
                summary = stringResource(Res.string.import_subscriptions_summary),
                onClick = onImportSubscriptions
            )
        }
    }

    if (pendingConfirmation != null) {
        ConfirmationDialog(
            confirmation = pendingConfirmation,
            onConfirm = {
                onConfirm(pendingConfirmation)
                onDismissConfirmation()
            },
            onDismiss = onDismissConfirmation
        )
    }

    if (pendingSettingsImport != null) {
        SettingsImportDialog(
            format = pendingSettingsImport.format,
            onConfirm = { onImportSettings(pendingSettingsImport) },
            onDismiss = { onSkipSettingsImport(pendingSettingsImport) }
        )
    }

    if (noFileManager) {
        AlertDialog(
            onDismissRequest = onDismissNoFileManager,
            title = { Text(text = stringResource(Res.string.no_app_to_open_intent)) },
            text = { Text(text = noFileManagerMessage(storageAccessFrameworkConfigurable)) },
            confirmButton = {
                TextButton(onClick = onDismissNoFileManager) {
                    Text(text = stringResource(Res.string.ok))
                }
            }
        )
    }
}

@Composable
private fun ConfirmationDialog(
    confirmation: BackupRestoreConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val message = when (confirmation) {
        BackupRestoreConfirmation.ResetSettings -> {
            stringResource(Res.string.reset_all_settings)
        }

        is BackupRestoreConfirmation.DataImport -> {
            stringResource(Res.string.override_current_data)
        }

        is BackupRestoreConfirmation.SubscriptionsImport -> {
            stringResource(Res.string.import_network_expensive_warning)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(Res.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun SettingsImportDialog(
    format: SettingsFormat,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.import_settings)) },
        text = when (format) {
            SettingsFormat.JSON -> {
                null
            }

            SettingsFormat.SERIALIZED -> {
                { Text(text = stringResource(Res.string.import_settings_vulnerable_format)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(Res.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.cancel))
            }
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun BackupRestoreSettingsScreenPreview() {
    BackupRestoreSettingsScreenContent()
}
