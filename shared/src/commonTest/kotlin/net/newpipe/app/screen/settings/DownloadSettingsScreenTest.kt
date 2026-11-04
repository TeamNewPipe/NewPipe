/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.screen.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import net.newpipe.app.extensions.withKoin
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.download_path_summary
import newpipe.shared.generated.resources.download_path_title
import newpipe.shared.generated.resources.downloads_storage_use_saf_summary_api_29
import newpipe.shared.generated.resources.downloads_storage_use_saf_title
import newpipe.shared.generated.resources.max_retry_desc
import newpipe.shared.generated.resources.settings_category_downloads_title
import org.jetbrains.compose.resources.getString
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class DownloadSettingsScreenTest {

    private val emptySettings = module {
        single<Settings> { MapSettings() }
    }

    @Test
    fun rendersTitleAndUnsetDownloadFolder() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { DownloadSettingsScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.settings_category_downloads_title))
                    .assertIsDisplayed()
                onNodeWithText(getString(Res.string.download_path_summary)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun downloadFolderIsDisabledWhileEveryDownloadAsks() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { DownloadSettingsScreenContent(storageAsk = true) },
            onContent = {
                onNodeWithText(getString(Res.string.download_path_title)).assertIsNotEnabled()
            }
        )
    }

    @Test
    fun storageAccessFrameworkIsLockedWhenNotConfigurable() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = {
                DownloadSettingsScreenContent(storageAccessFrameworkConfigurable = false)
            },
            onContent = {
                onNodeWithText(getString(Res.string.downloads_storage_use_saf_summary_api_29))
                    .assertIsDisplayed()
                onNodeWithText(getString(Res.string.downloads_storage_use_saf_title))
                    .assertIsNotEnabled()
            }
        )
    }

    @Test
    fun maximumRetriesKeepsItsStaticSummary() = runComposeUiTest {
        withKoin(
            modules = listOf(emptySettings),
            content = { DownloadSettingsScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.max_retry_desc)).assertIsDisplayed()
            }
        )
    }
}
