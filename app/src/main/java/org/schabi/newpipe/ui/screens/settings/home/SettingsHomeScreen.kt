/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui.screens.settings.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.navigation.Screen
import org.schabi.newpipe.ui.TextPreference
import org.schabi.newpipe.ui.components.common.ScaffoldWithToolbar

@Composable
fun SettingsHomeScreen(
    onNavigate: (Screen.Settings) -> Unit,
    onBackClick: () -> Unit
) {
    ScaffoldWithToolbar(
        title = stringResource(id = R.string.settings),
        onBackClick = onBackClick
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                TextPreference(
                    title = stringResource(R.string.settings_category_player_title),
                    icon = R.drawable.ic_play_arrow,
                    onClick = { onNavigate(Screen.Settings.Player) }
                )
            }
            item {
                TextPreference(
                    title = stringResource(R.string.settings_category_player_behavior_title),
                    icon = R.drawable.ic_settings,
                    onClick = { onNavigate(Screen.Settings.Behaviour) }
                )
            }
            item {
                TextPreference(
                    title = stringResource(R.string.settings_category_downloads_title),
                    icon = R.drawable.ic_file_download,
                    onClick = { onNavigate(Screen.Settings.Download) }
                )
            }
            item {
                TextPreference(
                    title = stringResource(R.string.settings_category_look_and_feel_title),
                    icon = R.drawable.ic_palette,
                    onClick = { onNavigate(Screen.Settings.LookFeel) }
                )
            }
            item {
                TextPreference(
                    title = stringResource(R.string.settings_category_history_title),
                    icon = R.drawable.ic_history,
                    onClick = { onNavigate(Screen.Settings.HistoryCache) }
                )
            }
            item {
                TextPreference(
                    title = stringResource(R.string.settings_category_content_title),
                    icon = R.drawable.ic_tv,
                    onClick = { onNavigate(Screen.Settings.Content) }
                )
            }
            item {
                TextPreference(
                    title = stringResource(R.string.settings_category_feed_title),
                    icon = R.drawable.ic_rss_feed,
                    onClick = { onNavigate(Screen.Settings.Feed) }
                )
            }
            item {
                TextPreference(
                    title = stringResource(R.string.settings_category_services_title),
                    icon = R.drawable.ic_subscriptions,
                    onClick = { onNavigate(Screen.Settings.Services) }
                )
            }
            item {
                TextPreference(
                    title = stringResource(R.string.settings_category_language_title),
                    icon = R.drawable.ic_language,
                    onClick = { onNavigate(Screen.Settings.Language) }
                )
            }
            item {
                TextPreference(
                    title = stringResource(R.string.settings_category_backup_restore_title),
                    icon = R.drawable.ic_backup,
                    onClick = { onNavigate(Screen.Settings.BackupRestore) }
                )
            }
            // Show Updates only on release builds
            if (!BuildConfig.DEBUG) {
                item {
                    TextPreference(
                        title = stringResource(R.string.settings_category_updates_title),
                        icon = R.drawable.ic_newpipe_update,
                        onClick = { onNavigate(Screen.Settings.Updates) }
                    )
                }
            }
            // Show Debug only on debug builds
            if (BuildConfig.DEBUG) {
                item {
                    TextPreference(
                        title = stringResource(R.string.settings_category_debug_title),
                        icon = R.drawable.ic_bug_report,
                        onClick = { onNavigate(Screen.Settings.Debug) }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SettingsHomeScreenPreview() = SettingsHomeScreen(onNavigate = {}, onBackClick = {})
