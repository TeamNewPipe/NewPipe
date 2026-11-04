/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.bricolage_grotesque_bold
import newpipe.shared.generated.resources.bricolage_grotesque_extrabold
import newpipe.shared.generated.resources.bricolage_grotesque_semibold
import newpipe.shared.generated.resources.instrument_sans_bold
import newpipe.shared.generated.resources.instrument_sans_medium
import newpipe.shared.generated.resources.instrument_sans_regular
import newpipe.shared.generated.resources.instrument_sans_semibold
import org.jetbrains.compose.resources.Font

/** Bricolage Grotesque: títulos de tela, de seção e de vídeo em destaque. */
@Composable
fun yewplayDisplayFamily() = FontFamily(
    Font(Res.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(Res.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(Res.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold)
)

/** Instrument Sans: toda a interface. */
@Composable
fun yewplaySansFamily() = FontFamily(
    Font(Res.font.instrument_sans_regular, FontWeight.Normal),
    Font(Res.font.instrument_sans_medium, FontWeight.Medium),
    Font(Res.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.instrument_sans_bold, FontWeight.Bold)
)

/**
 * Tipografia do YewPlay (proposta de interface, página 7):
 * título de tela 32sp, título de seção 18-20sp, título de item 14,5sp,
 * corpo 13,5-15sp e metadados 12,5-13sp.
 */
@Composable
fun yewplayTypography(): Typography {
    val display = yewplayDisplayFamily()
    val sans = yewplaySansFamily()
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        headlineLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.8).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.5).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.3).sp
        ),
        titleLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp
        ),
        titleMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 23.sp
        ),
        titleSmall = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.5.sp,
            lineHeight = 20.sp
        ),
        bodyLarge = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = sans, fontSize = 13.5.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = sans, fontSize = 12.5.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        labelMedium = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        ),
        labelSmall = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    )
}

/** Formas: botões 12dp, blocos 14-22dp (proposta, página 8). */
val yewplayShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)
