/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import net.newpipe.app.extensions.withKoin
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.disable_media_tunneling_automatic_info
import newpipe.shared.generated.resources.progressive_load_interval_summary
import newpipe.shared.generated.resources.progressive_load_interval_title
import newpipe.shared.generated.resources.settings_category_exoplayer_title
import org.jetbrains.compose.resources.getString
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class ExoPlayerSettingsScreenTest {

    private val emptySettings = module {
        single<Settings> { MapSettings() }
    }

    @Test
    fun rendersTitleAndLoadIntervalRow() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { ExoPlayerSettingsScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.settings_category_exoplayer_title)).assertIsDisplayed()
                onNodeWithText(getString(Res.string.progressive_load_interval_title)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun loadIntervalSummaryShowsSelectedEntryLabel() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { ExoPlayerSettingsScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.progressive_load_interval_summary, "64 KiB")).assertIsDisplayed()
            }
        )
    }

    @Test
    fun mediaTunnelingInfoShownOnlyWhenAutoDisabled() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { ExoPlayerSettingsScreenContent(mediaTunnelingAutoDisabled = true) },
            onContent = {
                onNodeWithText(getString(Res.string.disable_media_tunneling_automatic_info), substring = true)
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        )
    }
}
