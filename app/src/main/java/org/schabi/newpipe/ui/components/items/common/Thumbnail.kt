package org.schabi.newpipe.ui.components.items.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun Thumbnail(
    images: List<Image>,
    imageDescription: String,
    @DrawableRes imagePlaceholder: Int,
    cornerBackgroundColor: Color,
    cornerIcon: ImageVector?,
    cornerText: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        AsyncImage(
            model = ImageStrategy.choosePreferredImage(images),
            contentDescription = imageDescription,
            placeholder = painterResource(imagePlaceholder),
            error = painterResource(imagePlaceholder),
            contentScale = contentScale,
            modifier = modifier
        )

        Row(
            modifier = Modifier
                .padding(2.dp)
                .background(cornerBackgroundColor)
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (cornerIcon != null) {
                Icon(
                    imageVector = cornerIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = cornerText,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
