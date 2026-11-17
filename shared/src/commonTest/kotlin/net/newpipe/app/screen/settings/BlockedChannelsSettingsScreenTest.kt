/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import net.newpipe.app.extensions.settingsModule
import net.newpipe.app.extensions.withKoin
import net.newpipe.app.model.BlockedChannel
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.BlockedChannelActions
import net.newpipe.app.viewmodel.settings.BlockedChannelsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.blocked_channels
import newpipe.shared.generated.resources.navigate_back
import newpipe.shared.generated.resources.no_blocked_channels
import newpipe.shared.generated.resources.unblock
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class BlockedChannelsSettingsScreenTest {
    @Test
    fun showsEmptyState() = runComposeUiTest {
        setContent { BlockedChannelsSettingsScreenContent(emptyList(), {}) }

        onNodeWithText(getString(Res.string.no_blocked_channels)).assertIsDisplayed()
    }

    @Test
    fun screenHasTitleAndBackNavigation() = runComposeUiTest {
        val navigator = Navigator(Destination.ContentSettings) {}
        navigator.navigateTo(Destination.BlockedChannelsSettings)
        val viewModel = BlockedChannelsViewModel(FakeBlockedChannelActions())
        withKoin(
            modules = listOf(settingsModule()),
            content = { BlockedChannelsSettingsScreen(navigator, viewModel) },
            onContent = {
                onNodeWithText(getString(Res.string.blocked_channels)).assertIsDisplayed()
                onNodeWithContentDescription(getString(Res.string.navigate_back)).performClick()
                assertEquals(Destination.ContentSettings, navigator.backstack.last())
            }
        )
    }

    @Test
    fun observesUpdatesAndUnblocksSelectedChannel() = runComposeUiTest {
        val first = BlockedChannel("https://example.com/1", "First")
        val second = BlockedChannel("https://example.com/2", "Second")
        val actions = FakeBlockedChannelActions()
        val viewModel = BlockedChannelsViewModel(actions)
        setContent {
            val channels by viewModel.blockedChannels.collectAsStateWithLifecycle()
            BlockedChannelsSettingsScreenContent(channels, viewModel::unblockChannel)
        }

        runOnIdle { actions.channels.value = listOf(first, second) }
        onNodeWithText("First").assertIsDisplayed()
        onNodeWithText("Second").assertIsDisplayed()
        onAllNodesWithText(getString(Res.string.unblock))[0].performClick()
        waitUntil { actions.unblockedUrl != null }
        assertEquals(first.url, actions.unblockedUrl)
        onNodeWithText("First").assertDoesNotExist()
        onNodeWithText("Second").assertIsDisplayed()

        runOnIdle { actions.channels.value = emptyList() }
        onNodeWithText(getString(Res.string.no_blocked_channels)).assertIsDisplayed()
    }

    private class FakeBlockedChannelActions : BlockedChannelActions {
        val channels = MutableStateFlow<List<BlockedChannel>>(emptyList())
        var unblockedUrl: String? = null

        override fun getBlockedChannels() = channels

        override suspend fun unblockChannel(url: String) {
            channels.value = channels.value.filterNot { it.url == url }
            unblockedUrl = url
        }
    }
}
