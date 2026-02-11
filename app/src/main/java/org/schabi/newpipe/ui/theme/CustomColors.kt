package org.schabi.newpipe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A list of custom colors to use throughout the app, in addition to the color scheme defined in
 * [MaterialTheme.colorScheme]. Always try to use a color in [MaterialTheme.colorScheme] first
 * before adding a new color here, so it's easier to keep consistency.
 */
@Immutable
data class CustomColors(
    val onSurfaceVariantLink: Color = Color.Unspecified
)

private val onSurfaceVariantLinkLight = Color(0xFF5060B0)

private val onSurfaceVariantLinkDark = Color(0xFFC0D0FF)

val lightCustomColors = CustomColors(
    onSurfaceVariantLink = onSurfaceVariantLinkLight
)

val darkCustomColors = CustomColors(
    onSurfaceVariantLink = onSurfaceVariantLinkDark
)

/**
 * A `CompositionLocal` that keeps track of the currently set [CustomColors]. This needs to be setup
 * in every place where [MaterialTheme] is also setup, i.e. in the theme composable.
 */
val LocalCustomColors = staticCompositionLocalOf { CustomColors() }

@Suppress("UnusedReceiverParameter") // we do `MaterialTheme.` just for consistency
val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomColors.current
