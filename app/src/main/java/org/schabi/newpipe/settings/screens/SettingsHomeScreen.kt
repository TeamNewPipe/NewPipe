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
import org.schabi.newpipe.settings.navigation.BackupRestoreSettings
import org.schabi.newpipe.settings.navigation.BehaviourSettings
import org.schabi.newpipe.settings.navigation.ContentSettings
import org.schabi.newpipe.settings.navigation.DebugSettings
import org.schabi.newpipe.settings.navigation.DownloadSettings
import org.schabi.newpipe.settings.navigation.FeedSettings
import org.schabi.newpipe.settings.navigation.HistoryCacheSettings
import org.schabi.newpipe.settings.navigation.LanguageSettings
import org.schabi.newpipe.settings.navigation.LookFeelSettings
import org.schabi.newpipe.settings.navigation.PlayerSettings
import org.schabi.newpipe.settings.navigation.ServicesSettings
import org.schabi.newpipe.settings.navigation.UpdateSettings
import org.schabi.newpipe.ui.TextPreference
import org.schabi.newpipe.ui.components.common.ScaffoldWithToolbar

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
                    onClick = { backStack.add(PlayerSettings) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_player_behavior_title,
                    icon = R.drawable.ic_settings,
                    onClick = { backStack.add(BehaviourSettings) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_downloads_title,
                    icon = R.drawable.ic_file_download,
                    onClick = { backStack.add(DownloadSettings) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_look_and_feel_title,
                    icon = R.drawable.ic_palette,
                    onClick = { backStack.add(LookFeelSettings) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_history_title,
                    icon = R.drawable.ic_history,
                    onClick = { backStack.add(HistoryCacheSettings) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_content_title,
                    icon = R.drawable.ic_tv,
                    onClick = { backStack.add(ContentSettings) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_feed_title,
                    icon = R.drawable.ic_rss_feed,
                    onClick = { backStack.add(FeedSettings) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_services_title,
                    icon = R.drawable.ic_subscriptions,
                    onClick = { backStack.add(ServicesSettings) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_language_title,
                    icon = R.drawable.ic_language,
                    onClick = { backStack.add(LanguageSettings) }
                )
            }
            item {
                TextPreference(
                    title = R.string.settings_category_backup_restore_title,
                    icon = R.drawable.ic_backup,
                    onClick = { backStack.add(BackupRestoreSettings) }
                )
            }
            // Show Updates only on release builds
            if (!BuildConfig.DEBUG) {
                item {
                    TextPreference(
                        title = R.string.settings_category_updates_title,
                        icon = R.drawable.ic_newpipe_update,
                        onClick = { backStack.add(UpdateSettings) }
                    )
                }
            }
            // Show Debug only on debug builds
            if (BuildConfig.DEBUG) {
                item {
                    TextPreference(
                        title = R.string.settings_category_debug_title,
                        icon = R.drawable.ic_bug_report,
                        onClick = { backStack.add(DebugSettings) }
                    )
                }
            }
        }
    }
}
