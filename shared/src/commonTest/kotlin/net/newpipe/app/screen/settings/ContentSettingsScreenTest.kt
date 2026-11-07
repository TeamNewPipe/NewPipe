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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.newpipe.app.extensions.settingsModule
import net.newpipe.app.extensions.withKoin
import net.newpipe.app.preferences.ContentPreferences.IMAGE_QUALITY_HIGH
import net.newpipe.app.screen.settings.model.ChannelTab
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.channel_tab_videos
import newpipe.shared.generated.resources.content
import newpipe.shared.generated.resources.content_language_title
import newpipe.shared.generated.resources.image_quality_high
import newpipe.shared.generated.resources.image_quality_summary
import newpipe.shared.generated.resources.ok
import newpipe.shared.generated.resources.show_channel_tabs
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class ContentSettingsScreenTest {

    @Test
    fun rendersTitleAndContentLanguageRow() = runComposeUiTest {
        withKoin(
            modules = listOf(settingsModule()),
            content = { ContentSettingsScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.content)).assertIsDisplayed()
                onNodeWithText(getString(Res.string.content_language_title)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun appLanguageRowOpensSystemSettingsWhenTheSystemOwnsIt() = runComposeUiTest {
        var opened = false
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                ContentSettingsScreenContent(
                    usesSystemAppLanguageSettings = true,
                    systemAppLanguageName = "Deutsch",
                    onOpenSystemAppLanguageSettings = { opened = true }
                )
            },
            onContent = {
                onNodeWithText("Deutsch").performClick()
                assertTrue(opened)
            }
        )
    }

    @Test
    fun channelTabsAreOnlySavedOnConfirm() = runComposeUiTest {
        var saved: Set<String>? = null
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                ContentSettingsScreenContent(onChannelTabsChange = { value -> saved = value })
            },
            onContent = {
                onNodeWithText(getString(Res.string.show_channel_tabs))
                    .performScrollTo()
                    .performClick()
                onNodeWithText(getString(Res.string.channel_tab_videos)).performClick()
                assertNull(saved)

                onNodeWithText(getString(Res.string.ok)).performClick()
                assertEquals(ChannelTab.SHOW_KEYS - ChannelTab.VIDEOS.showKey, saved)
            }
        )
    }

    @Test
    fun imageQualitySummaryNamesTheSelectedQuality() = runComposeUiTest {
        withKoin(
            modules = listOf(settingsModule()),
            content = { ContentSettingsScreenContent(imageQuality = IMAGE_QUALITY_HIGH) },
            onContent = {
                val quality = getString(Res.string.image_quality_high)
                onNodeWithText(getString(Res.string.image_quality_summary, quality))
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        )
    }
}
