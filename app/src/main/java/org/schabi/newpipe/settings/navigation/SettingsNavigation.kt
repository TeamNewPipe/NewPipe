/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.screens.DebugScreen
import org.schabi.newpipe.settings.screens.SettingsHomeScreen
import org.schabi.newpipe.ui.screens.Screens

@Composable
fun SettingsNavigation(onExitSettings: () -> Unit) {
    val backStack = rememberNavBackStack(Screens.Settings.Home)

    val handleBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            onExitSettings()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = handleBack,
        entryProvider = entryProvider {
            entry<Screens.Settings.Home> { SettingsHomeScreen(backStack, handleBack) }
            entry<Screens.Settings.Player> { Text(stringResource(id = R.string.settings_category_player_title)) }
            entry<Screens.Settings.Behaviour> { Text(stringResource(id = R.string.settings_category_player_behavior_title)) }
            entry<Screens.Settings.Download> { Text(stringResource(id = R.string.settings_category_downloads_title)) }
            entry<Screens.Settings.LookFeel> { Text(stringResource(id = R.string.settings_category_look_and_feel_title)) }
            entry<Screens.Settings.HistoryCache> { Text(stringResource(id = R.string.settings_category_history_title)) }
            entry<Screens.Settings.Content> { Text(stringResource(id = R.string.settings_category_content_title)) }
            entry<Screens.Settings.Feed> { Text(stringResource(id = R.string.settings_category_feed_title)) }
            entry<Screens.Settings.Services> { Text(stringResource(id = R.string.settings_category_services_title)) }
            entry<Screens.Settings.Language> { Text(stringResource(id = R.string.settings_category_language_title)) }
            entry<Screens.Settings.BackupRestore> { Text(stringResource(id = R.string.settings_category_backup_restore_title)) }
            entry<Screens.Settings.Updates> { Text(stringResource(id = R.string.settings_category_updates_title)) }
            entry<Screens.Settings.Debug> { DebugScreen(backStack) }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        )
    )
}
