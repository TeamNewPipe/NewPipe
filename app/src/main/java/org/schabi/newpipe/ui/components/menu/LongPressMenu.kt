@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.schabi.newpipe.ui.components.menu

import android.content.res.Configuration
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import coil3.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.ui.theme.AppTheme
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            val maxContainerWidth = maxWidth
            val minButtonWidth = 70.dp
            val buttonHeight = 70.dp
            val padding = 12.dp
            val boxCount = ((maxContainerWidth - padding) / (minButtonWidth + padding)).toInt()
            val buttonWidth = (maxContainerWidth - (boxCount + 1) * padding) / boxCount
            val desiredHeaderWidth = buttonWidth * 4 + padding * 3

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(padding),
                verticalArrangement = Arrangement.spacedBy(padding),
                // left and right padding are implicit in the .align(Center), this way approximation
                // errors in the calculations above don't make the items wrap at the wrong position
                modifier = Modifier.align(Alignment.Center),
            ) {
                LongPressMenuHeader(
                    item = longPressable,
                    thumbnailHeight = buttonHeight,
                    // subtract 2.dp to account for approximation errors in the calculations above
                    modifier = if (desiredHeaderWidth >= maxContainerWidth - 2 * padding - 2.dp) {
                        // leave the height as small as possible, since it's the only item on the
                        // row anyway
                        Modifier.width(maxContainerWidth - 2 * padding)
                    } else {
                        // make sure it has the same height as other buttons
                        Modifier.size(desiredHeaderWidth, buttonHeight)
                    }
                )

                LongPressMenuButton(
                    icon = Icons.Default.AddToQueue,
                    text = stringResource(R.string.enqueue),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )

                LongPressMenuButton(
                    icon = Icons.Default.QueuePlayNext,
                    text = stringResource(R.string.enqueue_next_stream),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )

                LongPressMenuButton(
                    icon = Icons.Default.Headset,
                    text = stringResource(R.string.controls_background_title),
                    onClick = {},
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )

                LongPressMenuButton(
                    icon = Icons.Default.PictureInPicture,
                    text = stringResource(R.string.controls_popup_title),
                    onClick = {},
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )

                LongPressMenuButton(
                    icon = Icons.Default.PlayArrow,
                    text = stringResource(R.string.play),
                    onClick = {},
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )

                LongPressMenuButton(
                    icon = Icons.Default.Download,
                    text = stringResource(R.string.download),
                    onClick = {},
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )

                LongPressMenuButton(
                    icon = Icons.AutoMirrored.Default.PlaylistAdd,
                    text = stringResource(R.string.add_to_playlist),
                    onClick = {},
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )

                LongPressMenuButton(
                    icon = Icons.Default.Share,
                    text = stringResource(R.string.share),
                    onClick = {},
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )

                LongPressMenuButton(
                    icon = Icons.Default.OpenInBrowser,
                    text = stringResource(R.string.open_in_browser),
                    onClick = {},
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )

                LongPressMenuButton(
                    icon = Icons.Default.Done,
                    text = stringResource(R.string.mark_as_watched),
                    onClick = {},
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )

                LongPressMenuButton(
                    icon = Icons.Default.Person,
                    text = stringResource(R.string.show_channel_details),
                    onClick = {},
                    enabled = longPressable.uploaderUrl != null,
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )

                LongPressMenuButton(
                    icon = Icons.Default.Delete,
                    text = stringResource(R.string.delete),
                    onClick = {},
                    modifier = Modifier.size(buttonWidth, buttonHeight),
                )
            }
        }
    }
}

@Composable
fun LongPressMenuHeader(
    item: LongPressable,
    thumbnailHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        placeholder = painterResource(R.drawable.placeholder_thumbnail_video),
                        error = painterResource(R.drawable.placeholder_thumbnail_video),
                        modifier = Modifier
                            .height(thumbnailHeight)
                            .widthIn(max = 125.dp) // 16:9 thumbnail at most
                            .clip(MaterialTheme.shapes.large)
                    )
                }

                when (val decoration = item.decoration) {
                    is LongPressableDecoration.Duration -> {
                        // only show duration if there is a thumbnail
                        if (item.thumbnailUrl != null) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(MaterialTheme.shapes.medium),
                            ) {
                                Text(
                                    text = Localization.getDurationString(decoration.duration),
                                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                    is LongPressableDecoration.Live -> {
                        // only show "Live" if there is a thumbnail
                        if (item.thumbnailUrl != null) {
                            Surface(
                                color = Color.Red.copy(alpha = 0.6f),
                                contentColor = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(MaterialTheme.shapes.medium),
                            ) {
                                Text(
                                    text = stringResource(R.string.duration_live).uppercase(),
                                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    is LongPressableDecoration.Playlist -> {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .height(thumbnailHeight)
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
                                    text = Localization.localizeStreamCountMini(
                                        ctx,
                                        decoration.itemCount
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    null -> {}
                }
            }

            Column(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )

                val subtitle = Localization.concatenateStrings(
                    item.uploader,
                    item.uploadDate?.match<String>(
                        { it },
                        { Localization.relativeTime(it) }
                    ),
                    item.viewCount?.let { Localization.localizeViewCount(ctx, it) }
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
            }
        }
    }
}

@Composable
fun LongPressMenuButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(4.dp),
        border = null,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Box {
                // this allows making the box always the same height (i.e. the height of two text
                // lines), while making the text appear centered if it is just a single line
                Text(
                    text = "",
                    style = MaterialTheme.typography.bodySmall,
                    minLines = 2,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
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
            override val decoration: LongPressableDecoration = LongPressableDecoration.Playlist(12)

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
            override val decoration: LongPressableDecoration = LongPressableDecoration.Duration(500)

            override fun getPlayQueue(): PlayQueue {
                return SinglePlayQueue(listOf(), 0)
            }
        },
        object : LongPressable {
            override val title: String = LoremIpsum().values.first()
            override val url: String = "https://www.youtube.com/watch?v=YE7VzlLtp-4"
            override val thumbnailUrl: String? = null
            override val uploader: String? = null
            override val uploaderUrl: String = "https://www.youtube.com/@BlenderOfficial"
            override val viewCount: Long? = null
            override val uploadDate: Either<String, OffsetDateTime>? = null
            override val decoration: LongPressableDecoration? = null

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
            override val decoration: LongPressableDecoration = LongPressableDecoration.Live

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
            override val uploadDate: Either<String, OffsetDateTime> = Either.right(OffsetDateTime.now().minusSeconds(12))
            override val decoration: LongPressableDecoration = LongPressableDecoration.Playlist(1500)

            override fun getPlayQueue(): PlayQueue {
                return SinglePlayQueue(listOf(), 0)
            }
        }
    )
)

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun LongPressMenuPreview(
    @PreviewParameter(LongPressablePreviews::class) longPressable: LongPressable
) {
    DisposableEffect(null) {
        Localization.initPrettyTime(Localization.resolvePrettyTime())
        onDispose {}
    }
    AppTheme {
        LongPressMenu(
            longPressable = longPressable,
            onDismissRequest = {},
            sheetState = rememberStandardBottomSheetState(), // makes it start out as open
        )
    }
}
