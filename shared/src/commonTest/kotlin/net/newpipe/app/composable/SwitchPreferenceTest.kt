/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.composable

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SwitchPreferenceTest {

    @Test
    fun togglingRowFiresCallback() = runComposeUiTest {
        val title = "Enable feature"
        var newValue = false
        setContent {
            SwitchPreference(title = title, checked = false, onCheckedChange = { newValue = it })
        }

        onNodeWithText(title).performClick()
        assertTrue(newValue)
    }

    @Test
    fun disabledRowDoesNotFire() = runComposeUiTest {
        val title = "Enable feature"
        var fired = false
        setContent {
            SwitchPreference(
                title = title,
                checked = false,
                onCheckedChange = { fired = true },
                enabled = false
            )
        }

        onNodeWithText(title).performClick()
        assertFalse(fired)
    }
}