/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import net.newpipe.app.extensions.settingsModule
import net.newpipe.app.extensions.withKoin
import net.newpipe.app.screen.settings.model.PanicApp
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.panic_app_setting_summary
import newpipe.shared.generated.resources.panic_clear_app_database_title
import newpipe.shared.generated.resources.panic_clear_search_history_title
import newpipe.shared.generated.resources.panic_clear_watch_history_title
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class PanicSettingsScreenTest {

    @Test
    fun destructiveActionsStayDisabledUntilTriggerAppIsSet() = runComposeUiTest {
        var triggerApp by mutableStateOf<PanicApp?>(null)
        var changes = 0
        val app = PanicApp("org.example.panic", "Example panic app")
        val database = getString(Res.string.panic_clear_app_database_title)
        val search = getString(Res.string.panic_clear_search_history_title)
        val watch = getString(Res.string.panic_clear_watch_history_title)
        val noApp = getString(Res.string.panic_app_setting_summary)

        withKoin(
            modules = listOf(settingsModule()),
            content = {
                PanicSettingsContent(
                    triggerApp = triggerApp,
                    onClearAppDatabaseChange = { changes++ },
                    onClearSearchHistoryChange = { changes++ },
                    onClearWatchHistoryChange = { changes++ }
                )
            },
            onContent = {
                onNodeWithText(noApp).performScrollTo().assertIsDisplayed()
                onNodeWithText(app.name).assertDoesNotExist()
                for (title in listOf(database, search, watch)) {
                    onNodeWithText(title).performScrollTo().assertIsNotEnabled().performClick()
                }
                assertEquals(0, changes)

                runOnIdle { triggerApp = app }

                onNodeWithText(app.name).performScrollTo().assertIsDisplayed()
                onNodeWithText(noApp).assertDoesNotExist()
                for (title in listOf(database, search, watch)) {
                    onNodeWithText(title).performScrollTo().assertIsEnabled().performClick()
                }
                assertEquals(3, changes)
            }
        )
    }

    @Test
    fun destructiveActionsStayDisabledWithoutTriggerEvenWhenPreviouslySelected() = runComposeUiTest {
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                PanicSettingsContent(
                    clearAppDatabase = true,
                    clearSearchHistory = true,
                    clearWatchHistory = true
                )
            },
            onContent = {
                for (title in listOf(
                    getString(Res.string.panic_clear_app_database_title),
                    getString(Res.string.panic_clear_search_history_title),
                    getString(Res.string.panic_clear_watch_history_title)
                )) {
                    onNodeWithText(title).performScrollTo().assertIsNotEnabled()
                }
            }
        )
    }

    @Test
    fun clearingDatabaseKeepsHistoryActionsDisabledEvenWithTriggerApp() = runComposeUiTest {
        withKoin(
            modules = listOf(settingsModule()),
            content = {
                PanicSettingsContent(
                    triggerApp = PanicApp("org.example.panic", "Example panic app"),
                    clearAppDatabase = true
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.panic_clear_app_database_title))
                    .performScrollTo().assertIsEnabled()
                onNodeWithText(getString(Res.string.panic_clear_search_history_title))
                    .performScrollTo().assertIsNotEnabled()
                onNodeWithText(getString(Res.string.panic_clear_watch_history_title))
                    .performScrollTo().assertIsNotEnabled()
            }
        )
    }
}
