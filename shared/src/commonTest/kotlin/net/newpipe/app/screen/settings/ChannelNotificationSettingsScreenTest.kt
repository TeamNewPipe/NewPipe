/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.newpipe.app.extensions.settingsModule
import net.newpipe.app.extensions.withKoin
import net.newpipe.app.model.ChannelNotification
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.toggle_all
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class ChannelNotificationSettingsScreenTest {

    private val enabledChannel = ChannelNotification(
        id = 1,
        serviceId = 0,
        url = "a",
        name = "Enabled",
        enabled = true
    )
    private val disabledChannel = ChannelNotification(
        id = 2,
        serviceId = 0,
        url = "b",
        name = "Disabled",
        enabled = false
    )

    @Test
    fun checkboxesReflectTheStoredState() = runComposeUiTest {
        withKoin(
            modules = listOf(settingsModule()),
            content = { ChannelNotificationSettingsScreenContent(channels = listOf(enabledChannel, disabledChannel)) },
            onContent = {
                onNodeWithText(enabledChannel.name).assertIsOn()
                onNodeWithText(disabledChannel.name).assertIsOff()
            }
        )
    }

    @Test
    fun tappingAChannelReportsTheNewState() = runComposeUiTest {
        var change: Pair<ChannelNotification, Boolean>? = null
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                ChannelNotificationSettingsScreenContent(
                    channels = listOf(disabledChannel),
                    onEnabledChange = { channel, enabled -> change = channel to enabled }
                )
            },
            onContent = {
                onNodeWithText(disabledChannel.name).performClick()
                assertEquals(disabledChannel to true, change)
            }
        )
    }

    @Test
    fun toggleAllIsOfferedInTheTopBar() = runComposeUiTest {
        var toggled = false
        withKoin(
            modules = listOf(settingsModule()),
            content = { ChannelNotificationSettingsScreenContent(onToggleAll = { toggled = true }) },
            onContent = {
                onNodeWithContentDescription(getString(Res.string.toggle_all)).performClick()
                assertTrue(toggled)
            }
        )
    }
}
