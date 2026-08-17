package org.schabi.newpipe.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = YoutubeRed,
    onPrimary = YoutubeWhite,
    background = YoutubeDarkBackground,
    surface = YoutubeDarkSurface,
    surfaceVariant = YoutubeDarkSurfaceVariant,
    onBackground = YoutubeWhite,
    onSurface = YoutubeDarkOnSurface,
    onSurfaceVariant = YoutubeDarkOnSurfaceVariant,
    outline = YoutubeDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = YoutubeRed,
    onPrimary = YoutubeWhite,
    background = YoutubeLightBackground,
    surface = YoutubeLightSurface,
    surfaceVariant = YoutubeLightSurfaceVariant,
    onBackground = YoutubeBlack,
    onSurface = YoutubeLightOnSurface,
    onSurfaceVariant = YoutubeLightOnSurfaceVariant,
    outline = YoutubeLightOutline
)

@Composable
fun YouTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to prefer YouTube colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme

        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(), // Default M3 Typography for now
        content = content
    )
}
