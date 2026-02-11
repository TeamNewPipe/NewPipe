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
 * Obtained by combining [androidx.compose.material.icons.filled.Headset]
 * and [androidx.compose.material.icons.filled.Shuffle].
 * Some iterations were made before obtaining this icon, if you want to see them, search through git
 * history the commit "Remove previous versions of custom PlayShuffled/FromHere icons".
 */
val Icons.Filled.BackgroundShuffled: ImageVector by lazy {
    materialIcon(name = "Filled.BackgroundShuffled") {
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
            moveTo(13f, 12f)
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
private fun BackgroundShuffledPreview() {
    Icon(
        imageVector = Icons.Filled.BackgroundShuffled,
        contentDescription = null,
        modifier = Modifier.size(240.dp)
    )
}
