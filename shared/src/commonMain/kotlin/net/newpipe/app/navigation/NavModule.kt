/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.navigation

import androidx.compose.runtime.mutableStateListOf
import co.touchlab.kermit.Logger
import net.newpipe.app.screen.about.AboutScreen
import net.newpipe.app.screen.settings.backuprestore.BackupRestoreSettingsScreen
import net.newpipe.app.screen.settings.content.ContentSettingsScreen
import net.newpipe.app.screen.settings.debug.DebugScreen
import net.newpipe.app.screen.settings.download.DownloadSettingsScreen
import net.newpipe.app.screen.settings.exoplayer.ExoPlayerSettingsScreen
import net.newpipe.app.screen.settings.history.HistorySettingsScreen
import net.newpipe.app.screen.settings.home.SettingsHomeScreen
import net.newpipe.app.screen.settings.lookfeel.LookFeelSettingsScreen
import net.newpipe.app.screen.settings.player.PlayerSettingsScreen
import net.newpipe.app.screen.settings.updates.UpdatesSettingsScreen
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

    navigation<Destination.Settings.Home> {
        SettingsHomeScreen()
    }

    navigation<Destination.Settings.Debug> {
        DebugScreen()
    }

    navigation<Destination.Settings.Player> {
        PlayerSettingsScreen()
    }

    navigation<Destination.Settings.Download> {
        DownloadSettingsScreen()
    }

    navigation<Destination.Settings.HistoryCache> {
        HistorySettingsScreen()
    }

    navigation<Destination.Settings.ExoPlayer> {
        ExoPlayerSettingsScreen()
    }

    navigation<Destination.Settings.Updates> {
        UpdatesSettingsScreen()
    }

    navigation<Destination.Settings.LookFeel> {
        LookFeelSettingsScreen()
    }

    navigation<Destination.Settings.BackupRestore> {
        BackupRestoreSettingsScreen()
    }

    navigation<Destination.Settings.Content> {
        ContentSettingsScreen()
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
