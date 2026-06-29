package net.newpipe.app.screen.settings.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.BuildConfig
import net.newpipe.app.composable.TextPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_backup
import newpipe.shared.generated.resources.ic_bug_report
import newpipe.shared.generated.resources.ic_file_download
import newpipe.shared.generated.resources.ic_history
import newpipe.shared.generated.resources.ic_language
import newpipe.shared.generated.resources.ic_newpipe_update
import newpipe.shared.generated.resources.ic_palette
import newpipe.shared.generated.resources.ic_play_arrow
import newpipe.shared.generated.resources.ic_rss_feed
import newpipe.shared.generated.resources.ic_settings
import newpipe.shared.generated.resources.ic_subscriptions
import newpipe.shared.generated.resources.ic_tv
import newpipe.shared.generated.resources.settings
import newpipe.shared.generated.resources.settings_category_backup_restore_title
import newpipe.shared.generated.resources.settings_category_content_title
import newpipe.shared.generated.resources.settings_category_debug_title
import newpipe.shared.generated.resources.settings_category_downloads_title
import newpipe.shared.generated.resources.settings_category_feed_title
import newpipe.shared.generated.resources.settings_category_history_title
import newpipe.shared.generated.resources.settings_category_language_title
import newpipe.shared.generated.resources.settings_category_look_and_feel_title
import newpipe.shared.generated.resources.settings_category_player_behavior_title
import newpipe.shared.generated.resources.settings_category_player_title
import newpipe.shared.generated.resources.settings_category_services_title
import newpipe.shared.generated.resources.settings_category_updates_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * One row on the Settings home screen.
 *
 * @param destination Where tapping the row navigates to. Used as theLazyColumn
 *   key — must be unique within the rendered list.
 */
internal data class SettingsHomeRow(
    val title: StringResource,
    val icon: DrawableResource,
    val destination: Destination.Settings
)

@Composable
fun SettingsHomeScreen(navigator: Navigator = koinInject()) {
    val rows = remember { defaultSettingsHomeRows(isDebugBuild = BuildConfig.DEBUG) }
    SettingsHomeScreenContent(
        rows = rows,
        onNavigateUp = navigator::navigateUp,
        onNavigateTo = navigator::navigateTo
    )
}

@Composable
private fun SettingsHomeScreenContent(
    rows: List<SettingsHomeRow>,
    onNavigateUp: () -> Unit,
    onNavigateTo: (Destination.Settings) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(items = rows, key = { it.destination.toString() }) { row
                -> TextPreference(
                    title = stringResource(row.title),
                    icon = painterResource(row.icon),
                    onClick = { onNavigateTo(row.destination) }
                )
            }
        }
    }
}

/**
 * Default rows shown on the Settings home screen.
 *
 * Updates is omitted in debug builds (in-app updates ship only viarelease
 * APKs); Debug is added only in debug builds.
 */
private fun defaultSettingsHomeRows(isDebugBuild: Boolean):
        List<SettingsHomeRow> = buildList {
    add(SettingsHomeRow(
        Res.string.settings_category_player_title,
        Res.drawable.ic_play_arrow,
        Destination.Settings.Player
    ))
    add(SettingsHomeRow(
        Res.string.settings_category_player_behavior_title,
        Res.drawable.ic_settings,
        Destination.Settings.Behaviour
    ))
    add(SettingsHomeRow(
        Res.string.settings_category_downloads_title,
        Res.drawable.ic_file_download,
        Destination.Settings.Download
    ))
    add(SettingsHomeRow(
        Res.string.settings_category_look_and_feel_title,
        Res.drawable.ic_palette,
        Destination.Settings.LookFeel
    ))
    add(SettingsHomeRow(
        Res.string.settings_category_history_title,
        Res.drawable.ic_history,
        Destination.Settings.HistoryCache
    ))
    add(SettingsHomeRow(
        Res.string.settings_category_content_title,
        Res.drawable.ic_tv,
        Destination.Settings.Content
    ))
    add(SettingsHomeRow(
        Res.string.settings_category_feed_title,
        Res.drawable.ic_rss_feed,
        Destination.Settings.Feed
    ))
    add(SettingsHomeRow(
        Res.string.settings_category_services_title,
        Res.drawable.ic_subscriptions,
        Destination.Settings.Services
    ))
    add(SettingsHomeRow(
        Res.string.settings_category_language_title,
        Res.drawable.ic_language,
        Destination.Settings.Language
    ))
    add(SettingsHomeRow(
        Res.string.settings_category_backup_restore_title,
        Res.drawable.ic_backup,
        Destination.Settings.BackupRestore
    ))
    if (!isDebugBuild) {
        add(SettingsHomeRow(
            Res.string.settings_category_updates_title,
            Res.drawable.ic_newpipe_update,
            Destination.Settings.Updates
        ))
    }
    if (isDebugBuild) {
        add(SettingsHomeRow(
            Res.string.settings_category_debug_title,
            Res.drawable.ic_bug_report,
            Destination.Settings.Debug
        ))
    }
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun SettingsHomeScreenPreview() {
    SettingsHomeScreenContent(
        rows = defaultSettingsHomeRows(isDebugBuild = false),
        onNavigateUp = {},
        onNavigateTo = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun SettingsHomeScreenDebugBuildPreview() {
    SettingsHomeScreenContent(
        rows = defaultSettingsHomeRows(isDebugBuild = true),
        onNavigateUp = {},
        onNavigateTo = {}
    )
}