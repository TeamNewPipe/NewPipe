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
import net.newpipe.app.model.Developer
import net.newpipe.app.model.Library

@OptIn(ExperimentalTestApi::class)
class LibraryListItemTest {

    @Test
    fun testLibraryListItem() = runComposeUiTest {
        val library = Library(
            id = "Test id",
            name = "Test name",
            developers = listOf(
                Developer(name = "Test developer")
            ),
            licenses = listOf("Apache-2.0"),
            website = "https://www.example.com"
        )
        val desc = "By ${library.developers.first().name} under ${library.licenses.first()}"
        var clicked = false
        setContent {
            LibraryListItem(
                library = library,
                onClick = {
                    clicked = true
                }
            )
        }

        onNodeWithText(library.name).assertIsDisplayed()
        onNodeWithText(desc).apply {
            assertIsDisplayed()
            performClick()
            assertTrue(clicked)
        }
    }
}
