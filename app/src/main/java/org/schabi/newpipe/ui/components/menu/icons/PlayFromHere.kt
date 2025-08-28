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
 * Obtained by combining Filled.PlayArrow and Filled.PlaylistPlay
 */
val Icons.Filled.PlayFromHere: ImageVector by lazy {
    materialIcon(name = "Filled.HeadsetPlus") {
        materialPath {
            moveTo(5.000f, 3.000f)
            verticalLineToRelative(11.200f)
            lineToRelative(8.800f, -5.600f)
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
private fun PlayFromHerePreview() {
    Icon(
        imageVector = Icons.Filled.PlayFromHere,
        contentDescription = null,
        modifier = Modifier.size(240.dp),
    )
}
