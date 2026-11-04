/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.json.Json
import net.newpipe.Constants
import net.newpipe.app.navigation.Destination

/**
 * Entry point for compose-related UI components on Android
 */
class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(
                // TODO: Change when everything is in compose and this is the primary activity
                startDestination = Json.decodeFromString<Destination>(
                    intent.getStringExtra(Constants.INTENT_SCREEN_KEY)!!
                ),
                onCloseRequest = ::finish
            ) {
                val view = LocalView.current
                // YewPlay: dark status bar icons on the light theme, light ones otherwise
                val lightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f

                DisposableEffect(lightTheme) {
                    val windowController = WindowCompat.getInsetsController(window, view)
                    windowController.isAppearanceLightStatusBars = lightTheme
                    onDispose {
                        windowController.isAppearanceLightStatusBars = false
                    }
                }
            }
        }
    }
}
