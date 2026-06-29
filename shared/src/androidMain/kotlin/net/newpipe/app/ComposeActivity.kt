/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.json.Json
import net.newpipe.Constants
import net.newpipe.app.navigation.Destination
import net.newpipe.app.theme.currentService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Entry point for compose-related UI components on Android
 */
open class ComposeActivity : ComponentActivity() {

    /** Override to add `:app`-side or other host-specific bindings. */
    protected open fun platformModules(): List<Module> {
        return listOf(
            module { single<Context> { applicationContext } }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(
                // TODO: Change when everything is in compose and this is the primary activity
                        startDestination = Json.decodeFromString<Destination>(
                    intent.getStringExtra(Constants.INTENT_SCREEN_KEY)!!
                ),
                onCloseRequest = ::finish,
                platformModules = platformModules()
            ) {
                val view = LocalView.current
                val service = currentService()

                DisposableEffect(service) {
                    val windowController =
                        WindowCompat.getInsetsController(window, view)
                    windowController.isAppearanceLightStatusBars =
                        service.isSchemeColorDensityLight
                    onDispose {
                        windowController.isAppearanceLightStatusBars = false
                    }
                }
            }
        }
    }
}
