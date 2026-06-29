/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.screen.settings.lookfeel

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
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceOption
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TextPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.lookfeel.LIST_VIEW_AUTO
import net.newpipe.app.viewmodel.settings.lookfeel.LIST_VIEW_CARD
import net.newpipe.app.viewmodel.settings.lookfeel.LIST_VIEW_GRID
import net.newpipe.app.viewmodel.settings.lookfeel.LIST_VIEW_LIST
import net.newpipe.app.viewmodel.settings.lookfeel.LookFeelSettingsViewModel
import net.newpipe.app.viewmodel.settings.lookfeel.TABLET_AUTO
import net.newpipe.app.viewmodel.settings.lookfeel.TABLET_OFF
import net.newpipe.app.viewmodel.settings.lookfeel.TABLET_ON
import net.newpipe.app.viewmodel.settings.lookfeel.THEME_AUTO
import net.newpipe.app.viewmodel.settings.lookfeel.THEME_BLACK
import net.newpipe.app.viewmodel.settings.lookfeel.THEME_DARK
import net.newpipe.app.viewmodel.settings.lookfeel.THEME_LIGHT
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.caption_setting_description
import newpipe.shared.generated.resources.caption_setting_title
import newpipe.shared.generated.resources.list_view_mode_auto
import newpipe.shared.generated.resources.list_view_mode_card
import newpipe.shared.generated.resources.list_view_mode_grid
import newpipe.shared.generated.resources.list_view_mode_list
import newpipe.shared.generated.resources.list_view_mode_title
import newpipe.shared.generated.resources.main_tabs_position_summary
import newpipe.shared.generated.resources.main_tabs_position_title
import newpipe.shared.generated.resources.night_theme_available
import newpipe.shared.generated.resources.night_theme_summary
import newpipe.shared.generated.resources.night_theme_title
import newpipe.shared.generated.resources.settings_category_appearance_title
import newpipe.shared.generated.resources.show_hold_to_append_summary
import newpipe.shared.generated.resources.show_hold_to_append_title
import newpipe.shared.generated.resources.tablet_mode_auto
import newpipe.shared.generated.resources.tablet_mode_off
import newpipe.shared.generated.resources.tablet_mode_on
import newpipe.shared.generated.resources.tablet_mode_title
import newpipe.shared.generated.resources.theme_auto
import newpipe.shared.generated.resources.theme_black
import newpipe.shared.generated.resources.theme_dark
import newpipe.shared.generated.resources.theme_light
import newpipe.shared.generated.resources.theme_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LookFeelSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: LookFeelSettingsViewModel = koinViewModel()
) {
    val theme by viewModel.theme.collectAsState()
    val nightTheme by viewModel.nightTheme.collectAsState()
    val showHoldToAppend by viewModel.showHoldToAppend.collectAsState()
    val tabletMode by viewModel.tabletMode.collectAsState()
    val listViewMode by viewModel.listViewMode.collectAsState()
    val mainTabsPosition by viewModel.mainTabsPosition.collectAsState()

    LookFeelSettingsContent(
        theme = theme,
        nightTheme = nightTheme,
        showHoldToAppend = showHoldToAppend,
        tabletMode = tabletMode,
        listViewMode = listViewMode,
        mainTabsPosition = mainTabsPosition,
        onNavigateUp = navigator::navigateUp,
        onSetTheme = viewModel::setTheme,
        onSetNightTheme = viewModel::setNightTheme,
        onToggleShowHoldToAppend = viewModel::toggleShowHoldToAppend,
        onSetTabletMode = viewModel::setTabletMode,
        onSetListViewMode = viewModel::setListViewMode,
        onOpenCaptionSettings = viewModel::openCaptionSettings,
        onToggleMainTabsPosition = viewModel::toggleMainTabsPosition
    )
}

@Composable
private fun LookFeelSettingsContent(
    theme: String,
    nightTheme: String,
    showHoldToAppend: Boolean,
    tabletMode: String,
    listViewMode: String,
    mainTabsPosition: Boolean,
    onNavigateUp: () -> Unit,
    onSetTheme: (String) -> Unit,
    onSetNightTheme: (String) -> Unit,
    onToggleShowHoldToAppend: (Boolean) -> Unit,
    onSetTabletMode: (String) -> Unit,
    onSetListViewMode: (String) -> Unit,
    onOpenCaptionSettings: () -> Unit,
    onToggleMainTabsPosition: (Boolean) -> Unit
) {
    val themeOptions = themeOptions()
    val nightThemeOptions = nightThemeOptions()
    val tabletOptions = tabletModeOptions()
    val listViewOptions = listViewModeOptions()
    val nightThemeEnabled = theme == THEME_AUTO
    val autoLabel = stringResource(Res.string.theme_auto)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_appearance_title),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            ListPreference(
                title = stringResource(Res.string.theme_title),
                options = themeOptions,
                selectedValue = theme,
                onValueChange = onSetTheme
            )
            ListPreference(
                title = stringResource(Res.string.night_theme_title),
                options = nightThemeOptions,
                selectedValue = nightTheme,
                onValueChange = onSetNightTheme,
                enabled = nightThemeEnabled
            )
            if (!nightThemeEnabled) {
                // Disabled-row summary parity with legacy.
                TextPreference(
                    title = "",
                    summary = stringResource(Res.string.night_theme_available, autoLabel),
                    onClick = {},
                    enabled = false
                )
            } else {
                TextPreference(
                    title = "",
                    summary = stringResource(Res.string.night_theme_summary),
                    onClick = {},
                    enabled = false
                )
            }
            SwitchPreference(
                title = stringResource(Res.string.show_hold_to_append_title),
                summary = stringResource(Res.string.show_hold_to_append_summary),
                isChecked = showHoldToAppend,
                onCheckedChange = onToggleShowHoldToAppend
            )
            ListPreference(
                title = stringResource(Res.string.tablet_mode_title),
                options = tabletOptions,
                selectedValue = tabletMode,
                onValueChange = onSetTabletMode
            )
            ListPreference(
                title = stringResource(Res.string.list_view_mode_title),
                options = listViewOptions,
                selectedValue = listViewMode,
                onValueChange = onSetListViewMode
            )
            TextPreference(
                title = stringResource(Res.string.caption_setting_title),
                summary = stringResource(Res.string.caption_setting_description),
                onClick = onOpenCaptionSettings
            )
            SwitchPreference(
                title = stringResource(Res.string.main_tabs_position_title),
                summary = stringResource(Res.string.main_tabs_position_summary),
                isChecked = mainTabsPosition,
                onCheckedChange = onToggleMainTabsPosition
            )
        }
    }
}

@Composable
private fun themeOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(THEME_LIGHT, stringResource(Res.string.theme_light)),
    ListPreferenceOption(THEME_DARK, stringResource(Res.string.theme_dark)),
    ListPreferenceOption(THEME_BLACK, stringResource(Res.string.theme_black)),
    ListPreferenceOption(THEME_AUTO, stringResource(Res.string.theme_auto))
)

@Composable
private fun nightThemeOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(THEME_DARK, stringResource(Res.string.theme_dark)),
    ListPreferenceOption(THEME_BLACK, stringResource(Res.string.theme_black))
)

@Composable
private fun tabletModeOptions(): List<ListPreferenceOption> = listOf(
    ListPreferenceOption(TABLET_AUTO, stringResource(Res.string.tablet_mode_auto)),
    ListPreferenceOption(TABLET_ON, stringResource(Res.string.tablet_mode_on)),
    ListPreferenceOption(TABLET_OFF, stringResource(Res.string.tablet_mode_off))
)

@Composable
private fun listViewModeOptions(): List<ListPreferenceOption> =
    listOf(
        ListPreferenceOption(LIST_VIEW_AUTO, stringResource(Res.string.list_view_mode_auto)),
        ListPreferenceOption(LIST_VIEW_LIST, stringResource(Res.string.list_view_mode_list)),
        ListPreferenceOption(LIST_VIEW_GRID, stringResource(Res.string.list_view_mode_grid)),
        ListPreferenceOption(LIST_VIEW_CARD, stringResource(Res.string.list_view_mode_card))
    )

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun LookFeelSettingsScreenPreview() {
    LookFeelSettingsContent(
        theme = THEME_AUTO,
        nightTheme = THEME_DARK,
        showHoldToAppend = true,
        tabletMode = TABLET_AUTO,
        listViewMode = LIST_VIEW_AUTO,
        mainTabsPosition = false,
        onNavigateUp = {},
        onSetTheme = {},
        onSetNightTheme = {},
        onToggleShowHoldToAppend = {},
        onSetTabletMode = {},
        onSetListViewMode = {},
        onOpenCaptionSettings = {},
        onToggleMainTabsPosition = {}
    )
}