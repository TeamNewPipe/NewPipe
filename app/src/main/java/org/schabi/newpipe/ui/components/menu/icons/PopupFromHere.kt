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
 * Some iterations were made before obtaining this icon, if you want to see them, search through git
 * history the commit "Remove previous versions of custom PlayShuffled/FromHere icons".
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
private fun PopupFromHerePreview() {
    Icon(
        imageVector = Icons.Filled.PopupFromHere,
        contentDescription = null,
        modifier = Modifier.size(240.dp)
    )
}
