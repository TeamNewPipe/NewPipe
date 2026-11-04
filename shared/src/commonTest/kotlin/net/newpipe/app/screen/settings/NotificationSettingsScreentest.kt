/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.screen.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue
import net.newpipe.app.extensions.settingsModule
import net.newpipe.app.extensions.withKoin
import net.newpipe.app.model.ChannelNotificationCount
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.enable_streams_notifications_title
import newpipe.shared.generated.resources.settings
import newpipe.shared.generated.resources.settings_category_player_notification_title
import newpipe.shared.generated.resources.streams_notifications_interval_title
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class NotificationSettingsScreenTest {

    @Test
    fun newStreamsOptionsAreDisabledWhileTheSwitchIsOff() = runComposeUiTest {
        withKoin(
            modules = listOf(settingsModule()),
            content = { NotificationSettingsScreenContent(newStreamsNotificationsEnabled = false) },
            onContent = {
                onNodeWithText(getString(Res.string.streams_notifications_interval_title))
                    .assertIsNotEnabled()
            }
        )
    }

    @Test
    fun channelsSummaryShowsNotifiedOutOfTotal() = runComposeUiTest {
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                NotificationSettingsScreenContent(
                    newStreamsNotificationsEnabled = true,
                    channelNotificationCount = ChannelNotificationCount(notified = 3, total = 5)
                )
            },
            onContent = {
                onNodeWithText("3/5").assertIsDisplayed()
            }
        )
    }

    @Test
    fun disabledDeviceNotificationsOfferTheSystemSettings() = runComposeUiTest {
        var opened = false
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                NotificationSettingsScreenContent(
                    deviceNotificationsEnabled = false,
                    onOpenSystemNotificationSettings = { opened = true }
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.enable_streams_notifications_title))
                    .assertIsNotEnabled()
                onNodeWithText(getString(Res.string.settings)).performClick()
                assertTrue(opened)
            }
        )
    }

    @Test
    fun playerNotificationStaysEnabledWhenExempt() = runComposeUiTest {
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                NotificationSettingsScreenContent(
                    deviceNotificationsEnabled = false,
                    isPlayerNotificationExempt = true
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.settings_category_player_notification_title))
                    .assertIsEnabled()
            }
        )
    }
}
