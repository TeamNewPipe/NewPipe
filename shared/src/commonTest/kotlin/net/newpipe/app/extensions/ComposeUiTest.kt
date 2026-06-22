/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.koin.compose.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level
import org.koin.core.module.Module
import org.koin.dsl.koinConfiguration

/**
 * Sets the content for the UI test wrapped inside Koin
 * @param modules Modules for Koin to init for the composables
 * @param content Composable content for testing
 * @param onContent Non-composable code for testing, maybe dependent upon composable code
 */
@OptIn(ExperimentalTestApi::class)
inline fun ComposeUiTest.withKoin(
    modules: List<Module>,
    noinline content: @Composable () -> Unit = {},
    onContent: () -> Unit = {}
) {
    try {
        setContent {
            KoinApplication(
                configuration = koinConfiguration {
                    modules(modules)
                },
                logLevel = Level.DEBUG,
                content = content
            )
        }
        onContent()
    } finally {
        stopKoin()
    }
}
