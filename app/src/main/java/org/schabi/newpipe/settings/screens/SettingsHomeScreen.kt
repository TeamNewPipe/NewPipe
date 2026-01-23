/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.TextPreference
import org.schabi.newpipe.ui.components.common.ScaffoldWithToolbar
import org.schabi.newpipe.ui.screens.Screens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(backStack: NavBackStack<NavKey>, handleBack: () -> Unit) {
    ScaffoldWithToolbar(
        title = stringResource(id = R.string.settings),
        onBackClick = {
            handleBack()
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                TextPreference(
                    title = R.string.settings_category_player_title,
                    icon = R.drawable.ic_play_arrow,
                    onClick = { backStack.add(Screens.Settings.Player) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_player_behavior_title,
                    icon = R.drawable.ic_settings,
                    onClick = { backStack.add(Screens.Settings.Behaviour) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_downloads_title,
                    icon = R.drawable.ic_file_download,
                    onClick = { backStack.add(Screens.Settings.Download) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_look_and_feel_title,
                    icon = R.drawable.ic_palette,
                    onClick = { backStack.add(Screens.Settings.LookFeel) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_history_title,
                    icon = R.drawable.ic_history,
                    onClick = { backStack.add(Screens.Settings.HistoryCache) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_content_title,
                    icon = R.drawable.ic_tv,
                    onClick = { backStack.add(Screens.Settings.Content) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_feed_title,
                    icon = R.drawable.ic_rss_feed,
                    onClick = { backStack.add(Screens.Settings.Feed) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_services_title,
                    icon = R.drawable.ic_subscriptions,
                    onClick = { backStack.add(Screens.Settings.Services) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_language_title,
                    icon = R.drawable.ic_language,
                    onClick = { backStack.add(Screens.Settings.Language) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_backup_restore_title,
                    icon = R.drawable.ic_backup,
                    onClick = { backStack.add(Screens.Settings.BackupRestore) }
                )
            }
            // Show Updates only on release builds
            if (!BuildConfig.DEBUG) {
                item {
                    TextPreference(
                        title = R.string.settings_category_updates_title,
                        icon = R.drawable.ic_newpipe_update,
                        onClick = { backStack.add(Screens.Settings.Updates) }
                    )
                }
            }
            // Show Debug only on debug builds
            if (BuildConfig.DEBUG) {
                item {
                    TextPreference(
                        title = R.string.settings_category_debug_title,
                        icon = R.drawable.ic_bug_report,
                        onClick = { backStack.add(Screens.Settings.Debug) }
                    )
                }
            }
        }
    }
}
