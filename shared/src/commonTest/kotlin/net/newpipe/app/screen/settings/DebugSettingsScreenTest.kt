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
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.newpipe.app.extensions.withKoin
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.check_new_streams
import newpipe.shared.generated.resources.enable_leak_canary_summary
import newpipe.shared.generated.resources.leak_canary_not_available
import newpipe.shared.generated.resources.leakcanary
import newpipe.shared.generated.resources.settings_category_debug_title
import newpipe.shared.generated.resources.show_memory_leaks
import org.jetbrains.compose.resources.getString
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class DebugSettingsScreenTest {

    private val emptySettings = module {
        single<Settings> { MapSettings() }
    }

    @Test
    fun rendersTitleAndLeakCanaryRow() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { DebugSettingsScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.settings_category_debug_title))
                    .assertIsDisplayed()
                onNodeWithText(getString(Res.string.leakcanary)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun leakCanaryRowsExplainWhyTheyAreDisabled() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { DebugSettingsScreenContent(leakCanaryAvailable = false) },
            onContent = {
                onNodeWithText(getString(Res.string.leak_canary_not_available))
                    .assertIsDisplayed()
                onNodeWithText(getString(Res.string.enable_leak_canary_summary))
                    .assertDoesNotExist()
            }
        )
    }

    @Test
    fun memoryLeaksRowDoesNotFireWhenLeakCanaryIsMissing() = runComposeUiTest {
        var clicked = false
        withKoin(
            modules = listOf(emptySettings),
            content = {
                DebugSettingsScreenContent(
                    leakCanaryAvailable = false,
                    onShowMemoryLeaks = { clicked = true }
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.show_memory_leaks)).performClick()
                assertFalse(clicked)
            }
        )
    }

    @Test
    fun checkNewStreamsRowFiresItsAction() = runComposeUiTest {
        var clicked = false
        withKoin(
            modules = listOf(emptySettings),
            content = { DebugSettingsScreenContent(onCheckNewStreams = { clicked = true }) },
            onContent = {
                onNodeWithText(getString(Res.string.check_new_streams))
                    .performScrollTo()
                    .performClick()
                assertTrue(clicked)
            }
        )
    }
}
