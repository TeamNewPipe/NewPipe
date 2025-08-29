@file:Suppress("UnusedReceiverParameter")

package org.schabi.newpipe.ui.components.menu.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/*
        materialPath {
            moveTo(12.0f, 4.0f)
            lineToRelative(-1.41f, 1.41f)
            lineToRelative(5.59f, 5.59f)
            horizontalLineToRelative(-12.17f)
            verticalLineToRelative(2.0f)
            horizontalLineToRelative(12.17f)
            lineToRelative(-5.59f, 5.59f)
            lineToRelative(1.41f, 1.41f)
            lineToRelative(8.0f, -8.0f)
            close()
        }
 */

/**
 * Obtained by combining [androidx.compose.material.icons.filled.Headset]
 * and the tiny arrow in [androidx.compose.material.icons.filled.ContentPasteGo].
 */
val Icons.Filled.BackgroundFromHere: ImageVector by lazy {
    materialIcon(name = "Filled.BackgroundFromHere") {
        materialPath {
            moveTo(12.0f, 1.0f)
            curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
            verticalLineToRelative(7.0f)
            curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
            horizontalLineToRelative(3.0f)
            verticalLineToRelative(-8.0f)
            horizontalLineTo(5.0f)
            verticalLineToRelative(-2.0f)
            curveToRelative(0.0f, -3.87f, 3.13f, -7.0f, 7.0f, -7.0f)
            reflectiveCurveToRelative(7.0f, 3.13f, 7.0f, 7.0f)
            horizontalLineToRelative(2.0f)
            curveToRelative(0.0f, -4.97f, -4.03f, -9.0f, -9.0f, -9.0f)
            close()
        }
        materialPath {
            moveTo(19f, 11.5f)
            lineToRelative(-1.42f, 1.41f)
            lineToRelative(1.58f, 1.58f)
            lineToRelative(-6.17f, 0.0f)
            lineToRelative(0.0f, 2.0f)
            lineToRelative(6.17f, 0.0f)
            lineToRelative(-1.58f, 1.59f)
            lineToRelative(1.42f, 1.41f)
            lineToRelative(3.99f, -4.0f)
            close()
        }
        /*materialPath {
            moveTo(18.6f, 11.00f)
            lineToRelative(-1.064f, 1.064f)
            lineToRelative(2.586f, 2.586f)
            horizontalLineToRelative(-5.622f)
            verticalLineToRelative(-2.086f)
            horizontalLineToRelative(-1.5f)
            verticalLineToRelative(5.672f)
            horizontalLineToRelative(1.5f)
            verticalLineToRelative(-2.086f)
            horizontalLineToRelative(5.622f)
            lineToRelative(-2.586f, 2.586f)
            lineToRelative(1.064f, 1.064f)
            lineToRelative(4.400f, -4.400f)
            close()
        }*/
        /*materialPath {
            moveTo(18.6f, 11.00f)
            lineToRelative(-1.064f, 1.064f)
            lineToRelative(3.336f, 3.336f)
            lineToRelative(-3.336f, 3.336f)
            lineToRelative(1.064f, 1.064f)
            lineToRelative(4.400f, -4.400f)
            close()
        }
        materialPath {
            moveTo(14f, 11.00f)
            lineToRelative(-1.064f, 1.064f)
            lineToRelative(3.336f, 3.336f)
            lineToRelative(-3.336f, 3.336f)
            lineToRelative(1.064f, 1.064f)
            lineToRelative(4.400f, -4.400f)
            close()
        }*/
        /*materialPath {
            moveTo(18.6f, 11.00f)
            lineToRelative(-1.064f, 1.064f)
            lineToRelative(2.586f, 2.586f)
            horizontalLineToRelative(-7.122f)
            verticalLineToRelative(1.500f)
            horizontalLineToRelative(7.122f)
            lineToRelative(-2.586f, 2.586f)
            lineToRelative(1.064f, 1.064f)
            lineToRelative(4.400f, -4.400f)
            close()
        }*/
        /*materialPath {
            moveTo(17.200f, 11.200f)
            lineToRelative(-0.775f, 0.775f)
            lineToRelative(3.075f, 3.075f)
            horizontalLineToRelative(-6.694f)
            verticalLineToRelative(1.100f)
            horizontalLineToRelative(6.694f)
            lineToRelative(-3.075f, 3.075f)
            lineToRelative(0.775f, 0.775f)
            lineToRelative(4.400f, -4.400f)
            close()
        }*/
        /*materialPath {
            moveTo(12.817f, 12.202f)
            lineToRelative(-0.916f, 0.916f)
            lineToRelative(2.977f, 2.983f)
            lineToRelative(-2.977f, 2.983f)
            lineToRelative(0.916f, 0.916f)
            lineToRelative(3.900f, -3.900f)
            close()
        }
        materialPath {
            moveTo(17.100f, 12.202f)
            lineToRelative(-0.916f, 0.916f)
            lineToRelative(2.977f, 2.983f)
            lineToRelative(-2.977f, 2.983f)
            lineToRelative(0.916f, 0.916f)
            lineToRelative(3.900f, -3.900f)
            close()
        }*/
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun BackgroundFromHerePreview() {
    Icon(
        imageVector = Icons.Filled.BackgroundFromHere,
        contentDescription = null,
        modifier = Modifier.size(240.dp),
    )
}
