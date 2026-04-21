/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.schabi.newpipe.ComposeActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorReportHelper
import org.schabi.newpipe.ui.screens.ErrorReportEvent
import org.schabi.newpipe.ui.screens.ErrorReportScreen
import org.schabi.newpipe.ui.screens.settings.debug.DebugScreen
import org.schabi.newpipe.ui.screens.settings.home.SettingsHomeScreen

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
        if (backstack.size > 1) {
            backstack.removeLastOrNull()
        } else {
            activity?.finish()
        }
    }

    NavDisplay(
        backStack = backstack,
        onBack = ::onNavigateUp,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            // Error Report
            entry<Screen.Error> {
                val errorInfo = remember {
                    IntentCompat.getParcelableExtra(
                        activity!!.intent,
                        ComposeActivity.EXTRA_ERROR_INFO,
                        ErrorInfo::class.java
                    )!!
                }

                ErrorReportScreen(
                    errorInfo = errorInfo,
                    onEvent = { event ->
                        when (event) {
                            is ErrorReportEvent.ReportViaEmail ->
                                ErrorReportHelper.sendErrorEmail(context, errorInfo, event.comment)

                            is ErrorReportEvent.CopyForGitHub ->
                                ErrorReportHelper.copyForGitHub(context, errorInfo, event.comment)

                            is ErrorReportEvent.ReportOnGitHub ->
                                ErrorReportHelper.openGitHubIssues(context)

                            is ErrorReportEvent.ReadPrivacyPolicy ->
                                ErrorReportHelper.openPrivacyPolicy(context)

                            is ErrorReportEvent.ShareError ->
                                ErrorReportHelper.shareError(context, errorInfo, event.comment)

                            is ErrorReportEvent.NavigateUp ->
                                onNavigateUp()
                        }
                    }
                )
            }

            // Settings
            entry<Screen.Settings.Home> {
                SettingsHomeScreen(
                    onNavigate = { screen -> backstack.add(screen) },
                    onBackClick = ::onNavigateUp
                )
            }

            entry<Screen.Settings.Player> {
                Text(stringResource(id = R.string.settings_category_player_title))
            }

            entry<Screen.Settings.Behaviour> {
                Text(stringResource(id = R.string.settings_category_player_behavior_title))
            }

            entry<Screen.Settings.Download> {
                Text(stringResource(id = R.string.settings_category_downloads_title))
            }

            entry<Screen.Settings.LookFeel> {
                Text(stringResource(id = R.string.settings_category_look_and_feel_title))
            }

            entry<Screen.Settings.HistoryCache> {
                Text(stringResource(id = R.string.settings_category_history_title))
            }

            entry<Screen.Settings.Content> {
                Text(stringResource(id = R.string.settings_category_content_title))
            }

            entry<Screen.Settings.Feed> {
                Text(stringResource(id = R.string.settings_category_feed_title))
            }

            entry<Screen.Settings.Services> {
                Text(stringResource(id = R.string.settings_category_services_title))
            }

            entry<Screen.Settings.Language> {
                Text(stringResource(id = R.string.settings_category_language_title))
            }

            entry<Screen.Settings.BackupRestore> {
                Text(stringResource(id = R.string.settings_category_backup_restore_title))
            }

            entry<Screen.Settings.Updates> {
                Text(stringResource(id = R.string.settings_category_updates_title))
            }

            entry<Screen.Settings.Debug> {
                DebugScreen(onBackClick = { backstack.removeLastOrNull() })
            }
        }
    )
}
