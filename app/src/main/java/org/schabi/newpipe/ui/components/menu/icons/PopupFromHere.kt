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
 * Obtained by combining Filled.PictureInPicture and Filled.PlaylistPlay
 */
val Icons.Filled.PopupFromHere: ImageVector by lazy {
    materialIcon(name = "Filled.HeadsetPlus") {
        materialPath {
            moveTo(14.320f, 3.200f)
            horizontalLineToRelative(-6.400f)
            verticalLineToRelative(4.800f)
            horizontalLineToRelative(6.400f)
            lineTo(14.320f, 3.200f)
            close()
            moveTo(15.920f, 0.000f)
            lineTo(1.520f, 0.000f)
            curveToRelative(-0.880f, 0.000f, -1.600f, 0.720f, -1.600f, 1.600f)
            verticalLineToRelative(11.200f)
            curveToRelative(0.000f, 0.880f, 0.720f, 1.584f, 1.600f, 1.584f)
            horizontalLineToRelative(14.400f)
            curveToRelative(0.880f, 0.000f, 1.600f, -0.704f, 1.600f, -1.584f)
            lineTo(17.520f, 1.600f)
            curveToRelative(0.000f, -0.880f, -0.720f, -1.600f, -1.600f, -1.600f)
            close()
            moveTo(15.920f, 12.808f)
            lineTo(1.520f, 12.808f)
            lineTo(1.520f, 1.584f)
            horizontalLineToRelative(14.400f)
            verticalLineToRelative(11.224f)
            close()
        }
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
        }
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
