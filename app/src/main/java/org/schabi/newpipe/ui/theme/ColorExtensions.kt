package org.schabi.newpipe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Extended color for error hint.
 */
val md_theme_light_error_hint = Color(0xCC000000)
val md_theme_dark_error_hint = Color(0xCCFFFFFF)

val ColorScheme.errorHint: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        md_theme_dark_error_hint
    } else {
        md_theme_light_error_hint
    }
