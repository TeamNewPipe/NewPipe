/*
 * SPDX-FileCopyrightText: 2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import com.russhwolf.settings.Settings
import org.koin.compose.koinInject
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.russhwolf.settings.ObservableSettings

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark
)

private val blackScheme = darkScheme.copy(surface = Color.Black)

/**
 * Gets current color scheme chosen by the user
 */
@Composable
fun currentColorScheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    settings: Settings = koinInject()
): ColorScheme {
    val theme = rememberSettingString(settings, "theme", "auto_device_theme")
    val nightTheme = rememberSettingString(settings, "night_theme", "dark_theme")

    val nightScheme = when (nightTheme) {
        "black_theme" -> blackScheme
        else -> darkScheme
    }

    return when (theme) {
        "light_theme" -> lightScheme
        "dark_theme" -> darkScheme
        "black_theme" -> blackScheme
        else -> if (!useDarkTheme) lightScheme else nightScheme
    }
}

/**
 * Reads a string setting as Compose state, updating when the stored value changes so theme
 * edits made elsewhere (e.g. the Appearance screen) apply live without recreating the host.
 */
@Composable
private fun rememberSettingString(
    settings: Settings,
    key: String,
    defaultValue: String
): String {
    var value by remember(key) { mutableStateOf(settings.getString(key, defaultValue)) }
    val observable = settings as? ObservableSettings
    if (observable != null) {
        DisposableEffect(key, observable) {
            val listener = observable.addStringListener(key, defaultValue) { value = it }
            onDispose { listener.deactivate() }
        }
    }
    return value
}

/**
 * Default app theme for the NewPipe composables
 * @param isPreview Whether the theme is being used in preview mode
 * @param colorScheme Color scheme to use for theme, defaults to light in preview mode
 * @param content Composable content to show to the user
 */
@Composable
fun AppTheme(
    isPreview: Boolean = LocalInspectionMode.current,
    colorScheme: ColorScheme = when {
        isPreview -> if (isSystemInDarkTheme()) darkScheme else lightScheme
        else -> currentColorScheme()
    },
    content: @Composable () -> Unit
) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        content = content
    )
}
