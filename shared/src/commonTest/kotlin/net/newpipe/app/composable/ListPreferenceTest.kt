/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.composable

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ListPreferenceTest {

    private val entries = listOf(
        ListPreferenceEntry("a", "Option A"),
        ListPreferenceEntry("b", "Option B")
    )

    @Test
    fun showsSelectedEntryAsSummary() = runComposeUiTest {
        setContent {
            ListPreference(
                title = "Choose",
                entries = entries,
                selectedValue = "a",
                onValueSelected = {}
            )
        }

        onNodeWithText("Option A").assertIsDisplayed()
    }

    @Test
    fun selectingEntryFiresCallback() = runComposeUiTest {
        var selected: String? = null
        setContent {
            ListPreference(
                title = "Choose",
                entries = entries,
                selectedValue = "a",
                onValueSelected = { selected = it }
            )
        }

        onNodeWithText("Choose").performClick()
        onNodeWithText("Option B").performClick()
        assertEquals("b", selected)
    }
}
