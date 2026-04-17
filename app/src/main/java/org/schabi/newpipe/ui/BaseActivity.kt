/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import org.schabi.newpipe.ui.theme.AppTheme

/**
 * Base activity for Compose-based screens. Provides edge-to-edge display and
 * wraps Compose content in [AppTheme].
 *
 * Subclasses should be annotated with `@AndroidEntryPoint` if they need Hilt injection.
 */
open class BaseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
    }

    /**
     * Sets the Compose content wrapped in [AppTheme]. Call this instead of [setContent] directly.
     */
    fun composeSetContent(content: @Composable () -> Unit) {
        setContent {
            AppTheme(content = content)
        }
    }
}
