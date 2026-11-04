/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import net.newpipe.app.theme.AppTheme
import net.newpipe.app.theme.yewplayColorScheme

/**
 * YewPlay theme for screens hosted in the app's fragments: same colors, fonts and shapes as the
 * Compose screens of :shared, chosen from the theme preferences.
 */
@Composable
fun YpTheme(themeKey: String, nightThemeKey: String, content: @Composable () -> Unit) {
    val colorScheme = yewplayColorScheme(themeKey, nightThemeKey, isSystemInDarkTheme())
    AppTheme(colorScheme = colorScheme) {
        // screens hosted in fragments have no Surface, so give texts the theme's content color
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
    }
}
