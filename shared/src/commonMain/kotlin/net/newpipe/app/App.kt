/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.runtime.Composable
import net.newpipe.app.di.KoinApp
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.NavDisplay
import net.newpipe.app.navigation.navModule
import net.newpipe.app.theme.AppTheme
import org.koin.compose.KoinApplication
import org.koin.core.module.Module
import org.koin.plugin.module.dsl.koinConfiguration

/**
 * Entry point for the multiplatform compose application.
 *
 * @param startDestination Starting destination for the app; defaults to About.
 * @param onCloseRequest Callback to close the app.
 * @param platformModules Extra Koin modules supplied by the host platform.
 *   Android passes a module that registers the running Application/Context;
 *   iOS / desktop pass an empty list.
 * @param withKoin Extra composable content rendered inside the Koin context.
 */
@Composable
fun App(
    startDestination: Destination = Destination.About,
    onCloseRequest: () -> Unit,
    platformModules: List<Module> = emptyList(),
    withKoin: @Composable () -> Unit = {}
) {
    KoinApplication(
        configuration = koinConfiguration<KoinApp>(
            appDeclaration = {
                modules(navModule())
                modules(platformModules)
            }
        )
    ) {
        AppTheme {
            NavDisplay(
                startDestination = startDestination,
                onCloseRequest = onCloseRequest
            )
            withKoin()
        }
    }
}
