/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.screen.settings.updates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import net.newpipe.app.viewmodel.settings.updates.UpdatesSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.check_for_updates
import newpipe.shared.generated.resources.manual_update_description
import newpipe.shared.generated.resources.settings_category_updates_title
import newpipe.shared.generated.resources.updates_setting_description
import newpipe.shared.generated.resources.updates_setting_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UpdatesSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: UpdatesSettingsViewModel = koinViewModel()
) {
    val updateApp by viewModel.updateApp.collectAsState()

    UpdatesSettingsContent(
        updateApp = updateApp,
        onNavigateUp = navigator::navigateUp,
        onToggleUpdateApp = viewModel::toggleUpdateApp,
        onManualCheck = viewModel::runManualCheck
    )
}

@Composable
private fun UpdatesSettingsContent(
    updateApp: Boolean,
    onNavigateUp: () -> Unit,
    onToggleUpdateApp: (Boolean) -> Unit,
    onManualCheck: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_updates_title),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            SwitchPreference(
                title = stringResource(Res.string.updates_setting_title),
                summary = stringResource(Res.string.updates_setting_description),
                isChecked = updateApp,
                onCheckedChange = onToggleUpdateApp
            )
            TextPreference(
                title = stringResource(Res.string.check_for_updates),
                summary = stringResource(Res.string.manual_update_description),
                onClick = onManualCheck
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun UpdatesSettingsScreenPreview() {
    UpdatesSettingsContent(
        updateApp = false,
        onNavigateUp = {},
        onToggleUpdateApp = {},
        onManualCheck = {}
    )
}