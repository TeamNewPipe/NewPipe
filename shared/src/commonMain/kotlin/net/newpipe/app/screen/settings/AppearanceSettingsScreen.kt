/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.screen.settings

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
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceEntry
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.AppearanceActions
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.preferences.AppearancePreferences
import net.newpipe.app.viewmodel.settings.AppearanceSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.auto
import newpipe.shared.generated.resources.auto_device_theme_title
import newpipe.shared.generated.resources.black_theme_title
import newpipe.shared.generated.resources.caption_setting_description
import newpipe.shared.generated.resources.caption_setting_title
import newpipe.shared.generated.resources.card
import newpipe.shared.generated.resources.dark_theme_title
import newpipe.shared.generated.resources.grid
import newpipe.shared.generated.resources.light_theme_title
import newpipe.shared.generated.resources.list
import newpipe.shared.generated.resources.list_view_mode
import newpipe.shared.generated.resources.main_tabs_position_summary
import newpipe.shared.generated.resources.main_tabs_position_title
import newpipe.shared.generated.resources.night_theme_available
import newpipe.shared.generated.resources.night_theme_summary
import newpipe.shared.generated.resources.night_theme_title
import newpipe.shared.generated.resources.off
import newpipe.shared.generated.resources.on
import newpipe.shared.generated.resources.select_night_theme_toast
import newpipe.shared.generated.resources.settings_category_appearance_title
import newpipe.shared.generated.resources.show_hold_to_append_summary
import newpipe.shared.generated.resources.show_hold_to_append_title
import newpipe.shared.generated.resources.tablet_mode_title
import newpipe.shared.generated.resources.theme_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppearanceSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: AppearanceSettingsViewModel = koinViewModel(),
    appearanceActions: AppearanceActions = koinInject()
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val nightTheme by viewModel.nightTheme.collectAsStateWithLifecycle()
    val holdToAppend by viewModel.holdToAppend.collectAsStateWithLifecycle()
    val tabletMode by viewModel.tabletMode.collectAsStateWithLifecycle()
    val listViewMode by viewModel.listViewMode.collectAsStateWithLifecycle()
    val mainTabsPosition by viewModel.mainTabsPosition.collectAsStateWithLifecycle()

    AppearanceSettingsScreenContent(
        theme = theme,
        nightTheme = nightTheme,
        holdToAppend = holdToAppend,
        tabletMode = tabletMode,
        listViewMode = listViewMode,
        mainTabsPosition = mainTabsPosition,
        onThemeChange = viewModel::setTheme,
        onNightThemeChange = viewModel::setNightTheme,
        onHoldToAppendChange = viewModel::setHoldToAppend,
        onTabletModeChange = viewModel::setTabletMode,
        onListViewModeChange = viewModel::setListViewMode,
        onMainTabsPositionChange = viewModel::setMainTabsPosition,
        onCaptionSettingsClick = appearanceActions::openCaptionSettings,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun AppearanceSettingsScreenContent(
    theme: String = AppearancePreferences.DEFAULT_THEME,
    nightTheme: String = AppearancePreferences.DEFAULT_NIGHT_THEME,
    holdToAppend: Boolean = AppearancePreferences.DEFAULT_HOLD_TO_APPEND,
    tabletMode: String = AppearancePreferences.DEFAULT_TABLET_MODE,
    listViewMode: String = AppearancePreferences.DEFAULT_LIST_VIEW_MODE,
    mainTabsPosition: Boolean = AppearancePreferences.DEFAULT_MAIN_TABS_POSITION,
    onThemeChange: (String) -> Unit = {},
    onNightThemeChange: (String) -> Unit = {},
    onHoldToAppendChange: (Boolean) -> Unit = {},
    onTabletModeChange: (String) -> Unit = {},
    onListViewModeChange: (String) -> Unit = {},
    onMainTabsPositionChange: (Boolean) -> Unit = {},
    onCaptionSettingsClick: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectNightThemeMessage = stringResource(Res.string.select_night_theme_toast)

    val themeEntries = listOf(
        ListPreferenceEntry(AppearancePreferences.THEME_LIGHT, stringResource(Res.string.light_theme_title)),
        ListPreferenceEntry(AppearancePreferences.THEME_DARK, stringResource(Res.string.dark_theme_title)),
        ListPreferenceEntry(AppearancePreferences.THEME_BLACK, stringResource(Res.string.black_theme_title)),
        ListPreferenceEntry(AppearancePreferences.THEME_AUTO_DEVICE, stringResource(Res.string.auto_device_theme_title))
    )
    val nightThemeEntries = listOf(
        ListPreferenceEntry(AppearancePreferences.THEME_DARK, stringResource(Res.string.dark_theme_title)),
        ListPreferenceEntry(AppearancePreferences.THEME_BLACK, stringResource(Res.string.black_theme_title))
    )
    val tabletModeEntries = listOf(
        ListPreferenceEntry(AppearancePreferences.TABLET_MODE_AUTO, stringResource(Res.string.auto)),
        ListPreferenceEntry(AppearancePreferences.TABLET_MODE_ON, stringResource(Res.string.on)),
        ListPreferenceEntry(AppearancePreferences.TABLET_MODE_OFF, stringResource(Res.string.off))
    )
    val listViewModeEntries = listOf(
        ListPreferenceEntry(AppearancePreferences.LIST_VIEW_AUTO, stringResource(Res.string.auto)),
        ListPreferenceEntry(AppearancePreferences.LIST_VIEW_LIST, stringResource(Res.string.list)),
        ListPreferenceEntry(AppearancePreferences.LIST_VIEW_GRID, stringResource(Res.string.grid)),
        ListPreferenceEntry(AppearancePreferences.LIST_VIEW_CARD, stringResource(Res.string.card))
    )

    val nightThemeEnabled = theme == AppearancePreferences.THEME_AUTO_DEVICE
    val nightThemeSummary = if (nightThemeEnabled) {
        val selectedTitle = nightThemeEntries.firstOrNull { it.value == nightTheme }?.title.orEmpty()
        stringResource(Res.string.night_theme_summary, selectedTitle)
    } else {
        stringResource(Res.string.night_theme_available, stringResource(Res.string.auto_device_theme_title))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_appearance_title),
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
            ListPreference(
                title = stringResource(Res.string.theme_title),
                entries = themeEntries,
                selectedValue = theme,
                onValueSelected = { value ->
                    onThemeChange(value)
                    if (value == AppearancePreferences.THEME_AUTO_DEVICE) {
                        scope.launch { snackbarHostState.showSnackbar(selectNightThemeMessage) }
                    }
                }
            )
            ListPreference(
                title = stringResource(Res.string.night_theme_title),
                entries = nightThemeEntries,
                selectedValue = nightTheme,
                onValueSelected = onNightThemeChange,
                summary = nightThemeSummary,
                enabled = nightThemeEnabled
            )
            SwitchPreference(
                title = stringResource(Res.string.show_hold_to_append_title),
                summary = stringResource(Res.string.show_hold_to_append_summary),
                checked = holdToAppend,
                onCheckedChange = onHoldToAppendChange
            )
            ListPreference(
                title = stringResource(Res.string.tablet_mode_title),
                entries = tabletModeEntries,
                selectedValue = tabletMode,
                onValueSelected = onTabletModeChange
            )
            ListPreference(
                title = stringResource(Res.string.list_view_mode),
                entries = listViewModeEntries,
                selectedValue = listViewMode,
                onValueSelected = onListViewModeChange
            )
            PreferenceRow(
                title = stringResource(Res.string.caption_setting_title),
                summary = stringResource(Res.string.caption_setting_description),
                onClick = onCaptionSettingsClick
            )
            SwitchPreference(
                title = stringResource(Res.string.main_tabs_position_title),
                summary = stringResource(Res.string.main_tabs_position_summary),
                checked = mainTabsPosition,
                onCheckedChange = onMainTabsPositionChange
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun AppearanceSettingsScreenPreview() {
    AppearanceSettingsScreenContent()
}