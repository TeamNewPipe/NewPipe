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

/**
 * Obtained by combining [androidx.compose.material.icons.filled.PictureInPicture]
 * and the tiny arrow in [androidx.compose.material.icons.filled.ContentPasteGo].
 */
val Icons.Filled.PopupFromHere: ImageVector by lazy {
    materialIcon(name = "Filled.PopupFromHere") {
        materialPath {
            moveTo(19.0f, 5.0f)
            horizontalLineToRelative(-8.0f)
            verticalLineToRelative(5.0f)
            horizontalLineToRelative(8.0f)
            verticalLineToRelative(-5.0f)
            close()
            moveTo(21.0f, 1.0f)
            horizontalLineToRelative(-18.0f)
            curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
            verticalLineToRelative(14.0f)
            curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
            horizontalLineToRelative(8.5f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(-8.5f)
            verticalLineToRelative(-14.0f)
            horizontalLineToRelative(18.0f)
            verticalLineToRelative(7.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(-7.0f)
            curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
            close()
            /*moveTo(21.0f, 17.01f)
            horizontalLineToRelative(-18.0f)
            verticalLineToRelative(-14.03f)
            horizontalLineToRelative(18.0f)
            verticalLineToRelative(14.03f)
            close()*/
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
        /*materialPath { QUESTO È PERFETTO
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
            moveTo(18.600f, 11.200f)
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
            moveTo(18.600f, 11.200f)
            lineToRelative(-1.41f, 1.41f)
            lineToRelative(1.99f, 1.99f)
            horizontalLineToRelative(-6f)
            verticalLineToRelative(2.00f)
            horizontalLineToRelative(6f)
            lineToRelative(-1.99f, 1.99f)
            lineToRelative(1.41f, 1.41f)
            lineToRelative(4.400f, -4.400f)
            close()
        }*/
        /*
        materialPath {
            moveTo(15.817f, 16.202f)
            lineToRelative(-0.916f, 0.916f)
            lineToRelative(2.977f, 2.983f)
            lineToRelative(-2.977f, 2.983f)
            lineToRelative(0.916f, 0.916f)
            lineToRelative(3.900f, -3.900f)
            close()
        }
        materialPath {
            moveTo(20.100f, 16.202f)
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
private fun PopupFromHerePreview() {
    Icon(
        imageVector = Icons.Filled.PopupFromHere,
        contentDescription = null,
        modifier = Modifier.size(240.dp),
    )
}
