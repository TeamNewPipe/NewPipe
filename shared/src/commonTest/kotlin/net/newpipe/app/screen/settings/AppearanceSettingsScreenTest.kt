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
import net.newpipe.app.extensions.withKoin
import net.newpipe.app.preferences.AppearancePreferences
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.settings_category_appearance_title
import newpipe.shared.generated.resources.theme_title
import newpipe.shared.generated.resources.auto_device_theme_title
import newpipe.shared.generated.resources.night_theme_available
import org.jetbrains.compose.resources.getString
import org.koin.dsl.module
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppearanceSettingsScreenTest {

    private val emptySettings = module {
        single<Settings> { MapSettings() }
    }

    @Test
    fun rendersTitleAndThemeRow() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { AppearanceSettingsScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.settings_category_appearance_title)).assertIsDisplayed()
                onNodeWithText(getString(Res.string.theme_title)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun nightThemeShowsUnavailableSummaryWhenThemeNotAuto() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = {
                AppearanceSettingsScreenContent(theme = AppearancePreferences.THEME_DARK)
            },
            onContent = {
                onNodeWithText(
                    getString(
                        Res.string.night_theme_available,
                        getString(Res.string.auto_device_theme_title)
                    )
                ).assertIsDisplayed()
            }
        )
    }
}
