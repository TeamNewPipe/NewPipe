/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable.about

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue
import net.newpipe.app.model.Link

@OptIn(ExperimentalTestApi::class)
class LinkListItemTest {

    @Test
    fun testLinkListItem() = runComposeUiTest {
        val link = Link(
            title = "Test title",
            description = "Test description",
            action = "Test action",
            url = "https://www.example.com"
        )
        var actionClicked = false
        setContent {
            LinkListItem(
                link = link,
                onAction = { actionClicked = true }
            )
        }

        onNodeWithText(link.title).assertIsDisplayed()
        onNodeWithText(link.description).assertIsDisplayed()
        onNodeWithText(link.action).apply {
            assertIsDisplayed()
            performClick()
            assertTrue(actionClicked)
        }
    }
}
