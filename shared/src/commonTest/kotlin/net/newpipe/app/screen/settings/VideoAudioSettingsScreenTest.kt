/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.newpipe.app.extensions.withKoin
import net.newpipe.app.preferences.VideoAudioPreferences.BEST_RESOLUTION
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.best_resolution
import newpipe.shared.generated.resources.default_resolution_title
import newpipe.shared.generated.resources.minimize_on_exit_popup_description
import newpipe.shared.generated.resources.minimize_on_exit_title
import newpipe.shared.generated.resources.ok
import newpipe.shared.generated.resources.permission_display_over_apps
import newpipe.shared.generated.resources.settings
import newpipe.shared.generated.resources.settings_category_exoplayer_title
import newpipe.shared.generated.resources.settings_category_player_title
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
                onNodeWithText(getString(Res.string.settings_category_video_audio_title))
                    .assertIsDisplayed()
                onNodeWithText(getString(Res.string.default_resolution_title)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun rendersPlayerCategoryHeader() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { VideoAudioSettingsScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.settings_category_player_title))
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        )
    }

    @Test
    fun bestResolutionShowsLocalizedLabelAsSummary() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = {
                VideoAudioSettingsScreenContent(defaultResolution = BEST_RESOLUTION)
            },
            onContent = {
                onNodeWithText(getString(Res.string.best_resolution)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun rendersPlayerSettingsLink() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { VideoAudioSettingsScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.settings_category_exoplayer_title))
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        )
    }

    @Test
    fun choosingPopupWithoutPermissionOpensTheSettings() = runComposeUiTest {
        var opened = false
        withKoin(
            modules = listOf(emptySettings),
            content = {
                VideoAudioSettingsScreenContent(
                    canDrawOverlays = { false },
                    onOpenPopupPermissionSettings = { opened = true }
                )
            },
            onContent = {
                selectMinimizeToPopup()
                onNodeWithText(getString(Res.string.permission_display_over_apps))
                    .assertIsDisplayed()
                onNodeWithText(getString(Res.string.settings)).performClick()
                waitForIdle()
                assertTrue(opened)
            }
        )
    }

    @Test
    fun choosingPopupWithPermissionShowsNoSnackbar() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { VideoAudioSettingsScreenContent(canDrawOverlays = { true }) },
            onContent = {
                selectMinimizeToPopup()
                onNodeWithText(getString(Res.string.permission_display_over_apps))
                    .assertDoesNotExist()
            }
        )
    }

    @Test
    fun permissionGuideIsShownBeforeOpeningTheSettings() = runComposeUiTest {
        var opened = false
        withKoin(
            modules = listOf(emptySettings),
            content = {
                VideoAudioSettingsScreenContent(
                    canDrawOverlays = { false },
                    needsPopupPermissionGuide = true,
                    appLabel = "NewPipe",
                    onOpenPopupPermissionSettings = { opened = true }
                )
            },
            onContent = {
                selectMinimizeToPopup()
                onNodeWithText(getString(Res.string.settings)).performClick()
                waitForIdle()
                assertFalse(opened)
                onNodeWithText(getString(Res.string.ok)).performClick()
                assertTrue(opened)
            }
        )
    }

    private suspend fun ComposeUiTest.selectMinimizeToPopup() {
        onNodeWithText(getString(Res.string.minimize_on_exit_title))
            .performScrollTo()
            .performClick()
        onNodeWithText(getString(Res.string.minimize_on_exit_popup_description)).performClick()
        waitForIdle()
    }
}
