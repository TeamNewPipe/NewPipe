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
 * Obtained by combining [androidx.compose.material.icons.filled.PlayArrow]
 * and the tiny arrow in [androidx.compose.material.icons.filled.ContentPasteGo].
 * Some iterations were made before obtaining this icon, if you want to see them, search through git
 * history the commit "Remove previous versions of custom PlayShuffled/FromHere icons".
 */
val Icons.Filled.PlayFromHere: ImageVector by lazy {
    materialIcon(name = "Filled.PlayFromHere") {
        materialPath {
            moveTo(2.5f, 2.5f)
            verticalLineToRelative(14.0f)
            lineToRelative(11.0f, -7.0f)
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
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PlayFromHerePreview() {
    Icon(
        imageVector = Icons.Filled.PlayFromHere,
        contentDescription = null,
        modifier = Modifier.size(240.dp)
    )
}
