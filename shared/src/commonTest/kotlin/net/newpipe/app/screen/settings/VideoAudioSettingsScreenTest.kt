/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import net.newpipe.app.extensions.withKoin
import net.newpipe.app.preferences.VideoAudioPreferences
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.best_resolution
import newpipe.shared.generated.resources.default_resolution_title
import newpipe.shared.generated.resources.settings_category_video_audio_title
import org.jetbrains.compose.resources.getString
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class VideoAudioSettingsScreenTest {

    private val emptySettings = module {
        single<Settings> { MapSettings() }
    }

    @Test
    fun rendersTitleAndResolutionRow() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { VideoAudioSettingsScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.settings_category_video_audio_title)).assertIsDisplayed()
                onNodeWithText(getString(Res.string.default_resolution_title)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun bestResolutionShowsLocalizedLabelAsSummary() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = {
                VideoAudioSettingsScreenContent(defaultResolution = VideoAudioPreferences.BEST_RESOLUTION)
            },
            onContent = {
                onNodeWithText(getString(Res.string.best_resolution)).assertIsDisplayed()
            }
        )
    }
}