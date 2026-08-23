package org.schabi.newpipe.info_list

import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import com.squareup.picasso.Target
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.InfoItem.InfoType
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.PicassoHelper
import org.schabi.newpipe.util.ThemeHelper
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Composable
fun PipePlayComposeTheme(
    context: Context,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(context) {
        resolveComposeColorScheme(context)
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

private fun resolveComposeColorScheme(context: Context): ColorScheme {
    val background = Color(ThemeHelper.resolveColorFromAttr(context, R.attr.windowBackground))
    val onSurface = Color(ThemeHelper.resolveColorFromAttr(context, android.R.attr.textColorPrimary))
    val onSurfaceVariant = Color(ThemeHelper.resolveColorFromAttr(context, android.R.attr.textColorSecondary))
    val surfaceVariant = Color(ThemeHelper.resolveColorFromAttr(context, R.attr.card_item_background_color))
    val outline = Color(ThemeHelper.resolveColorFromAttr(context, R.attr.border_color))
    val contrastBackground = Color(ThemeHelper.resolveColorFromAttr(context, R.attr.contrast_background_color))
    val resources = context.resources
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val selectedTheme = preferences.getString(
        context.getString(R.string.theme_key),
        resources.getString(R.string.default_theme_value)
    )
    val isLight = when (selectedTheme) {
        resources.getString(R.string.light_theme_key) -> true
        resources.getString(R.string.auto_device_theme_key) -> !ThemeHelper.isDeviceDarkThemeEnabled(context)
        else -> false
    }

    return if (isLight) {
        lightColorScheme(
            primary = onSurface,
            onPrimary = background,
            primaryContainer = surfaceVariant,
            onPrimaryContainer = onSurface,
            secondary = onSurface,
            onSecondary = background,
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = onSurface,
            background = background,
            onBackground = onSurface,
            surface = background,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = contrastBackground,
            scrim = Color.Black
        )
    } else {
        darkColorScheme(
            primary = onSurface,
            onPrimary = background,
            primaryContainer = surfaceVariant,
            onPrimaryContainer = onSurface,
            secondary = onSurface,
            onSecondary = background,
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = onSurface,
            background = background,
            onBackground = onSurface,
            surface = background,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = contrastBackground,
            scrim = Color.Black
        )
    }
}

@Composable
private fun rememberPicassoBitmap(
    key: Any?,
    request: () -> RequestCreator
): Bitmap? {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(key) {
        bitmap = null
        val target = object : Target {
            override fun onBitmapLoaded(loadedBitmap: Bitmap, from: Picasso.LoadedFrom) {
                bitmap = loadedBitmap
            }

            override fun onBitmapFailed(e: Exception?, errorDrawable: android.graphics.drawable.Drawable?) {
                bitmap = null
            }

            override fun onPrepareLoad(placeHolderDrawable: android.graphics.drawable.Drawable?) {
            }
        }
        request().into(target)
        onDispose {
        }
    }

    return bitmap
}

@Composable
private fun RemoteImage(
    modifier: Modifier,
    contentScale: ContentScale,
    bitmap: Bitmap?
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

data class ComposeItemState(
    val title: String,
    val subtitle: String?,
    val details: String?,
    val imageUrl: String?,
    val durationText: String?,
    val showLiveBadge: Boolean,
    val showPaidBadge: Boolean,
    val progress: Float?,
    val playlistCount: String?,
    val isChannel: Boolean
)

fun buildInfoItemState(
    context: Context,
    item: InfoItem,
    historyRecordManager: HistoryRecordManager?
): ComposeItemState? {
    return when (item.infoType) {
        InfoType.STREAM -> {
            item as StreamInfoItem
            val state = historyRecordManager?.loadStreamState(item)?.blockingGet()?.firstOrNull()
            val details = buildString {
                if (item.viewCount >= 0) {
                    append(
                        when (item.streamType) {
                            StreamType.AUDIO_LIVE_STREAM -> Localization.listeningCount(context, item.viewCount)
                            StreamType.LIVE_STREAM -> Localization.shortWatchingCount(context, item.viewCount)
                            else -> Localization.shortViewCount(context, item.viewCount)
                        }
                    )
                }
                val uploadDate = item.uploadDate?.let {
                    Localization.relativeTime(it.offsetDateTime())
                } ?: item.textualUploadDate
                if (!uploadDate.isNullOrEmpty()) {
                    if (isNotEmpty()) {
                        append(" • ")
                    }
                    append(uploadDate)
                }
            }.ifEmpty { null }
            ComposeItemState(
                title = item.name ?: "",
                subtitle = item.uploaderName,
                details = details,
                imageUrl = item.thumbnailUrl,
                durationText = when {
                    item.requiresMembership() -> context.getString(R.string.paid_video)
                    item.duration > 0 -> Localization.getDurationString(item.duration)
                    item.streamType == StreamType.LIVE_STREAM
                            || item.streamType == StreamType.AUDIO_LIVE_STREAM -> context.getString(R.string.duration_live).uppercase()
                    else -> null
                },
                showLiveBadge = item.streamType == StreamType.LIVE_STREAM
                        || item.streamType == StreamType.AUDIO_LIVE_STREAM,
                showPaidBadge = item.requiresMembership(),
                progress = if (state != null && item.duration > 0) {
                    TimeUnit.MILLISECONDS.toSeconds(state.progressMillis).toFloat() / item.duration.toFloat()
                } else {
                    null
                },
                playlistCount = null,
                isChannel = false
            )
        }
        InfoType.PLAYLIST -> {
            item as PlaylistInfoItem
            ComposeItemState(
                title = item.name,
                subtitle = item.uploaderName,
                details = null,
                imageUrl = item.thumbnailUrl,
                durationText = null,
                showLiveBadge = false,
                showPaidBadge = false,
                progress = null,
                playlistCount = Localization.localizeStreamCountMini(context, item.streamCount),
                isChannel = false
            )
        }
        InfoType.CHANNEL -> {
            item as ChannelInfoItem
            val details = buildString {
                if (item.subscriberCount >= 0) {
                    append(Localization.shortSubscriberCount(context, item.subscriberCount))
                }
                if (item.streamCount >= 0) {
                    if (isNotEmpty()) {
                        append(" • ")
                    }
                    append(Localization.localizeStreamCount(context, item.streamCount))
                }
            }.ifEmpty { null }
            ComposeItemState(
                title = item.name,
                subtitle = item.description,
                details = details,
                imageUrl = item.thumbnailUrl,
                durationText = null,
                showLiveBadge = false,
                showPaidBadge = false,
                progress = null,
                playlistCount = null,
                isChannel = true
            )
        }
        else -> null
    }
}

fun buildLocalItemState(
    context: Context,
    item: LocalItem,
    dateTimeFormatter: DateTimeFormatter?
): ComposeItemState? {
    return when (item.localItemType) {
        LocalItem.LocalItemType.PLAYLIST_STREAM_ITEM -> {
            item as PlaylistStreamEntry
            ComposeItemState(
                title = item.streamEntity.title,
                subtitle = null,
                details = Localization.concatenateStrings(
                    item.streamEntity.uploader,
                    NewPipe.getNameOfService(item.streamEntity.serviceId)
                ),
                imageUrl = item.streamEntity.thumbnailUrl,
                durationText = if (item.streamEntity.duration > 0) {
                    Localization.getDurationString(item.streamEntity.duration)
                } else {
                    null
                },
                showLiveBadge = false,
                showPaidBadge = false,
                progress = if (item.progressMillis > 0 && item.streamEntity.duration > 0) {
                    TimeUnit.MILLISECONDS.toSeconds(item.progressMillis).toFloat() / item.streamEntity.duration.toFloat()
                } else {
                    null
                },
                playlistCount = null,
                isChannel = false
            )
        }
        LocalItem.LocalItemType.STATISTIC_STREAM_ITEM -> {
            item as StreamStatisticsEntry
            ComposeItemState(
                title = item.streamEntity.title,
                subtitle = item.streamEntity.uploader,
                details = Localization.concatenateStrings(
                    Localization.shortViewCount(context, item.watchCount),
                    dateTimeFormatter?.format(item.latestAccessDate)
                ),
                imageUrl = item.streamEntity.thumbnailUrl,
                durationText = if (item.streamEntity.duration > 0) {
                    Localization.getDurationString(item.streamEntity.duration)
                } else {
                    null
                },
                showLiveBadge = false,
                showPaidBadge = false,
                progress = if (item.progressMillis > 0 && item.streamEntity.duration > 0) {
                    TimeUnit.MILLISECONDS.toSeconds(item.progressMillis).toFloat() / item.streamEntity.duration.toFloat()
                } else {
                    null
                },
                playlistCount = null,
                isChannel = false
            )
        }
        LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM -> {
            item as PlaylistMetadataEntry
            ComposeItemState(
                title = item.name,
                subtitle = null,
                details = null,
                imageUrl = item.thumbnailUrl,
                durationText = null,
                showLiveBadge = false,
                showPaidBadge = false,
                progress = null,
                playlistCount = Localization.localizeStreamCountMini(context, item.streamCount),
                isChannel = false
            )
        }
        LocalItem.LocalItemType.PLAYLIST_REMOTE_ITEM -> {
            item as PlaylistRemoteEntity
            ComposeItemState(
                title = item.name,
                subtitle = item.uploader,
                details = null,
                imageUrl = item.thumbnailUrl,
                durationText = null,
                showLiveBadge = false,
                showPaidBadge = false,
                progress = null,
                playlistCount = Localization.localizeStreamCountMini(context, item.streamCount ?: -1L),
                isChannel = false
            )
        }
    }
}

@Composable
fun CommonItem(
    state: ComposeItemState,
    isGridLayout: Boolean,
    isCardLayout: Boolean,
    showDragHandle: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDragStart: (() -> Unit)? = null
) {
    if (state.isChannel) {
        if (isGridLayout || isCardLayout) {
            ChannelGridItem(
                state = state,
                modifier = modifier,
                onClick = onClick,
                onLongClick = onLongClick
            )
        } else {
            ChannelListItem(
                state = state,
                modifier = modifier,
                onClick = onClick,
                onLongClick = onLongClick
            )
        }
    } else if (isGridLayout || isCardLayout) {
        StreamOrPlaylistGridItem(
            state = state,
            modifier = modifier,
            onClick = onClick,
            onLongClick = onLongClick,
            showDragHandle = showDragHandle,
            onDragStart = onDragStart,
            isCardLayout = isCardLayout
        )
    } else {
        StreamOrPlaylistListItem(
            state = state,
            modifier = modifier,
            onClick = onClick,
            onLongClick = onLongClick,
            showDragHandle = showDragHandle,
            onDragStart = onDragStart
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelListItem(
    state: ComposeItemState,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val context = LocalContext.current
    val bitmap = rememberPicassoBitmap(state.imageUrl) {
        PicassoHelper.loadScaledDownThumbnail(context, state.imageUrl)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        RemoteImage(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            bitmap = bitmap
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = state.title,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!state.subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!state.details.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.details,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelGridItem(
    state: ComposeItemState,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val context = LocalContext.current
    val bitmap = rememberPicassoBitmap(state.imageUrl) {
        PicassoHelper.loadScaledDownThumbnail(context, state.imageUrl)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RemoteImage(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            bitmap = bitmap
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.title,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (!state.details.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.details,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OverlayBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = textColor,
        fontSize = 10.sp,
        lineHeight = 18.sp,
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
private fun ThumbnailBox(
    state: ComposeItemState,
    width: Dp? = null,
    height: Dp? = null,
    useAspectRatio: Boolean,
    rounded: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = rememberPicassoBitmap(state.imageUrl) {
        if (state.playlistCount != null) {
            PicassoHelper.loadPlaylistThumbnail(state.imageUrl)
        } else {
            PicassoHelper.loadScaledDownThumbnail(context, state.imageUrl)
        }
    }

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .then(if (height != null) Modifier.height(height) else Modifier)
            .then(if (useAspectRatio) Modifier.aspectRatio(16f / 9f) else Modifier)
            .clip(RoundedCornerShape(rounded))
    ) {
        RemoteImage(
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            bitmap = bitmap
        )

        if (state.showLiveBadge) {
            OverlayBadge(
                text = stringResource(R.string.duration_live).uppercase(),
                backgroundColor = Color.Red.copy(alpha = 0.7f),
                textColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        } else if (!state.durationText.isNullOrEmpty()) {
            OverlayBadge(
                text = state.durationText,
                backgroundColor = Color(0x99000000),
                textColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        if (state.showPaidBadge) {
            OverlayBadge(
                text = stringResource(R.string.paid_video).uppercase(),
                backgroundColor = Color(0xFFFFD700),
                textColor = Color.Black,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        state.progress?.takeIf { it > 0f }?.let {
            LinearProgressIndicator(
                progress = it.coerceIn(0f, 1f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color.Red,
                trackColor = Color(0x33FFFFFF)
            )
        }

        state.playlistCount?.let {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.25f)
                    .align(Alignment.CenterEnd)
                    .background(Color(0x80000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Text(
                        text = it,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DragHandle(
    onDragStart: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = Icons.Default.DragHandle,
        contentDescription = stringResource(R.string.detail_drag_description),
        modifier = modifier.pointerInteropFilter {
            if (it.actionMasked == MotionEvent.ACTION_DOWN) {
                onDragStart?.invoke()
            }
            false
        },
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamOrPlaylistListItem(
    state: ComposeItemState,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    showDragHandle: Boolean,
    onDragStart: (() -> Unit)?
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        ThumbnailBox(
            state = state,
            width = 120.dp,
            height = 70.dp,
            useAspectRatio = false,
            rounded = 4.dp,
            modifier = Modifier.padding(vertical = 1.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = state.title,
                style = TextStyle(fontSize = 13.5.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            state.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            state.details?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showDragHandle) {
            DragHandle(
                onDragStart = onDragStart,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(8.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamOrPlaylistGridItem(
    state: ComposeItemState,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    showDragHandle: Boolean,
    onDragStart: (() -> Unit)?,
    isCardLayout: Boolean
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = if (isCardLayout) 2.dp else 12.dp, vertical = if (isCardLayout) 8.dp else 12.dp)
    ) {
        ThumbnailBox(
            state = state,
            useAspectRatio = true,
            rounded = 8.dp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.title,
                style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (showDragHandle) 28.dp else 0.dp)
            )

            if (showDragHandle) {
                DragHandle(
                    onDragStart = onDragStart,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        state.subtitle?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        state.details?.let {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
