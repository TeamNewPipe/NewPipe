/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.screen.settings.backuprestore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.composable.ConfirmDialog
import net.newpipe.app.composable.TextPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.backuprestore.BackupRestoreSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.cancel
import newpipe.shared.generated.resources.export_data_summary
import newpipe.shared.generated.resources.export_data_title
import newpipe.shared.generated.resources.export_subscriptions_summary
import newpipe.shared.generated.resources.export_subscriptions_title
import newpipe.shared.generated.resources.import_data_summary
import newpipe.shared.generated.resources.import_data_title
import newpipe.shared.generated.resources.import_subscriptions_summary
import newpipe.shared.generated.resources.import_subscriptions_title
import newpipe.shared.generated.resources.ok
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
    viewModel: BackupRestoreSettingsViewModel = koinViewModel()
) {
    BackupRestoreSettingsContent(
        onNavigateUp = navigator::navigateUp,
        onImportDatabase = viewModel::importDatabase,
        onExportDatabase = viewModel::exportDatabase,
        onResetSettings = viewModel::resetAllSettings,
        onImportSubscriptions = viewModel::importSubscriptions,
        onExportSubscriptions = viewModel::exportSubscriptions
    )
}

@Composable
private fun BackupRestoreSettingsContent(
    onNavigateUp: () -> Unit,
    onImportDatabase: () -> Unit,
    onExportDatabase: () -> Unit,
    onResetSettings: () -> Unit,
    onImportSubscriptions: () -> Unit,
    onExportSubscriptions: () -> Unit
) {
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    if (showResetConfirm) {
        ConfirmDialog(
            title = stringResource(Res.string.reset_settings_title),
            message = stringResource(Res.string.reset_all_settings),
            confirmLabel = stringResource(Res.string.ok),
            dismissLabel = stringResource(Res.string.cancel),
            onConfirm = {
                showResetConfirm = false
                onResetSettings()
            },
            onDismiss = { showResetConfirm = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title =
                    stringResource(Res.string.settings_category_backup_restore_title),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            TextPreference(
                title = stringResource(Res.string.import_data_title),
                summary = stringResource(Res.string.import_data_summary),
                onClick = onImportDatabase
            )
            TextPreference(
                title = stringResource(Res.string.export_data_title),
                summary = stringResource(Res.string.export_data_summary),
                onClick = onExportDatabase
            )
            TextPreference(
                title = stringResource(Res.string.reset_settings_title),
                summary =
                    stringResource(Res.string.reset_settings_summary),
                onClick = { showResetConfirm = true }
            )
            TextPreference(
                title =
                    stringResource(Res.string.export_subscriptions_title),
                summary =
                    stringResource(Res.string.export_subscriptions_summary),
                onClick = onExportSubscriptions
            )
            TextPreference(
                title =
                    stringResource(Res.string.import_subscriptions_title),
                summary =
                    stringResource(Res.string.import_subscriptions_summary),
                onClick = onImportSubscriptions
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun BackupRestoreSettingsScreenPreview() {
    BackupRestoreSettingsContent(
        onNavigateUp = {},
        onImportDatabase = {},
        onExportDatabase = {},
        onResetSettings = {},
        onImportSubscriptions = {},
        onExportSubscriptions = {}
    )
}