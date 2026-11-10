/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.newpipe.app.extensions.settingsModule
import net.newpipe.app.extensions.withKoin
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.check_for_updates
import newpipe.shared.generated.resources.checking_updates_toast
import newpipe.shared.generated.resources.updates_setting_description
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class UpdateSettingsScreenTest {

    @Test
    fun updatesSwitchReportsTheNewValue() = runComposeUiTest {
        var newValue: Boolean? = null
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                UpdateSettingsScreenContent(onUpdateChecksEnabledChange = { value -> newValue = value })
            },
            onContent = {
                onNodeWithText(getString(Res.string.updates_setting_description)).performClick()
                assertEquals(true, newValue)
            }
        )
    }

    @Test
    fun manualCheckStartsTheCheckAndTellsTheUser() = runComposeUiTest {
        var checked = false
        withKoin(
            modules = listOf(settingsModule()),
            content = { UpdateSettingsScreenContent(onCheckForUpdates = { checked = true }) },
            onContent = {
                onNodeWithText(getString(Res.string.check_for_updates)).performClick()
                assertTrue(checked)
                onNodeWithText(getString(Res.string.checking_updates_toast)).assertIsDisplayed()
            }
        )
    }
}
