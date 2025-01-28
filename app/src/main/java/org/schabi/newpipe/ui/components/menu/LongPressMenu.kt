@file:OptIn(ExperimentalMaterial3Api::class)

package org.schabi.newpipe.ui.components.menu

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.Either
import org.schabi.newpipe.util.Localization
import java.time.OffsetDateTime

@Composable
fun LongPressMenu(
    longPressable: LongPressable,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest,
        sheetState = sheetState,
    ) {
        Column {
            LongPressMenuHeader(
                item = longPressable,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
            )
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
fun LongPressMenuHeader(item: LongPressable, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Row {
            Box(
                modifier = Modifier.height(70.dp)
            ) {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        placeholder = painterResource(R.drawable.placeholder_thumbnail_video),
                        error = painterResource(R.drawable.placeholder_thumbnail_video),
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 125.dp) // 16:9 thumbnail at most
                            .clip(MaterialTheme.shapes.large)
                    )
                }

                item.playlistSize?.let { playlistSize ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight()
                            .width(40.dp)
                            .clip(MaterialTheme.shapes.large),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Default.PlaylistPlay,
                                contentDescription = null,
                            )
                            Text(
                                text = Localization.localizeStreamCountMini(ctx, playlistSize),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }

                item.duration?.takeIf { it >= 0 }?.let { duration ->
                    // only show duration if there is a thumbnail and there is no playlist header
                    if (item.thumbnailUrl != null && item.playlistSize == null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .clip(MaterialTheme.shapes.medium),
                        ) {
                            Text(
                                text = Localization.getDurationString(duration),
                                modifier = Modifier.padding(vertical = 2.dp, horizontal = 3.dp)
                            )
                        }
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .height(70.dp)
                    .padding(vertical = 12.dp, horizontal = 12.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )

                Text(
                    text = Localization.concatenateStrings(
                        item.uploader,
                        item.uploadDate?.match<String>(
                            { it },
                            { Localization.localizeUploadDate(ctx, it) }
                        ),
                        item.viewCount?.let { Localization.localizeViewCount(ctx, it) }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
        }
    }
}

private class LongPressablePreviews : CollectionPreviewParameterProvider<LongPressable>(
    listOf(
        object : LongPressable {
            override val title: String = "Big Buck Bunny"
            override val url: String = "https://www.youtube.com/watch?v=YE7VzlLtp-4"
            override val thumbnailUrl: String =
                "https://i.ytimg.com/vi_webp/YE7VzlLtp-4/maxresdefault.webp"
            override val uploader: String = "Blender"
            override val uploaderUrl: String = "https://www.youtube.com/@BlenderOfficial"
            override val viewCount: Long = 8765432
            override val uploadDate: Either<String, OffsetDateTime> = Either.left("16 years ago")
            override val playlistSize: Long = 12
            override val duration: Long = 500

            override fun getPlayQueue(): PlayQueue {
                return SinglePlayQueue(listOf(), 0)
            }
        },
        object : LongPressable {
            override val title: String = LoremIpsum().values.first()
            override val url: String = "https://www.youtube.com/watch?v=YE7VzlLtp-4"
            override val thumbnailUrl: String? = null
            override val uploader: String = "Blender"
            override val uploaderUrl: String = "https://www.youtube.com/@BlenderOfficial"
            override val viewCount: Long = 8765432
            override val uploadDate: Either<String, OffsetDateTime> = Either.left("16 years ago")
            override val playlistSize: Long? = null
            override val duration: Long = 500

            override fun getPlayQueue(): PlayQueue {
                return SinglePlayQueue(listOf(), 0)
            }
        },
        object : LongPressable {
            override val title: String = LoremIpsum().values.first()
            override val url: String = "https://www.youtube.com/watch?v=YE7VzlLtp-4"
            override val thumbnailUrl: String =
                "https://i.ytimg.com/vi_webp/YE7VzlLtp-4/maxresdefault.webp"
            override val uploader: String? = null
            override val uploaderUrl: String? = null
            override val viewCount: Long? = null
            override val uploadDate: Either<String, OffsetDateTime>? = null
            override val playlistSize: Long? = null
            override val duration: Long = 500

            override fun getPlayQueue(): PlayQueue {
                return SinglePlayQueue(listOf(), 0)
            }
        },
        object : LongPressable {
            override val title: String = LoremIpsum().values.first()
            override val url: String = "https://www.youtube.com/watch?v=YE7VzlLtp-4"
            override val thumbnailUrl: String? = null
            override val uploader: String? = null
            override val uploaderUrl: String? = null
            override val viewCount: Long? = null
            override val uploadDate: Either<String, OffsetDateTime>? = null
            override val playlistSize: Long = 1500
            override val duration: Long = 500

            override fun getPlayQueue(): PlayQueue {
                return SinglePlayQueue(listOf(), 0)
            }
        }
    )
)

@Preview
@Composable
private fun LongPressMenuPreview(
    @PreviewParameter(LongPressablePreviews::class) longPressable: LongPressable
) {
    LongPressMenu(
        longPressable = longPressable,
        onDismissRequest = {},
        sheetState =  rememberStandardBottomSheetState(), // makes it start out as open
    )
}
