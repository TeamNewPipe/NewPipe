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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.UpdateActions
import net.newpipe.app.preferences.UpdatePreferences.DEFAULT_UPDATE_APP
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.UpdateSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.check_for_updates
import newpipe.shared.generated.resources.checking_updates_toast
import newpipe.shared.generated.resources.manual_update_description
import newpipe.shared.generated.resources.settings_category_updates_title
import newpipe.shared.generated.resources.updates_setting_description
import newpipe.shared.generated.resources.updates_setting_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UpdateSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: UpdateSettingsViewModel = koinViewModel(),
    updateActions: UpdateActions = koinInject()
) {
    val updateChecksEnabled by viewModel.updateChecksEnabled.collectAsStateWithLifecycle()

    UpdateSettingsScreenContent(
        updateChecksEnabled = updateChecksEnabled,
        onUpdateChecksEnabledChange = viewModel::setUpdateChecksEnabled,
        onCheckForUpdates = updateActions::checkForUpdates,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun UpdateSettingsScreenContent(
    updateChecksEnabled: Boolean = DEFAULT_UPDATE_APP,
    onUpdateChecksEnabledChange: (Boolean) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val checkingUpdatesText = stringResource(Res.string.checking_updates_toast)

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_updates_title),
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
            SwitchPreference(
                title = stringResource(Res.string.updates_setting_title),
                summary = stringResource(Res.string.updates_setting_description),
                checked = updateChecksEnabled,
                onCheckedChange = onUpdateChecksEnabledChange
            )
            PreferenceRow(
                title = stringResource(Res.string.check_for_updates),
                summary = stringResource(Res.string.manual_update_description),
                onClick = {
                    scope.launch { snackbarHostState.showSnackbar(checkingUpdatesText) }
                    onCheckForUpdates()
                }
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun UpdateSettingsScreenPreview() {
    UpdateSettingsScreenContent()
}
