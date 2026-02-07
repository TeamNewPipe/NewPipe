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
val Icons.Filled.PopupShuffled: ImageVector by lazy {
    materialIcon(name = "Filled.PopupShuffled") {
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
            horizontalLineToRelative(10f)
            verticalLineToRelative(-2.0f)
            horizontalLineToRelative(-10f)
            verticalLineToRelative(-14.0f)
            horizontalLineToRelative(18.0f)
            verticalLineToRelative(7.0f)
            horizontalLineToRelative(2.0f)
            verticalLineToRelative(-7.0f)
            curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
            close()
        }
        materialPath {
            moveTo(15f, 12f)
            moveToRelative(3.145f, 2.135f)
            lineToRelative(-2.140f, -2.135f)
            lineToRelative(-1.005f, 1.005f)
            lineToRelative(2.135f, 2.135f)
            close()
            moveToRelative(1.505f, -2.135f)
            lineToRelative(1.170f, 1.170f)
            lineToRelative(-5.820f, 5.815f)
            lineToRelative(1.005f, 1.005f)
            lineToRelative(5.825f, -5.820f)
            lineToRelative(1.170f, 1.170f)
            lineToRelative(0.000f, -3.340f)
            close()
            moveToRelative(1.215f, 4.855f)
            lineToRelative(-1.005f, 1.005f)
            lineToRelative(0.965f, 0.965f)
            lineToRelative(-1.175f, 1.175f)
            lineToRelative(3.350f, 0.000f)
            lineToRelative(0.000f, -3.350f)
            lineToRelative(-1.170f, 1.170f)
            close()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PopupFromHerePreview() {
    Icon(
        imageVector = Icons.Filled.PopupShuffled,
        contentDescription = null,
        modifier = Modifier.size(240.dp)
    )
}
