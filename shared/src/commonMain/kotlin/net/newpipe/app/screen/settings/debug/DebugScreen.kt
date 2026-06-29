/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.debug

import androidx.compose.foundation.layout.Column
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
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TextPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.SettingsViewModel
import net.newpipe.app.viewmodel.settings.debug.DebugSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.check_new_streams
import newpipe.shared.generated.resources.crash_the_app
import newpipe.shared.generated.resources.create_error_notification
import newpipe.shared.generated.resources.enable_disposed_exceptions_summary
import newpipe.shared.generated.resources.enable_disposed_exceptions_title
import newpipe.shared.generated.resources.enable_leak_canary_summary
import newpipe.shared.generated.resources.leak_canary_not_available
import newpipe.shared.generated.resources.leakcanary
import newpipe.shared.generated.resources.settings_category_debug_title
import newpipe.shared.generated.resources.settings_layout_redesign
import newpipe.shared.generated.resources.show_crash_the_player_summary
import newpipe.shared.generated.resources.show_crash_the_player_title
import newpipe.shared.generated.resources.show_error_snackbar
import newpipe.shared.generated.resources.show_memory_leaks
import newpipe.shared.generated.resources.show_original_time_ago_summary
import newpipe.shared.generated.resources.show_original_time_ago_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DebugScreen(
    navigator: Navigator = koinInject(),
    debugVm: DebugSettingsViewModel = koinViewModel(),
    settingsVm: SettingsViewModel = koinViewModel()
) {
    val settingsLayoutRedesign by settingsVm.settingsLayoutRedesign.collectAsState()
    val allowHeapDumping by debugVm.allowHeapDumping.collectAsState()
    val allowDisposedExceptions by debugVm.allowDisposedExceptions.collectAsState()
    val showOriginalTimeAgo by debugVm.showOriginalTimeAgo.collectAsState()
    val showCrashThePlayer by debugVm.showCrashThePlayer.collectAsState()

    DebugScreenContent(
        settingsLayoutRedesign = settingsLayoutRedesign,
        isLeakCanaryAvailable = debugVm.isLeakCanaryAvailable,
        allowHeapDumping = allowHeapDumping,
        allowDisposedExceptions = allowDisposedExceptions,
        showOriginalTimeAgo = showOriginalTimeAgo,
        showCrashThePlayer = showCrashThePlayer,
        onNavigateUp = navigator::navigateUp,
        onToggleHeapDumping = debugVm::toggleAllowHeapDumping,
        onShowMemoryLeaks = debugVm::showMemoryLeaks,
        onToggleDisposedExceptions = debugVm::toggleAllowDisposedExceptions,
        onToggleOriginalTimeAgo = debugVm::toggleShowOriginalTimeAgo,
        onToggleCrashThePlayer = debugVm::toggleShowCrashThePlayer,
        onCheckNewStreams = debugVm::checkNewStreams,
        onCrashTheApp = debugVm::crashTheApp,
        onShowErrorSnackbar = debugVm::showErrorSnackbar,
        onCreateErrorNotification = debugVm::createErrorNotification,
        onToggleSettingsLayoutRedesign = settingsVm::toggleSettingsLayoutRedesign
    )
}

@Composable
private fun DebugScreenContent(
    settingsLayoutRedesign: Boolean,
    isLeakCanaryAvailable: Boolean,
    allowHeapDumping: Boolean,
    allowDisposedExceptions: Boolean,
    showOriginalTimeAgo: Boolean,
    showCrashThePlayer: Boolean,
    onNavigateUp: () -> Unit,
    onToggleHeapDumping: (Boolean) -> Unit,
    onShowMemoryLeaks: () -> Unit,
    onToggleDisposedExceptions: (Boolean) -> Unit,
    onToggleOriginalTimeAgo: (Boolean) -> Unit,
    onToggleCrashThePlayer: (Boolean) -> Unit,
    onCheckNewStreams: () -> Unit,
    onCrashTheApp: () -> Unit,
    onShowErrorSnackbar: () -> Unit,
    onCreateErrorNotification: () -> Unit,
    onToggleSettingsLayoutRedesign: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_debug_title),
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
                title = stringResource(Res.string.leakcanary),
                summary = stringResource(
                    if (isLeakCanaryAvailable)
                        Res.string.enable_leak_canary_summary
                    else Res.string.leak_canary_not_available
                ),
                isChecked = allowHeapDumping,
                onCheckedChange = onToggleHeapDumping,
                enabled = isLeakCanaryAvailable
            )
            TextPreference(
                title = stringResource(Res.string.show_memory_leaks),
                onClick = onShowMemoryLeaks,
                enabled = isLeakCanaryAvailable
            )
            SwitchPreference(
                title = stringResource(Res.string.enable_disposed_exceptions_title),
                summary = stringResource(Res.string.enable_disposed_exceptions_summary),
                isChecked = allowDisposedExceptions,
                onCheckedChange = onToggleDisposedExceptions
            )
            SwitchPreference(
                title = stringResource(Res.string.show_original_time_ago_title),
                summary = stringResource(Res.string.show_original_time_ago_summary),
                isChecked = showOriginalTimeAgo,
                onCheckedChange = onToggleOriginalTimeAgo
            )
            SwitchPreference(
                title = stringResource(Res.string.show_crash_the_player_title),
                summary = stringResource(Res.string.show_crash_the_player_summary),
                isChecked = showCrashThePlayer,
                onCheckedChange = onToggleCrashThePlayer
            )
            TextPreference(
                title = stringResource(Res.string.check_new_streams),
                onClick = onCheckNewStreams
            )
            TextPreference(
                title = stringResource(Res.string.crash_the_app),
                onClick = onCrashTheApp
            )
            TextPreference(
                title = stringResource(Res.string.show_error_snackbar),
                onClick = onShowErrorSnackbar
            )
            TextPreference(
                title = stringResource(Res.string.create_error_notification),
                onClick = onCreateErrorNotification
            )
            SwitchPreference(
                title = stringResource(Res.string.settings_layout_redesign),
                isChecked = settingsLayoutRedesign,
                onCheckedChange = onToggleSettingsLayoutRedesign
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun DebugScreenPreview() {
    DebugScreenContent(
        settingsLayoutRedesign = true,
        isLeakCanaryAvailable = true,
        allowHeapDumping = false,
        allowDisposedExceptions = false,
        showOriginalTimeAgo = false,
        showCrashThePlayer = false,
        onNavigateUp = {},
        onToggleHeapDumping = {},
        onShowMemoryLeaks = {},
        onToggleDisposedExceptions = {},
        onToggleOriginalTimeAgo = {},
        onToggleCrashThePlayer = {},
        onCheckNewStreams = {},
        onCrashTheApp = {},
        onShowErrorSnackbar = {},
        onCreateErrorNotification = {},
        onToggleSettingsLayoutRedesign = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun DebugScreenNoLeakCanaryPreview() {
    DebugScreenContent(
        settingsLayoutRedesign = false,
        isLeakCanaryAvailable = false,
        allowHeapDumping = false,
        allowDisposedExceptions = false,
        showOriginalTimeAgo = false,
        showCrashThePlayer = false,
        onNavigateUp = {},
        onToggleHeapDumping = {},
        onShowMemoryLeaks = {},
        onToggleDisposedExceptions = {},
        onToggleOriginalTimeAgo = {},
        onToggleCrashThePlayer = {},
        onCheckNewStreams = {},
        onCrashTheApp = {},
        onShowErrorSnackbar = {},
        onCreateErrorNotification = {},
        onToggleSettingsLayoutRedesign = {}
    )
}