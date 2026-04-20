/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.AndroidEntryPoint
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.navigation.NavDisplay
import org.schabi.newpipe.navigation.Screen
import org.schabi.newpipe.ui.theme.AppTheme

/**
 * Single host activity for all Compose-based screens.
 * Other parts of the app (including legacy View-based code) launch this activity
 * via Intent with extras specifying which screen to display.
 */
@AndroidEntryPoint
class ComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        val startDestination: NavKey = resolveStartDestination(intent)

        setContent {
            AppTheme {
                NavDisplay(startDestination)
            }
        }
    }

    private fun resolveStartDestination(intent: Intent): NavKey {
        return when (intent.getStringExtra(EXTRA_SCREEN)) {
            SCREEN_ERROR -> Screen.Error

            SCREEN_SETTINGS -> Screen.Settings.Home

            else -> throw IllegalArgumentException(
                "Unknown screen: ${intent.getStringExtra(EXTRA_SCREEN)}"
            )
        }
    }

    companion object {
        const val EXTRA_SCREEN = "extra_screen"
        const val EXTRA_ERROR_INFO = "extra_error_info"

        const val SCREEN_ERROR = "error"
        const val SCREEN_SETTINGS = "settings"

        fun errorIntent(context: Context, errorInfo: ErrorInfo): Intent {
            return Intent(context, ComposeActivity::class.java).apply {
                putExtra(EXTRA_SCREEN, SCREEN_ERROR)
                putExtra(EXTRA_ERROR_INFO, errorInfo)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun settingsIntent(context: Context): Intent {
            return Intent(context, ComposeActivity::class.java).apply {
                putExtra(EXTRA_SCREEN, SCREEN_SETTINGS)
            }
        }
    }
}
