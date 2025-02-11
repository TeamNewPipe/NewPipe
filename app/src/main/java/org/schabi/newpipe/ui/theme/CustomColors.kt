package org.schabi.newpipe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class CustomColors(
    val onSurfaceVariantLink: Color = Color.Unspecified,
)

val onSurfaceVariantLinkLight = Color(0xFF5060B0)

val onSurfaceVariantLinkDark = Color(0xFFC0D0FF)

val lightCustomColors = CustomColors(
    onSurfaceVariantLink = onSurfaceVariantLinkLight
)

val darkCustomColors = CustomColors(
    onSurfaceVariantLink = onSurfaceVariantLinkDark
)

val LocalCustomColors = staticCompositionLocalOf { CustomColors() }

val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomColors.current
