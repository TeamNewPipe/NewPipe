/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui.screens.settings.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.ErrorUtil.Companion.createNotification
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.ui.SwitchPreference
import org.schabi.newpipe.ui.TextPreference
import org.schabi.newpipe.ui.components.common.ScaffoldWithToolbar
import org.schabi.newpipe.ui.screens.settings.SettingsViewModel

private const val DUMMY = "Dummy"

data class DebugScreenState(
    val settingsLayoutRedesign: Boolean,
    val isLeakCanaryAvailable: Boolean,
    val allowHeapDumping: Boolean,
    val allowDisposedExceptions: Boolean,
    val showOriginalTimeAgo: Boolean,
    val showCrashThePlayer: Boolean
)

data class DebugScreenActions(
    val onBackClick: () -> Unit,
    val onToggleAllowHeapDumping: (Boolean) -> Unit,
    val onShowMemoryLeaksClick: () -> Unit,
    val onToggleAllowDisposedExceptions: (Boolean) -> Unit,
    val onToggleShowOriginalTimeAgo: (Boolean) -> Unit,
    val onToggleShowCrashThePlayer: (Boolean) -> Unit,
    val onCheckNewStreamsClick: () -> Unit,
    val onCrashTheAppClick: () -> Unit,
    val onShowErrorSnackbarClick: () -> Unit,
    val onCreateErrorNotificationClick: () -> Unit,
    val onToggleSettingsLayoutRedesign: (Boolean) -> Unit
)

@Composable
fun DebugScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    debugViewModel: DebugSettingsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val state = DebugScreenState(
        settingsLayoutRedesign = settingsViewModel.settingsLayoutRedesign.collectAsState().value,
        isLeakCanaryAvailable = debugViewModel.isLeakCanaryAvailable.collectAsState().value,
        allowHeapDumping = debugViewModel.allowHeapDumping.collectAsState().value,
        allowDisposedExceptions = debugViewModel.allowDisposedExceptions.collectAsState().value,
        showOriginalTimeAgo = debugViewModel.showOriginalTimeAgo.collectAsState().value,
        showCrashThePlayer = debugViewModel.showCrashThePlayer.collectAsState().value
    )

    val actions = DebugScreenActions(
        onBackClick = onBackClick,
        onToggleAllowHeapDumping = debugViewModel::toggleAllowHeapDumping,
        onShowMemoryLeaksClick = {
            debugViewModel.getLeakDisplayActivityIntent()?.let {
                context.startActivity(it)
            }
        },
        onToggleAllowDisposedExceptions = debugViewModel::toggleAllowDisposedExceptions,
        onToggleShowOriginalTimeAgo = debugViewModel::toggleShowOriginalTimeAgo,
        onToggleShowCrashThePlayer = debugViewModel::toggleShowCrashThePlayer,
        onCheckNewStreamsClick = debugViewModel::checkNewStreams,
        onCrashTheAppClick = {
            throw RuntimeException(DUMMY)
        },
        onShowErrorSnackbarClick = {
            ErrorUtil.showUiErrorSnackbar(
                context,
                DUMMY,
                RuntimeException(DUMMY)
            )
        },
        onCreateErrorNotificationClick = {
            createNotification(
                context,
                ErrorInfo(
                    RuntimeException(DUMMY),
                    UserAction.UI_ERROR,
                    DUMMY
                )
            )
        },
        onToggleSettingsLayoutRedesign = settingsViewModel::toggleSettingsLayoutRedesign
    )

    DebugScreenContent(
        state = state,
        actions = actions,
        modifier = modifier
    )
}

@Composable
fun DebugScreenContent(
    state: DebugScreenState,
    actions: DebugScreenActions,
    modifier: Modifier = Modifier
) {
    ScaffoldWithToolbar(
        title = stringResource(id = R.string.settings_category_debug_title),
        onBackClick = actions.onBackClick
    ) { paddingValues ->
        Column(modifier = modifier.padding(paddingValues)) {
            SwitchPreference(
                title = stringResource(R.string.leakcanary),
                summary = stringResource(
                    if (state.isLeakCanaryAvailable) {
                        R.string.enable_leak_canary_summary
                    } else {
                        R.string.leak_canary_not_available
                    }
                ),
                isChecked = state.allowHeapDumping,
                onCheckedChange = actions.onToggleAllowHeapDumping,
                enabled = state.isLeakCanaryAvailable
            )
            TextPreference(
                title = stringResource(R.string.show_memory_leaks),
                summary = if (state.isLeakCanaryAvailable) {
                    null
                } else {
                    stringResource(R.string.leak_canary_not_available)
                },
                onClick = actions.onShowMemoryLeaksClick,
                enabled = state.isLeakCanaryAvailable
            )
            SwitchPreference(
                title = stringResource(R.string.enable_disposed_exceptions_title),
                summary = stringResource(R.string.enable_disposed_exceptions_summary),
                isChecked = state.allowDisposedExceptions,
                onCheckedChange = actions.onToggleAllowDisposedExceptions
            )
            SwitchPreference(
                title = stringResource(R.string.show_original_time_ago_title),
                summary = stringResource(R.string.show_original_time_ago_summary),
                isChecked = state.showOriginalTimeAgo,
                onCheckedChange = actions.onToggleShowOriginalTimeAgo
            )
            SwitchPreference(
                title = stringResource(R.string.show_crash_the_player_title),
                summary = stringResource(R.string.show_crash_the_player_summary),
                isChecked = state.showCrashThePlayer,
                onCheckedChange = actions.onToggleShowCrashThePlayer
            )
            TextPreference(
                title = stringResource(R.string.check_new_streams),
                onClick = actions.onCheckNewStreamsClick
            )
            TextPreference(
                title = stringResource(R.string.crash_the_app),
                onClick = actions.onCrashTheAppClick
            )
            TextPreference(
                title = stringResource(R.string.show_error_snackbar),
                onClick = actions.onShowErrorSnackbarClick
            )
            TextPreference(
                title = stringResource(R.string.create_error_notification),
                onClick = actions.onCreateErrorNotificationClick
            )
            SwitchPreference(
                title = stringResource(R.string.settings_layout_redesign),
                isChecked = state.settingsLayoutRedesign,
                onCheckedChange = actions.onToggleSettingsLayoutRedesign
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DebugScreenPreview() {
    DebugScreenContent(
        state = DebugScreenState(
            settingsLayoutRedesign = false,
            isLeakCanaryAvailable = true,
            allowHeapDumping = false,
            allowDisposedExceptions = true,
            showOriginalTimeAgo = false,
            showCrashThePlayer = true
        ),
        actions = DebugScreenActions(
            onBackClick = {},
            onToggleAllowHeapDumping = {},
            onShowMemoryLeaksClick = {},
            onToggleAllowDisposedExceptions = {},
            onToggleShowOriginalTimeAgo = {},
            onToggleShowCrashThePlayer = {},
            onCheckNewStreamsClick = {},
            onCrashTheAppClick = {},
            onShowErrorSnackbarClick = {},
            onCreateErrorNotificationClick = {},
            onToggleSettingsLayoutRedesign = {}
        )
    )
}
