/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.composable.ConfirmDialog
import net.newpipe.app.composable.PreferenceCategoryHeader
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TextPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.history.HistorySettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.cancel
import newpipe.shared.generated.resources.clear_cookie_summary
import newpipe.shared.generated.resources.clear_cookie_title
import newpipe.shared.generated.resources.clear_playback_states_summary
import newpipe.shared.generated.resources.clear_playback_states_title
import newpipe.shared.generated.resources.clear_search_history_summary
import newpipe.shared.generated.resources.clear_search_history_title
import newpipe.shared.generated.resources.clear_views_history_summary
import newpipe.shared.generated.resources.clear_views_history_title
import newpipe.shared.generated.resources.delete
import newpipe.shared.generated.resources.enable_playback_resume_summary
import newpipe.shared.generated.resources.enable_playback_resume_title
import newpipe.shared.generated.resources.enable_playback_state_lists_summary
import newpipe.shared.generated.resources.enable_playback_state_lists_title
import newpipe.shared.generated.resources.enable_search_history_summary
import newpipe.shared.generated.resources.enable_search_history_title
import newpipe.shared.generated.resources.enable_watch_history_summary
import newpipe.shared.generated.resources.enable_watch_history_title
import newpipe.shared.generated.resources.metadata_cache_wipe_summary
import newpipe.shared.generated.resources.metadata_cache_wipe_title
import newpipe.shared.generated.resources.settings_category_clear_data_title
import newpipe.shared.generated.resources.settings_category_history_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class PendingDelete { WATCH, PLAYBACK_STATES, SEARCH }

@Composable
fun HistorySettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: HistorySettingsViewModel = koinViewModel()
) {
    val watchHistory by viewModel.watchHistory.collectAsState()
    val playbackResume by viewModel.playbackResume.collectAsState()
    val playbackStateLists by viewModel.playbackStateLists.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val hasCookies by viewModel.hasRecaptchaCookies.collectAsState()

    HistorySettingsContent(
        watchHistory = watchHistory,
        playbackResume = playbackResume,
        playbackStateLists = playbackStateLists,
        searchHistory = searchHistory,
        hasRecaptchaCookies = hasCookies,
        onNavigateUp = navigator::navigateUp,
        onToggleWatchHistory = viewModel::toggleWatchHistory,
        onTogglePlaybackResume = viewModel::togglePlaybackResume,
        onTogglePlaybackStateLists = viewModel::togglePlaybackStateLists,
        onToggleSearchHistory = viewModel::toggleSearchHistory,
        onWipeMetadataCache = viewModel::wipeMetadataCache,
        onDeleteWatchHistory = viewModel::deleteWatchHistory,
        onDeletePlaybackStates = viewModel::deletePlaybackStates,
        onDeleteSearchHistory = viewModel::deleteSearchHistory,
        onClearCookies = viewModel::clearRecaptchaCookies
    )
}

@Composable
private fun HistorySettingsContent(
    watchHistory: Boolean,
    playbackResume: Boolean,
    playbackStateLists: Boolean,
    searchHistory: Boolean,
    hasRecaptchaCookies: Boolean,
    onNavigateUp: () -> Unit,
    onToggleWatchHistory: (Boolean) -> Unit,
    onTogglePlaybackResume: (Boolean) -> Unit,
    onTogglePlaybackStateLists: (Boolean) -> Unit,
    onToggleSearchHistory: (Boolean) -> Unit,
    onWipeMetadataCache: () -> Unit,
    onDeleteWatchHistory: () -> Unit,
    onDeletePlaybackStates: () -> Unit,
    onDeleteSearchHistory: () -> Unit,
    onClearCookies: () -> Unit
) {
    var pending by rememberSaveable { mutableStateOf<PendingDelete?>(null)
    }

    pending?.let { which ->
        val (title, message, action) = when (which) {
            PendingDelete.WATCH -> Triple(
                stringResource(Res.string.clear_views_history_title),
                stringResource(Res.string.clear_views_history_summary),
                onDeleteWatchHistory
            )
            PendingDelete.PLAYBACK_STATES -> Triple(
                stringResource(Res.string.clear_playback_states_title),
                stringResource(Res.string.clear_playback_states_summary),
                onDeletePlaybackStates
            )
            PendingDelete.SEARCH -> Triple(
                stringResource(Res.string.clear_search_history_title),
                stringResource(Res.string.clear_search_history_summary),
                onDeleteSearchHistory
            )
        }
        ConfirmDialog(
            title = title,
            message = message,
            confirmLabel = stringResource(Res.string.delete),
            dismissLabel = stringResource(Res.string.cancel),
            onConfirm = {
                action()
                pending = null
            },
            onDismiss = { pending = null }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_history_title),
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
                title = stringResource(Res.string.enable_watch_history_title),
                summary = stringResource(Res.string.enable_watch_history_summary),
                isChecked = watchHistory,
                onCheckedChange = onToggleWatchHistory
            )
            SwitchPreference(
                title = stringResource(Res.string.enable_playback_resume_title),
                summary = stringResource(Res.string.enable_playback_resume_summary),
                isChecked = playbackResume,
                onCheckedChange = onTogglePlaybackResume,
                enabled = watchHistory
            )
            SwitchPreference(
                title = stringResource(Res.string.enable_playback_state_lists_title),
                summary = stringResource(Res.string.enable_playback_state_lists_summary),
                isChecked = playbackStateLists,
                onCheckedChange = onTogglePlaybackStateLists,
                enabled = watchHistory
            )
            SwitchPreference(
                title = stringResource(Res.string.enable_search_history_title),
                summary = stringResource(Res.string.enable_search_history_summary),
                isChecked = searchHistory,
                onCheckedChange = onToggleSearchHistory
            )

            PreferenceCategoryHeader(
                title = stringResource(Res.string.settings_category_clear_data_title)
            )

            TextPreference(
                title = stringResource(Res.string.metadata_cache_wipe_title),
                summary = stringResource(Res.string.metadata_cache_wipe_summary),
                onClick = onWipeMetadataCache
            )
            TextPreference(
                title = stringResource(Res.string.clear_views_history_title),
                summary = stringResource(Res.string.clear_views_history_summary),
                onClick = { pending = PendingDelete.WATCH }
            )
            TextPreference(
                title = stringResource(Res.string.clear_playback_states_title),
                summary = stringResource(Res.string.clear_playback_states_summary),
                onClick = { pending = PendingDelete.PLAYBACK_STATES }
            )
            TextPreference(
                title = stringResource(Res.string.clear_search_history_title),
                summary = stringResource(Res.string.clear_search_history_summary),
                onClick = { pending = PendingDelete.SEARCH }
            )
            TextPreference(
                title = stringResource(Res.string.clear_cookie_title),
                summary = stringResource(Res.string.clear_cookie_summary),
                onClick = onClearCookies,
                enabled = hasRecaptchaCookies
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun HistorySettingsScreenPreview() {
    HistorySettingsContent(
        watchHistory = true,
        playbackResume = true,
        playbackStateLists = false,
        searchHistory = true,
        hasRecaptchaCookies = true,
        onNavigateUp = {},
        onToggleWatchHistory = {},
        onTogglePlaybackResume = {},
        onTogglePlaybackStateLists = {},
        onToggleSearchHistory = {},
        onWipeMetadataCache = {},
        onDeleteWatchHistory = {},
        onDeletePlaybackStates = {},
        onDeleteSearchHistory = {},
        onClearCookies = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun HistorySettingsScreenWatchHistoryOffPreview() {
    HistorySettingsContent(
        watchHistory = false,
        playbackResume = true,
        playbackStateLists = false,
        searchHistory = true,
        hasRecaptchaCookies = false,
        onNavigateUp = {},
        onToggleWatchHistory = {},
        onTogglePlaybackResume = {},
        onTogglePlaybackStateLists = {},
        onToggleSearchHistory = {},
        onWipeMetadataCache = {},
        onDeleteWatchHistory = {},
        onDeletePlaybackStates = {},
        onDeleteSearchHistory = {},
        onClearCookies = {}
    )
}