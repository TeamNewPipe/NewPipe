/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preferences.HistoryPreferences.DEFAULT_ENABLE_PLAYBACK_RESUME
import net.newpipe.app.preferences.HistoryPreferences.DEFAULT_ENABLE_PLAYBACK_STATE_LISTS
import net.newpipe.app.preferences.HistoryPreferences.DEFAULT_ENABLE_SEARCH_HISTORY
import net.newpipe.app.preferences.HistoryPreferences.DEFAULT_ENABLE_WATCH_HISTORY
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.screen.settings.model.HistoryMessage
import net.newpipe.app.viewmodel.settings.HistorySettingsViewModel
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
import newpipe.shared.generated.resources.delete_playback_states_alert
import newpipe.shared.generated.resources.delete_search_history_alert
import newpipe.shared.generated.resources.delete_view_history_alert
import newpipe.shared.generated.resources.enable_playback_resume_summary
import newpipe.shared.generated.resources.enable_playback_resume_title
import newpipe.shared.generated.resources.enable_playback_state_lists_summary
import newpipe.shared.generated.resources.enable_playback_state_lists_title
import newpipe.shared.generated.resources.enable_search_history_summary
import newpipe.shared.generated.resources.enable_search_history_title
import newpipe.shared.generated.resources.enable_watch_history_summary
import newpipe.shared.generated.resources.enable_watch_history_title
import newpipe.shared.generated.resources.metadata_cache_wipe_complete_notice
import newpipe.shared.generated.resources.metadata_cache_wipe_summary
import newpipe.shared.generated.resources.metadata_cache_wipe_title
import newpipe.shared.generated.resources.recaptcha_cookies_cleared
import newpipe.shared.generated.resources.search_history_deleted
import newpipe.shared.generated.resources.settings_category_clear_data_title
import newpipe.shared.generated.resources.settings_category_history_title
import newpipe.shared.generated.resources.watch_history_deleted
import newpipe.shared.generated.resources.watch_history_states_deleted
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

enum class HistoryClearTarget {
    WATCH_HISTORY,
    PLAYBACK_STATES,
    SEARCH_HISTORY
}

@Composable
fun HistorySettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: HistorySettingsViewModel = koinViewModel()
) {
    val watchHistoryEnabled by viewModel.watchHistoryEnabled.collectAsStateWithLifecycle()
    val playbackResumeEnabled by viewModel.playbackResumeEnabled.collectAsStateWithLifecycle()
    val playbackStateListsEnabled by viewModel.playbackStateListsEnabled
        .collectAsStateWithLifecycle()
    val searchHistoryEnabled by viewModel.searchHistoryEnabled.collectAsStateWithLifecycle()
    val recaptchaCookiesPresent by viewModel.recaptchaCookiesPresent
        .collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var pendingClear by remember { mutableStateOf<HistoryClearTarget?>(null) }

    HistorySettingsScreenContent(
        watchHistoryEnabled = watchHistoryEnabled,
        playbackResumeEnabled = playbackResumeEnabled,
        playbackStateListsEnabled = playbackStateListsEnabled,
        searchHistoryEnabled = searchHistoryEnabled,
        recaptchaCookiesPresent = recaptchaCookiesPresent,
        message = message,
        pendingClear = pendingClear,
        onWatchHistoryEnabledChange = viewModel::setWatchHistoryEnabled,
        onPlaybackResumeEnabledChange = viewModel::setPlaybackResumeEnabled,
        onPlaybackStateListsEnabledChange = viewModel::setPlaybackStateListsEnabled,
        onSearchHistoryEnabledChange = viewModel::setSearchHistoryEnabled,
        onWipeMetadataCache = viewModel::wipeMetadataCache,
        onRequestClear = { target -> pendingClear = target },
        onConfirmClear = { target ->
            when (target) {
                HistoryClearTarget.WATCH_HISTORY -> viewModel.clearWatchHistory()
                HistoryClearTarget.PLAYBACK_STATES -> viewModel.clearPlaybackStates()
                HistoryClearTarget.SEARCH_HISTORY -> viewModel.clearSearchHistory()
            }
        },
        onDismissClear = { pendingClear = null },
        onClearRecaptchaCookies = viewModel::clearRecaptchaCookies,
        onMessageShown = viewModel::onMessageShown,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun HistorySettingsScreenContent(
    watchHistoryEnabled: Boolean = DEFAULT_ENABLE_WATCH_HISTORY,
    playbackResumeEnabled: Boolean = DEFAULT_ENABLE_PLAYBACK_RESUME,
    playbackStateListsEnabled: Boolean = DEFAULT_ENABLE_PLAYBACK_STATE_LISTS,
    searchHistoryEnabled: Boolean = DEFAULT_ENABLE_SEARCH_HISTORY,
    recaptchaCookiesPresent: Boolean = false,
    message: HistoryMessage? = null,
    pendingClear: HistoryClearTarget? = null,
    onWatchHistoryEnabledChange: (Boolean) -> Unit = {},
    onPlaybackResumeEnabledChange: (Boolean) -> Unit = {},
    onPlaybackStateListsEnabledChange: (Boolean) -> Unit = {},
    onSearchHistoryEnabledChange: (Boolean) -> Unit = {},
    onWipeMetadataCache: () -> Unit = {},
    onRequestClear: (HistoryClearTarget) -> Unit = {},
    onConfirmClear: (HistoryClearTarget) -> Unit = {},
    onDismissClear: () -> Unit = {},
    onClearRecaptchaCookies: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val messageText = message?.let { pending ->
        when (pending) {
            HistoryMessage.METADATA_CACHE_WIPED -> {
                stringResource(Res.string.metadata_cache_wipe_complete_notice)
            }

            HistoryMessage.WATCH_HISTORY_DELETED -> {
                stringResource(Res.string.watch_history_deleted)
            }

            HistoryMessage.PLAYBACK_STATES_DELETED -> {
                stringResource(Res.string.watch_history_states_deleted)
            }

            HistoryMessage.SEARCH_HISTORY_DELETED -> {
                stringResource(Res.string.search_history_deleted)
            }

            HistoryMessage.RECAPTCHA_COOKIES_CLEARED -> {
                stringResource(Res.string.recaptcha_cookies_cleared)
            }
        }
    }

    LaunchedEffect(message) {
        if (messageText == null) {
            return@LaunchedEffect
        }
        // Showing the snackbar from the surrounding scope instead of this effect, so that marking
        // the message as seen below cannot cancel it by restarting the effect
        scope.launch { snackbarHostState.showSnackbar(messageText) }
        onMessageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_history_title),
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
            SwitchPreference(
                title = stringResource(Res.string.enable_watch_history_title),
                summary = stringResource(Res.string.enable_watch_history_summary),
                checked = watchHistoryEnabled,
                onCheckedChange = onWatchHistoryEnabledChange
            )

            SwitchPreference(
                title = stringResource(Res.string.enable_playback_resume_title),
                summary = stringResource(Res.string.enable_playback_resume_summary),
                checked = playbackResumeEnabled,
                onCheckedChange = onPlaybackResumeEnabledChange,
                enabled = watchHistoryEnabled
            )
            SwitchPreference(
                title = stringResource(Res.string.enable_playback_state_lists_title),
                summary = stringResource(Res.string.enable_playback_state_lists_summary),
                checked = playbackStateListsEnabled,
                onCheckedChange = onPlaybackStateListsEnabledChange,
                enabled = watchHistoryEnabled
            )
            SwitchPreference(
                title = stringResource(Res.string.enable_search_history_title),
                summary = stringResource(Res.string.enable_search_history_summary),
                checked = searchHistoryEnabled,
                onCheckedChange = onSearchHistoryEnabledChange
            )

            PreferenceCategoryTitle(
                title = stringResource(Res.string.settings_category_clear_data_title)
            )

            PreferenceRow(
                title = stringResource(Res.string.metadata_cache_wipe_title),
                summary = stringResource(Res.string.metadata_cache_wipe_summary),
                onClick = onWipeMetadataCache
            )
            PreferenceRow(
                title = stringResource(Res.string.clear_views_history_title),
                summary = stringResource(Res.string.clear_views_history_summary),
                onClick = { onRequestClear(HistoryClearTarget.WATCH_HISTORY) }
            )
            PreferenceRow(
                title = stringResource(Res.string.clear_playback_states_title),
                summary = stringResource(Res.string.clear_playback_states_summary),
                onClick = { onRequestClear(HistoryClearTarget.PLAYBACK_STATES) }
            )
            PreferenceRow(
                title = stringResource(Res.string.clear_search_history_title),
                summary = stringResource(Res.string.clear_search_history_summary),
                onClick = { onRequestClear(HistoryClearTarget.SEARCH_HISTORY) }
            )
            PreferenceRow(
                title = stringResource(Res.string.clear_cookie_title),
                summary = stringResource(Res.string.clear_cookie_summary),
                enabled = recaptchaCookiesPresent,
                onClick = onClearRecaptchaCookies
            )
        }
    }

    if (pendingClear != null) {
        ClearDataConfirmationDialog(
            target = pendingClear,
            onConfirm = {
                onConfirmClear(pendingClear)
                onDismissClear()
            },
            onDismiss = onDismissClear
        )
    }
}

@Composable
private fun ClearDataConfirmationDialog(
    target: HistoryClearTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (target) {
        HistoryClearTarget.WATCH_HISTORY -> {
            stringResource(Res.string.delete_view_history_alert)
        }

        HistoryClearTarget.PLAYBACK_STATES -> {
            stringResource(Res.string.delete_playback_states_alert)
        }

        HistoryClearTarget.SEARCH_HISTORY -> {
            stringResource(Res.string.delete_search_history_alert)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.cancel))
            }
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun HistorySettingsScreenPreview() {
    HistorySettingsScreenContent()
}
