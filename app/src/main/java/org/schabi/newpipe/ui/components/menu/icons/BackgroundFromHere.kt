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
 * Obtained by combining Filled.Headset and Filled.PlaylistPlay
 */
val Icons.Filled.BackgroundFromHere: ImageVector by lazy {
    materialIcon(name = "Filled.HeadsetPlus") {
        materialPath {
            moveTo(7.200f, 0.000f)
            curveToRelative(-3.976f, 0.000f, -7.200f, 3.224f, -7.200f, 7.200f)
            verticalLineToRelative(5.600f)
            curveToRelative(0.000f, 1.328f, 1.072f, 2.400f, 2.400f, 2.400f)
            horizontalLineToRelative(2.400f)
            verticalLineToRelative(-6.400f)
            horizontalLineTo(1.600f)
            verticalLineToRelative(-1.600f)
            curveToRelative(0.000f, -3.096f, 2.504f, -5.600f, 5.600f, -5.600f)
            reflectiveCurveToRelative(5.600f, 2.504f, 5.600f, 5.600f)
            verticalLineToRelative(1.600f)
            horizontalLineToRelative(-3.200f)
            verticalLineToRelative(6.400f)
            horizontalLineToRelative(2.400f)
            curveToRelative(1.328f, 0.000f, 2.400f, -1.072f, 2.400f, -2.400f)
            verticalLineToRelative(-5.600f)
            curveToRelative(0.000f, -3.976f, -3.224f, -7.200f, -7.200f, -7.200f)
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
private fun BackgroundFromHerePreview() {
    Icon(
        imageVector = Icons.Filled.BackgroundFromHere,
        contentDescription = null,
        modifier = Modifier.size(240.dp),
    )
}
