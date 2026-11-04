/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * YewPlay: playlist cover as a 2x2 mosaic of the first thumbnails (proposal, page 14). With a
 * single thumbnail it fills the whole square.
 */
@Composable
fun YpMosaicCover(thumbnailUrls: List<String?>, modifier: Modifier = Modifier) {
    val urls = thumbnailUrls.filterNotNull().take(4)
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (urls.size < 4) {
            if (urls.isNotEmpty()) {
                Tile(urls[0], Modifier.fillMaxSize())
            }
        } else {
            for (row in 0 until 2) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (column in 0 until 2) {
                        Tile(urls[row * 2 + column], Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(url: String, modifier: Modifier) {
    AsyncImage(
        modifier = modifier,
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop
    )
}
