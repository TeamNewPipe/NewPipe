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

@Composable
fun SettingsNavigation(onExitSettings: () -> Unit) {
    val backStack = rememberNavBackStack(SettingsHome)

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
            entry<SettingsHome> { SettingsHomeScreen(backStack, handleBack) }
            entry<PlayerSettings> { Text(stringResource(id = R.string.settings_category_player_title)) }
            entry<BehaviourSettings> { Text(stringResource(id = R.string.settings_category_player_behavior_title)) }
            entry<DownloadSettings> { Text(stringResource(id = R.string.settings_category_downloads_title)) }
            entry<LookFeelSettings> { Text(stringResource(id = R.string.settings_category_look_and_feel_title)) }
            entry<HistoryCacheSettings> { Text(stringResource(id = R.string.settings_category_history_title)) }
            entry<ContentSettings> { Text(stringResource(id = R.string.settings_category_content_title)) }
            entry<FeedSettings> { Text(stringResource(id = R.string.settings_category_feed_title)) }
            entry<ServicesSettings> { Text(stringResource(id = R.string.settings_category_services_title)) }
            entry<LanguageSettings> { Text(stringResource(id = R.string.settings_category_language_title)) }
            entry<BackupRestoreSettings> { Text(stringResource(id = R.string.settings_category_backup_restore_title)) }
            entry<UpdateSettings> { Text(stringResource(id = R.string.settings_category_updates_title)) }
            entry<DebugSettings> { DebugScreen(backStack) }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        )
    )
}
