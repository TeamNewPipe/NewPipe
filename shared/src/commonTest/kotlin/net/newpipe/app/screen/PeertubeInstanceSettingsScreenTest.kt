/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.newpipe.app.extensions.withKoin
import net.newpipe.app.model.PeertubeInstance
import net.newpipe.app.preferences.PeertubePreferences.DEFAULT_INSTANCE
import net.newpipe.app.screen.settings.model.PeertubeInstanceMessage
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ok
import newpipe.shared.generated.resources.peertube_instance_add_fail
import newpipe.shared.generated.resources.peertube_instance_add_title
import newpipe.shared.generated.resources.restore_defaults
import org.jetbrains.compose.resources.getString
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class PeertubeInstanceSettingsScreenTest {

    private val emptySettings = module {
        single<Settings> { MapSettings() }
    }

    private val tilvids = PeertubeInstance(name = "Tilvids", url = "https://tilvids.com")

    @Test
    fun rendersInstancesWithTheSelectedOneChecked() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = {
                PeertubeInstanceSettingsScreenContent(instances = listOf(DEFAULT_INSTANCE, tilvids))
            },
            onContent = {
                onNodeWithText(tilvids.name).assertIsDisplayed()
                onNodeWithText(DEFAULT_INSTANCE.name, useUnmergedTree = false).assertIsDisplayed()
                onNodeWithText(DEFAULT_INSTANCE.url).assertIsSelected()
            }
        )
    }

    @Test
    fun tappingAnInstanceSelectsIt() = runComposeUiTest {
        var selected: PeertubeInstance? = null
        withKoin(
            modules = listOf(emptySettings),
            content = {
                PeertubeInstanceSettingsScreenContent(
                    instances = listOf(DEFAULT_INSTANCE, tilvids),
                    onSelect = { instance -> selected = instance }
                )
            },
            onContent = {
                onNodeWithText(tilvids.name).performClick()
                assertEquals(tilvids, selected)
            }
        )
    }

    @Test
    fun addDialogPassesTheEnteredUrl() = runComposeUiTest {
        var added: String? = null
        withKoin(
            modules = listOf(emptySettings),
            content = { PeertubeInstanceSettingsScreenContent(onAdd = { url -> added = url }) },
            onContent = {
                onNodeWithContentDescription(getString(Res.string.peertube_instance_add_title))
                    .performClick()
                onNode(hasSetTextAction()).performTextInput("tilvids.com")
                onNodeWithText(getString(Res.string.ok)).performClick()
                assertEquals("tilvids.com", added)
            }
        )
    }

    @Test
    fun restoreDefaultsAsksForConfirmationFirst() = runComposeUiTest {
        var restored = false
        withKoin(
            modules = listOf(emptySettings),
            content = { PeertubeInstanceSettingsScreenContent(onRestoreDefaults = { restored = true }) },
            onContent = {
                onNodeWithContentDescription(getString(Res.string.restore_defaults)).performClick()
                onNodeWithText(getString(Res.string.ok)).performClick()
                assertTrue(restored)
            }
        )
    }

    @Test
    fun failedAddIsShownInASnackbar() = runComposeUiTest {
        var shown = false
        withKoin(
            modules = listOf(emptySettings),
            content = {
                PeertubeInstanceSettingsScreenContent(
                    message = PeertubeInstanceMessage.ADD_FAILED,
                    onMessageShown = { shown = true }
                )
            },
            onContent = {
                waitForIdle()
                onNodeWithText(getString(Res.string.peertube_instance_add_fail)).assertIsDisplayed()
                assertTrue(shown)
            }
        )
    }
}
