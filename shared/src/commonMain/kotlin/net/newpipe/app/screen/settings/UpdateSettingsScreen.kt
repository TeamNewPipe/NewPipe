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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preferences.UpdatePreferences.DEFAULT_UPDATE_APP
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.UpdateSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.checking_updates_toast
import newpipe.shared.generated.resources.manual_update_description
import newpipe.shared.generated.resources.manual_update_title
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
) {
    val updateAppEnabled by viewModel.updateAppEnabled.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val checkingMessage = stringResource(Res.string.checking_updates_toast)
    LaunchedEffect(viewModel) {
        viewModel.checkingForUpdates.collect {
            snackbarHostState.showSnackbar(checkingMessage)
        }
    }

    UpdateSettingsScreenContent(
        updateAppEnabled = updateAppEnabled,
        onUpdateAppEnabledChanged = viewModel::setUpdateAppEnabled,
        onManualUpdateClicked = viewModel::onManualUpdateClicked,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { navigator.navigateUp() },
    )
}

@Composable
fun UpdateSettingsScreenContent(
    updateAppEnabled: Boolean,
    onUpdateAppEnabledChanged: (Boolean) -> Unit,
    onManualUpdateClicked: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_updates_title),
                onNavigateUp = onNavigateUp,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            SwitchPreference(
                title = stringResource(Res.string.updates_setting_title),
                summary = stringResource(Res.string.updates_setting_description),
                checked = updateAppEnabled,
                onCheckedChange = onUpdateAppEnabledChanged,
            )

            PreferenceRow(
                title = stringResource(Res.string.manual_update_title),
                summary = stringResource(Res.string.manual_update_description),
                onClick = onManualUpdateClicked,
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun UpdateSettingsScreenPreview() {
    UpdateSettingsScreenContent(
        updateAppEnabled = DEFAULT_UPDATE_APP,
        onUpdateAppEnabledChanged = {},
        onManualUpdateClicked = {},
    )
}