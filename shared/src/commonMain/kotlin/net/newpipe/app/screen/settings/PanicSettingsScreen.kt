/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.preferences.PanicPreferences
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.screen.settings.model.PanicApp
import net.newpipe.app.viewmodel.settings.PanicSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.panic_app_setting_summary
import newpipe.shared.generated.resources.panic_app_setting_title
import newpipe.shared.generated.resources.panic_clear_app_database_summary
import newpipe.shared.generated.resources.panic_clear_app_database_title
import newpipe.shared.generated.resources.panic_clear_search_history_summary
import newpipe.shared.generated.resources.panic_clear_search_history_title
import newpipe.shared.generated.resources.panic_clear_watch_history_summary
import newpipe.shared.generated.resources.panic_clear_watch_history_title
import newpipe.shared.generated.resources.panic_destructive_actions_summary
import newpipe.shared.generated.resources.panic_destructive_actions_title
import newpipe.shared.generated.resources.panic_exit_app_summary
import newpipe.shared.generated.resources.panic_exit_app_title
import newpipe.shared.generated.resources.panic_remove_from_recent_summary
import newpipe.shared.generated.resources.panic_remove_from_recent_title
import newpipe.shared.generated.resources.panic_settings
import newpipe.shared.generated.resources.panic_settings_summary
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PanicSettingsScreen(
    viewModel: PanicSettingsViewModel = koinViewModel(),
    onBackClicked: () -> Unit = {},
    showTopBar: Boolean = true // TODO: remove when Fragment integration is removed
) {
    val triggerApp by viewModel.triggerApp.collectAsStateWithLifecycle()
    val exitApp by viewModel.exitApp.collectAsStateWithLifecycle()
    val removeFromRecentApps by viewModel.removeFromRecentApps.collectAsStateWithLifecycle()

    val clearAppDatabase by viewModel.clearAppDatabase.collectAsStateWithLifecycle()
    val clearSearchHistory by viewModel.clearSearchHistory.collectAsStateWithLifecycle()
    val clearWatchHistory by viewModel.clearWatchHistory.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = stringResource(Res.string.panic_settings),
                    onNavigateUp = onBackClicked
                )
            }
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            PanicSettingsContent(
                triggerApp = triggerApp,
                exitApp = exitApp,
                removeFromRecentApps = removeFromRecentApps,
                clearAppDatabase = clearAppDatabase,
                clearSearchHistory = clearSearchHistory,
                clearWatchHistory = clearWatchHistory,

                onExitAppChange = viewModel::setExitApp,
                onRemoveFromRecentAppsChange = viewModel::setRemoveFromRecentApps,
                onClearAppDatabaseChange = viewModel::setClearAppDatabase,
                onClearSearchHistoryChange = viewModel::setClearSearchHistory,
                onClearWatchHistoryChange = viewModel::setClearWatchHistory,
            )
        }
    }
}

@Composable
fun PanicSettingsContent(
    triggerApp: PanicApp? = null,
    exitApp: Boolean = PanicPreferences.DEFAULT_EXIT_APP,
    removeFromRecentApps: Boolean = PanicPreferences.DEFAULT_REMOVE_FROM_RECENT_APPS,
    clearAppDatabase: Boolean = PanicPreferences.DEFAULT_CLEAR_APP_DATABASE,
    clearSearchHistory: Boolean = PanicPreferences.DEFAULT_CLEAR_SEARCH_HISTORY,
    clearWatchHistory: Boolean = PanicPreferences.DEFAULT_CLEAR_WATCH_HISTORY,
    onExitAppChange: (Boolean) -> Unit = {},
    onRemoveFromRecentAppsChange: (Boolean) -> Unit = {},
    onClearAppDatabaseChange: (Boolean) -> Unit = {},
    onClearSearchHistoryChange: (Boolean) -> Unit = {},
    onClearWatchHistoryChange: (Boolean) -> Unit = {},
) {
    val triggerAppAvailable = triggerApp != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(WindowInsets.navigationBars.asPaddingValues())
    ) {
        Text(
            text = stringResource(Res.string.panic_settings_summary),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp, end = 8.dp, start = 16.dp)
        )
        SwitchPreference(
            title = stringResource(Res.string.panic_exit_app_title),
            checked = exitApp,
            onCheckedChange = onExitAppChange,
            summary = stringResource(Res.string.panic_exit_app_summary)
        )

        SwitchPreference(
            title = stringResource(Res.string.panic_remove_from_recent_title),
            checked = removeFromRecentApps,
            onCheckedChange = onRemoveFromRecentAppsChange,
            summary = stringResource(Res.string.panic_remove_from_recent_summary)
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        PreferenceCategoryTitle(
            title = stringResource(Res.string.panic_destructive_actions_title),
            summary = stringResource(Res.string.panic_destructive_actions_summary)
        )

        Text(
            text = stringResource(Res.string.panic_app_setting_title),
            modifier = Modifier.padding(top = 16.dp, end = 8.dp, start = 16.dp)
        )

        if (triggerAppAvailable) {
            Text(
                text = triggerApp.name,
                modifier = Modifier.padding(top = 8.dp, end = 8.dp, start = 16.dp),
                style = MaterialTheme.typography.titleSmall
            )
        } else {
            Text(
                text = stringResource(Res.string.panic_app_setting_summary),
                modifier = Modifier.padding(top = 8.dp, end = 8.dp, start = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic
            )
        }

        Spacer(modifier = Modifier.padding(8.dp))

        SwitchPreference(
            title = stringResource(Res.string.panic_clear_app_database_title),
            checked = clearAppDatabase,
            onCheckedChange = onClearAppDatabaseChange,
            summary = stringResource(Res.string.panic_clear_app_database_summary),
            enabled = triggerAppAvailable
        )

        SwitchPreference(
            title = stringResource(Res.string.panic_clear_search_history_title),
            checked = clearSearchHistory,
            enabled = triggerAppAvailable && !clearAppDatabase,
            onCheckedChange = onClearSearchHistoryChange,
            summary = stringResource(Res.string.panic_clear_search_history_summary)
        )

        SwitchPreference(
            title = stringResource(Res.string.panic_clear_watch_history_title),
            checked = clearWatchHistory,
            enabled = triggerAppAvailable && !clearAppDatabase,
            onCheckedChange = onClearWatchHistoryChange,
            summary = stringResource(Res.string.panic_clear_watch_history_summary)
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
fun PanicSettingsContentPreview() {
    PanicSettingsContent()
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview
@Composable
fun PanicSettingsContentPreviewClearDatabase() {
    PanicSettingsContent(
        triggerApp = PanicApp("com.ripple.panic", "Ripple"),
        clearAppDatabase = true
    )
}
