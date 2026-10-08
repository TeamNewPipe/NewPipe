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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.DebugActions
import net.newpipe.app.preferences.DebugPreferences.DEFAULT_ALLOW_DISPOSED_EXCEPTIONS
import net.newpipe.app.preferences.DebugPreferences.DEFAULT_ALLOW_HEAP_DUMPING
import net.newpipe.app.preferences.DebugPreferences.DEFAULT_SHOW_CRASH_THE_PLAYER
import net.newpipe.app.preferences.DebugPreferences.DEFAULT_SHOW_ORIGINAL_TIME_AGO
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.DebugSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.check_new_streams
import newpipe.shared.generated.resources.crash_the_app
import newpipe.shared.generated.resources.create_error_notification
import newpipe.shared.generated.resources.enable_disposed_exceptions_summary
import newpipe.shared.generated.resources.enable_disposed_exceptions_title
import newpipe.shared.generated.resources.enable_leak_canary_summary
import newpipe.shared.generated.resources.error_snackbar_action
import newpipe.shared.generated.resources.error_snackbar_message
import newpipe.shared.generated.resources.leak_canary_not_available
import newpipe.shared.generated.resources.leakcanary
import newpipe.shared.generated.resources.settings_category_debug_title
import newpipe.shared.generated.resources.show_crash_the_player_summary
import newpipe.shared.generated.resources.show_crash_the_player_title
import newpipe.shared.generated.resources.show_error_snackbar
import newpipe.shared.generated.resources.show_memory_leaks
import newpipe.shared.generated.resources.show_original_time_ago_summary
import newpipe.shared.generated.resources.show_original_time_ago_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val DUMMY = "Dummy"

@Composable
fun DebugSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: DebugSettingsViewModel = koinViewModel(),
    debugActions: DebugActions = koinInject()
) {
    val allowHeapDumping by viewModel.allowHeapDumping.collectAsStateWithLifecycle()
    val allowDisposedExceptions by viewModel.allowDisposedExceptions
        .collectAsStateWithLifecycle()
    val showOriginalTimeAgo by viewModel.showOriginalTimeAgo.collectAsStateWithLifecycle()
    val showCrashThePlayer by viewModel.showCrashThePlayer.collectAsStateWithLifecycle()
    val leakCanaryAvailable = remember { debugActions.isLeakCanaryAvailable }

    DebugSettingsScreenContent(
        allowHeapDumping = allowHeapDumping,
        allowDisposedExceptions = allowDisposedExceptions,
        showOriginalTimeAgo = showOriginalTimeAgo,
        showCrashThePlayer = showCrashThePlayer,
        leakCanaryAvailable = leakCanaryAvailable,
        onAllowHeapDumpingChange = viewModel::setAllowHeapDumping,
        onAllowDisposedExceptionsChange = viewModel::setAllowDisposedExceptions,
        onShowOriginalTimeAgoChange = viewModel::setShowOriginalTimeAgo,
        onShowCrashThePlayerChange = viewModel::setShowCrashThePlayer,
        onShowMemoryLeaks = debugActions::showMemoryLeaks,
        onCheckNewStreams = debugActions::checkNewStreams,
        onCrashTheApp = { throw RuntimeException(DUMMY) },
        onReportDummyError = debugActions::reportDummyError,
        onCreateErrorNotification = debugActions::createErrorNotification,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun DebugSettingsScreenContent(
    allowHeapDumping: Boolean = DEFAULT_ALLOW_HEAP_DUMPING,
    allowDisposedExceptions: Boolean = DEFAULT_ALLOW_DISPOSED_EXCEPTIONS,
    showOriginalTimeAgo: Boolean = DEFAULT_SHOW_ORIGINAL_TIME_AGO,
    showCrashThePlayer: Boolean = DEFAULT_SHOW_CRASH_THE_PLAYER,
    leakCanaryAvailable: Boolean = true,
    onAllowHeapDumpingChange: (Boolean) -> Unit = {},
    onAllowDisposedExceptionsChange: (Boolean) -> Unit = {},
    onShowOriginalTimeAgoChange: (Boolean) -> Unit = {},
    onShowCrashThePlayerChange: (Boolean) -> Unit = {},
    onShowMemoryLeaks: () -> Unit = {},
    onCheckNewStreams: () -> Unit = {},
    onCrashTheApp: () -> Unit = {},
    onReportDummyError: () -> Unit = {},
    onCreateErrorNotification: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val heapDumpingSummary = stringResource(Res.string.enable_leak_canary_summary)
    val notAvailableSummary = stringResource(Res.string.leak_canary_not_available)
    val errorMessage = stringResource(Res.string.error_snackbar_message)
    val reportLabel = stringResource(Res.string.error_snackbar_action)

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_debug_title),
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
                title = stringResource(Res.string.leakcanary),
                summary = if (leakCanaryAvailable) heapDumpingSummary else notAvailableSummary,
                checked = allowHeapDumping,
                onCheckedChange = onAllowHeapDumpingChange,
                enabled = leakCanaryAvailable
            )
            PreferenceRow(
                title = stringResource(Res.string.show_memory_leaks),
                summary = if (leakCanaryAvailable) null else notAvailableSummary,
                enabled = leakCanaryAvailable,
                onClick = onShowMemoryLeaks
            )
            SwitchPreference(
                title = stringResource(Res.string.enable_disposed_exceptions_title),
                summary = stringResource(Res.string.enable_disposed_exceptions_summary),
                checked = allowDisposedExceptions,
                onCheckedChange = onAllowDisposedExceptionsChange
            )
            SwitchPreference(
                title = stringResource(Res.string.show_original_time_ago_title),
                summary = stringResource(Res.string.show_original_time_ago_summary),
                checked = showOriginalTimeAgo,
                onCheckedChange = onShowOriginalTimeAgoChange
            )
            SwitchPreference(
                title = stringResource(Res.string.show_crash_the_player_title),
                summary = stringResource(Res.string.show_crash_the_player_summary),
                checked = showCrashThePlayer,
                onCheckedChange = onShowCrashThePlayerChange
            )
            PreferenceRow(
                title = stringResource(Res.string.check_new_streams),
                onClick = onCheckNewStreams
            )
            PreferenceRow(
                title = stringResource(Res.string.crash_the_app),
                onClick = onCrashTheApp
            )
            PreferenceRow(
                title = stringResource(Res.string.show_error_snackbar),
                onClick = {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = errorMessage,
                            actionLabel = reportLabel
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onReportDummyError()
                        }
                    }
                }
            )
            PreferenceRow(
                title = stringResource(Res.string.create_error_notification),
                onClick = onCreateErrorNotification
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun DebugSettingsScreenPreview() {
    DebugSettingsScreenContent()
}
