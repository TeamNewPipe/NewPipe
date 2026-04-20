/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.schabi.newpipe.error.ErrorReportHelper
import org.schabi.newpipe.ui.screens.ErrorReportScreen
import org.schabi.newpipe.ui.screens.settings.navigation.SettingsNavigation
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * Top-level navigation display for all Compose screens in the app.
 * @param startDestination the initial screen to display, resolved from the launching Intent.
 */

@Composable
fun NavDisplay(startDestination: NavKey) {
    val backstack = rememberNavBackStack(startDestination)
    val context = LocalContext.current

    // TODO: Drop this logic once everything is in Compose
    val activity = LocalActivity.current

    fun onNavigateUp() {
        if (backstack.size == 1) activity?.finish() else backstack.removeLastOrNull()
    }

    NavDisplay(
        backStack = backstack,
        onBack = { backstack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Screen.Error> { screen ->
                ErrorReportScreen(
                    errorInfo = screen.errorInfo,
                    onBackClick = ::onNavigateUp,
                    onReportViaEmail = { comment ->
                        ErrorReportHelper.sendErrorEmail(context, screen.errorInfo, comment)
                    },
                    onCopyForGitHub = { comment ->
                        ErrorReportHelper.copyForGitHub(context, screen.errorInfo, comment)
                    },
                    onReportOnGitHub = {
                        ErrorReportHelper.openGitHubIssues(context)
                    },
                    onReadPrivacyPolicy = {
                        ErrorReportHelper.openPrivacyPolicy(context)
                    },
                    onShareError = { comment ->
                        ErrorReportHelper.shareError(context, screen.errorInfo, comment)
                    }
                )
            }

            entry<Screen.Settings.Home> {
                SettingsNavigation(onExitSettings = ::onNavigateUp)
            }
        }
    )
}
