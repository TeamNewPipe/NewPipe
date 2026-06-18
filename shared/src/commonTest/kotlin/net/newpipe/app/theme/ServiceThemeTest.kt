/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import net.newpipe.app.Constants.KEY_STREAMING_SERVICE
import net.newpipe.app.extensions.withKoin
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class ServiceThemeTest {

    @Test
    fun testDefaultServiceIsYouTube() = runComposeUiTest {
        val emptySettings = module {
            single<Settings> { MapSettings() }
        }

        withKoin(
            modules = listOf(emptySettings),
            content = {
                assertEquals(currentService(), Service.YOUTUBE)
            }
        )
    }

    @Test
    fun testServiceSwitchingWorks() = runComposeUiTest {
        val settings = module {
            single<Settings> {
                MapSettings(KEY_STREAMING_SERVICE to "PeerTube")
            }
        }

        withKoin(
            modules = listOf(settings),
            content = {
                assertNotEquals(currentService(), Service.YOUTUBE)
                assertEquals(currentService(), Service.PEERTUBE)
            }
        )
    }
}
