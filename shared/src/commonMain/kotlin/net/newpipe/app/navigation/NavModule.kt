/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.navigation

import androidx.compose.runtime.mutableStateListOf
import co.touchlab.kermit.Logger
import net.newpipe.app.screen.about.AboutScreen
import net.newpipe.app.screen.settings.SettingsHomeScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Singleton
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.single

/**
 * Navigation module to make navigation easier with nav3
 *
 * There is currently no annotation to handle this so we are using DSL API of Koin
 */
@OptIn(KoinExperimentalAPI::class)
fun navModule() = module {
    single<Navigator>()

    navigation<Destination.About> {
        AboutScreen()
    }

    navigation<Destination.Settings> {
        SettingsHomeScreen()
    }
}

/**
 * Helper to navigate up and to different destinations in compose
 */
@Singleton
class Navigator(
    @Provided
    private val startDestination: Destination,

    @Provided
    private val onCloseRequest: () -> Unit
) {

    /**
     * Navigation backstack in compose
     */
    val backstack = mutableStateListOf(startDestination)

    /**
     * Navigates to the given destination
     */
    fun navigateTo(destination: Destination) = backstack.add(destination)

    /**
     * Navigates to the previous entry in the backstack
     */
    fun navigateUp() = when {
        backstack.size > 1 -> backstack.removeLastOrNull()

        else -> {
            Logger.i(messageString = "Cannot remove the only entry in backstack!")
            onCloseRequest()
        }
    }
}
