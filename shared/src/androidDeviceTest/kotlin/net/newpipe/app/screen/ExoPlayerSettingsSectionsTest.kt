/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.disable_media_tunneling_automatic_info
import newpipe.shared.generated.resources.disable_media_tunneling_summary
import newpipe.shared.generated.resources.progressive_load_interval_summary
import newpipe.shared.generated.resources.progressive_load_interval_title
import org.jetbrains.compose.resources.getString

@Composable
private fun Hosted(content: @Composable () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
}

@OptIn(ExperimentalTestApi::class)
class ExoPlayerSettingsSectionsTest {

    @Test
    fun rendersLoadIntervalRow() = runComposeUiTest {
        setContent { Hosted { ExoPlayerSettingsSectionsContent() } }

        onNodeWithText(getString(Res.string.progressive_load_interval_title)).assertIsDisplayed()
    }

    @Test
    fun loadIntervalSummaryShowsSelectedEntryLabel() = runComposeUiTest {
        setContent { Hosted { ExoPlayerSettingsSectionsContent() } }

        onNodeWithText(getString(Res.string.progressive_load_interval_summary, "64 KiB"))
            .assertIsDisplayed()
    }

    @Test
    fun mediaTunnelingInfoShownWhenAutoDisabled() = runComposeUiTest {
        setContent {
            Hosted { ExoPlayerSettingsSectionsContent(mediaTunnelingAutoDisabled = true) }
        }

        onNodeWithText(getString(Res.string.disable_media_tunneling_automatic_info), substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun mediaTunnelingInfoHiddenWhenNotAutoDisabled() = runComposeUiTest {
        setContent {
            Hosted { ExoPlayerSettingsSectionsContent(mediaTunnelingAutoDisabled = false) }
        }

        onNodeWithText(getString(Res.string.disable_media_tunneling_summary), substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText(getString(Res.string.disable_media_tunneling_automatic_info), substring = true)
            .assertDoesNotExist()
    }
}
