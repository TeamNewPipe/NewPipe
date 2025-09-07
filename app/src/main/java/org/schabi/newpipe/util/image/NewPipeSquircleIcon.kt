package org.schabi.newpipe.util.image

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Generated with https://github.com/rafaeltonholo/svg-to-compose/
 * based on assets/newpipe_squircle.svg.
 */
val NewPipeSquircleIcon: ImageVector
    get() {
        val current = _newPipeIcon
        if (current != null) return current

        return ImageVector.Builder(
            name = "org.schabi.newpipe.ui.theme.AppTheme.NewPipeSquircleIcon",
            defaultWidth = 100.0.dp,
            defaultHeight = 100.0.dp,
            viewportWidth = 100.0f,
            viewportHeight = 100.0f,
        ).apply {
            // M0 50 C0 15 15 0 50 0 s50 15 50 50 -15 50 -50 50 S0 85 0 50
            path(
                fill = SolidColor(Color(0xFFCD201F)),
            ) {
                // M 0 50
                moveTo(x = 0.0f, y = 50.0f)
                // C 0 15 15 0 50 0
                curveTo(
                    x1 = 0.0f,
                    y1 = 15.0f,
                    x2 = 15.0f,
                    y2 = 0.0f,
                    x3 = 50.0f,
                    y3 = 0.0f,
                )
                // s 50 15 50 50
                reflectiveCurveToRelative(
                    dx1 = 50.0f,
                    dy1 = 15.0f,
                    dx2 = 50.0f,
                    dy2 = 50.0f,
                )
                // s -15 50 -50 50
                reflectiveCurveToRelative(
                    dx1 = -15.0f,
                    dy1 = 50.0f,
                    dx2 = -50.0f,
                    dy2 = 50.0f,
                )
                // S 0 85 0 50
                reflectiveCurveTo(
                    x1 = 0.0f,
                    y1 = 85.0f,
                    x2 = 0.0f,
                    y2 = 50.0f,
                )
            }
            // M31.7 19.2 v61.7 l9.7 -5.73 V36 l23.8 14 -17.6 10.35 V71.5 L84 50
            path(
                fill = SolidColor(Color(0xFFFFFFFF)),
            ) {
                // M 31.7 19.2
                moveTo(x = 31.7f, y = 19.2f)
                // v 61.7
                verticalLineToRelative(dy = 61.7f)
                // l 9.7 -5.73
                lineToRelative(dx = 9.7f, dy = -5.73f)
                // V 36
                verticalLineTo(y = 36.0f)
                // l 23.8 14
                lineToRelative(dx = 23.8f, dy = 14.0f)
                // l -17.6 10.35
                lineToRelative(dx = -17.6f, dy = 10.35f)
                // V 71.5
                verticalLineTo(y = 71.5f)
                // L 84 50
                lineTo(x = 84.0f, y = 50.0f)
            }
        }.build().also { _newPipeIcon = it }
    }

@Preview
@Composable
private fun IconPreview() {
    Image(
        imageVector = NewPipeSquircleIcon,
        contentDescription = null,
    )
}

@Suppress("ObjectPropertyName")
private var _newPipeIcon: ImageVector? = null
