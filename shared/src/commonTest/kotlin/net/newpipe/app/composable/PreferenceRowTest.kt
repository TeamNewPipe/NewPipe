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
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PreferenceRowTest {

    @Test
    fun testPreferenceRowTitleAndClick() = runComposeUiTest {
        val title = "Test title"
        var clicked = false
        setContent {
            PreferenceRow(
                title = title,
                onClick = { clicked = true }
            )
        }

        onNodeWithText(title).apply {
            assertIsDisplayed()
            performClick()
        }
        assertTrue(clicked)
    }

    @Test
    fun testPreferenceRowShowsSummary() = runComposeUiTest {
        val title = "Test title"
        val summary = "Test summary"
        setContent {
            PreferenceRow(title = title, summary = summary)
        }

        onNodeWithText(title).assertIsDisplayed()
        onNodeWithText(summary).assertIsDisplayed()
    }
}
