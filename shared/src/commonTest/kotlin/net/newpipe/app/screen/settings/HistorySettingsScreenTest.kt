/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import net.newpipe.app.extensions.settingsModule
import net.newpipe.app.extensions.withKoin
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.clear_cookie_title
import newpipe.shared.generated.resources.clear_views_history_title
import newpipe.shared.generated.resources.delete
import newpipe.shared.generated.resources.delete_view_history_alert
import newpipe.shared.generated.resources.enable_playback_resume_title
import newpipe.shared.generated.resources.enable_watch_history_title
import newpipe.shared.generated.resources.settings_category_history_title
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class HistorySettingsScreenTest {

    @Test
    fun rendersTitleAndWatchHistoryRow() = runComposeUiTest {
        withKoin(
            modules = listOf(settingsModule()),
            content = { HistorySettingsScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.settings_category_history_title))
                    .assertIsDisplayed()
                onNodeWithText(getString(Res.string.enable_watch_history_title))
                    .assertIsDisplayed()
            }
        )
    }

    @Test
    fun dependentRowDoesNotToggleWhileWatchHistoryIsOff() = runComposeUiTest {
        var changed: Boolean? = null
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                HistorySettingsScreenContent(
                    watchHistoryEnabled = false,
                    onPlaybackResumeEnabledChange = { value -> changed = value }
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.enable_playback_resume_title)).performClick()
                assertNull(changed)
            }
        )
    }

    @Test
    fun clearWatchHistoryRowAsksBeforeDeleting() = runComposeUiTest {
        var confirmed: HistoryClearTarget? = null
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                HistorySettingsScreenContent(
                    pendingClear = HistoryClearTarget.WATCH_HISTORY,
                    onConfirmClear = { target -> confirmed = target }
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.delete_view_history_alert)).assertIsDisplayed()
                onNodeWithText(getString(Res.string.delete)).performClick()
                assertEquals(HistoryClearTarget.WATCH_HISTORY, confirmed)
            }
        )
    }

    @Test
    fun clearRowOnlyRequestsConfirmationWhenTapped() = runComposeUiTest {
        var requested: HistoryClearTarget? = null
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                HistorySettingsScreenContent(
                    onRequestClear = { target -> requested = target }
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.clear_views_history_title))
                    .performScrollTo()
                    .performClick()
                assertEquals(HistoryClearTarget.WATCH_HISTORY, requested)
            }
        )
    }

    @Test
    fun cookieRowIsInertWithoutStoredCookies() = runComposeUiTest {
        var clicked = false
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                HistorySettingsScreenContent(
                    recaptchaCookiesPresent = false,
                    onClearRecaptchaCookies = { clicked = true }
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.clear_cookie_title))
                    .performScrollTo()
                    .performClick()
                assertFalse(clicked)
            }
        )
    }
}
