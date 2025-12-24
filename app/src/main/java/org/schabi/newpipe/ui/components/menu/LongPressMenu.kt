@file:OptIn(ExperimentalMaterial3Api::class)

package org.schabi.newpipe.ui.components.menu

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.ktx.popFirst
import org.schabi.newpipe.ui.components.common.ScaffoldWithToolbar
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.EnqueueNext
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.ShowChannelDetails
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.customColors
import org.schabi.newpipe.util.Either
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.text.FixedHeightCenteredText
import org.schabi.newpipe.util.text.fadedMarquee
import java.time.OffsetDateTime

fun openLongPressMenuInActivity(
    activity: Activity,
    longPressable: LongPressable,
    longPressActions: List<LongPressAction>,
) {
    activity.addContentView(
        getLongPressMenuView(activity, longPressable, longPressActions),
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    )
}

fun getLongPressMenuView(
    context: Context,
    longPressable: LongPressable,
    longPressActions: List<LongPressAction>,
): ComposeView {
    return ComposeView(context).apply {
        setContent {
            AppTheme {
                LongPressMenu(
                    longPressable = longPressable,
                    longPressActions = longPressActions,
                    onDismissRequest = { (this.parent as ViewGroup).removeView(this) },
                )
            }
        }
    }
}

internal val MinButtonWidth = 86.dp

@Composable
fun LongPressMenu(
    longPressable: LongPressable,
    longPressActions: List<LongPressAction>,
    onDismissRequest: () -> Unit,
) {
    var showEditor by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showEditor) {
        // we can't put the editor in a bottom sheet, because it relies on dragging gestures
        Dialog(
            onDismissRequest = { showEditor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ScaffoldWithToolbar(
                title = stringResource(R.string.long_press_menu_actions_editor),
                onBackClick = { showEditor = false },
            ) { paddingValues ->
                LongPressMenuEditor(modifier = Modifier.padding(paddingValues))
            }
        }
    } else {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = onDismissRequest,
            dragHandle = { LongPressMenuDragHandle(onEditActions = { showEditor = true }) },
        ) {
            LongPressMenuContent(
                longPressable = longPressable,
                longPressActions = longPressActions,
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

@Composable
private fun LongPressMenuContent(
    longPressable: LongPressable,
    longPressActions: List<LongPressAction>,
    onDismissRequest: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, bottom = 16.dp)
    ) {
        val buttonHeight = MinButtonWidth // landscape aspect ratio, square in the limit
        val headerWidthInButtons = 5 // the header is 5 times as wide as the buttons
        val buttonsPerRow = (this.maxWidth / MinButtonWidth).toInt()

        // the channel icon goes in the menu header, so do not show a button for it
        val actions = longPressActions.toMutableList()
        val ctx = LocalContext.current
        val onUploaderClick = actions.popFirst { it.type == ShowChannelDetails }
            ?.let { showChannelDetailsAction ->
                {
                    showChannelDetailsAction.action(ctx)
                    onDismissRequest()
                }
            }

        Column {
            var actionIndex = -1 // -1 indicates the header
            while (actionIndex < actions.size) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    var rowIndex = 0
                    while (rowIndex < buttonsPerRow) {
                        if (actionIndex >= actions.size) {
                            // no more buttons to show, fill the rest of the row with a
                            // spacer that has the same weight as the missing buttons, so that
                            // the other buttons don't grow too wide
                            Spacer(
                                modifier = Modifier
                                    .height(buttonHeight)
                                    .fillMaxWidth()
                                    .weight((buttonsPerRow - rowIndex).toFloat()),
                            )
                            break
                        } else if (actionIndex >= 0) {
                            val action = actions[actionIndex]
                            LongPressMenuButton(
                                icon = action.type.icon,
                                text = stringResource(action.type.label),
                                onClick = {
                                    action.action(ctx)
                                    onDismissRequest()
                                },
                                enabled = action.enabled(false),
                                modifier = Modifier
                                    .height(buttonHeight)
                                    .fillMaxWidth()
                                    .weight(1F),
                            )
                            rowIndex += 1
                        } else if (headerWidthInButtons >= buttonsPerRow) {
                            // this branch is taken if the header is going to fit on one line
                            // (i.e. on phones in portrait)
                            LongPressMenuHeader(
                                item = longPressable,
                                onUploaderClick = onUploaderClick,
                                modifier = Modifier
                                    // leave the height as small as possible, since it's the
                                    // only item on the row anyway
                                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                                    .fillMaxWidth()
                                    .weight(headerWidthInButtons.toFloat()),
                            )
                            rowIndex += headerWidthInButtons
                        } else {
                            // this branch is taken if the header will have some buttons to its
                            // right (i.e. on tablets or on phones in landscape)
                            LongPressMenuHeader(
                                item = longPressable,
                                onUploaderClick = onUploaderClick,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .heightIn(min = 70.dp)
                                    .fillMaxWidth()
                                    .weight(headerWidthInButtons.toFloat()),
                            )
                            rowIndex += headerWidthInButtons
                        }
                        actionIndex += 1
                    }
                }
            }
        }
    }
}

@Composable
fun LongPressMenuDragHandle(onEditActions: () -> Unit) {
    var showFocusTrap by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (showFocusTrap) {
            // Just a focus trap to make sure the button below (onEditActions) is not the button
            // that is first focused when opening the view. That would be a problem on Android TVs
            // with DPAD, where the long press menu is opened by long pressing on stuff, and the UP
            // event of the long press would click the button below if it were the first focused.
            // This way we create a focus trap which disappears as soon as it is focused, leaving
            // the focus to "nothing focused". Ideally it would be great to focus the first item in
            // the long press menu, but then there would need to be a way to ignore the UP from the
            // DPAD after an externally-triggered long press.
            Box(Modifier.size(1.dp).focusable().onFocusChanged { showFocusTrap = !it.isFocused })
        }
        BottomSheetDefaults.DragHandle(
            modifier = Modifier.align(Alignment.Center)
        )
        IconButton(
            onClick = onEditActions,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            // show a small button here, it's not an important button and it shouldn't
            // capture the user attention
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = stringResource(R.string.edit),
                // same color and height as the DragHandle
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(2.dp)
                    .size(16.dp),
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun LongPressMenuDragHandlePreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            LongPressMenuDragHandle {}
        }
    }
}

@Composable
fun LongPressMenuHeader(
    item: LongPressable,
    onUploaderClick: (() -> Unit)?,
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
                            .height(70.dp)
                            .widthIn(max = 125.dp) // 16:9 thumbnail at most
                            .clip(MaterialTheme.shapes.large)
                    )
                }

                when (val decoration = item.decoration) {
                    is LongPressable.Decoration.Duration -> {
                        // only show duration if there is a thumbnail
                        if (item.thumbnailUrl != null) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(MaterialTheme.shapes.medium),
                            ) {
                                Text(
                                    text = Localization.getDurationString(decoration.duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp),
                                )
                            }
                        }
                    }

                    is LongPressable.Decoration.Live -> {
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
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    is LongPressable.Decoration.Playlist -> {
                        Surface(
                            color = Color.Black.copy(alpha = 0.4f),
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(width = 40.dp, height = 70.dp)
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
                modifier = Modifier.padding(vertical = 12.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadedMarquee(edgeWidth = 12.dp),
                )

                val subtitle = getSubtitleAnnotatedString(
                    item = item,
                    showLink = onUploaderClick != null,
                    linkColor = MaterialTheme.customColors.onSurfaceVariantLink,
                    ctx = ctx,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        inlineContent = getSubtitleInlineContent(),
                        modifier = if (onUploaderClick == null) {
                            Modifier
                        } else {
                            Modifier.clickable(onClick = onUploaderClick)
                        }
                            .fillMaxWidth()
                            .fadedMarquee(edgeWidth = 12.dp),
                    )
                }
            }
        }
    }
}

fun getSubtitleAnnotatedString(
    item: LongPressable,
    showLink: Boolean,
    linkColor: Color,
    ctx: Context,
) = buildAnnotatedString {
    var shouldAddSeparator = false
    if (showLink) {
        withStyle(
            SpanStyle(
                fontWeight = FontWeight.Bold,
                color = linkColor,
                textDecoration = TextDecoration.Underline
            )
        ) {
            if (item.uploader.isNullOrBlank()) {
                append(ctx.getString(R.string.show_channel_details))
            } else {
                append(item.uploader)
            }
            append(" ")
            // see getSubtitleInlineContent()
            appendInlineContent("open_in_new", "↗")
        }
        shouldAddSeparator = true
    } else if (!item.uploader.isNullOrBlank()) {
        append(item.uploader)
        shouldAddSeparator = true
    }

    val uploadDate = item.uploadDate?.match<String>(
        { it },
        { Localization.relativeTime(it) }
    )
    if (!uploadDate.isNullOrBlank()) {
        if (shouldAddSeparator) {
            append(Localization.DOT_SEPARATOR)
        }
        shouldAddSeparator = true
        append(uploadDate)
    }

    val viewCount = item.viewCount?.let {
        Localization.localizeViewCount(ctx, true, item.streamType, it)
    }
    if (!viewCount.isNullOrBlank()) {
        if (shouldAddSeparator) {
            append(Localization.DOT_SEPARATOR)
        }
        append(viewCount)
    }
}

/**
 * [getSubtitleAnnotatedString] returns a string that might make use of the OpenInNew icon, and we
 * provide it to [Text] through its `inlineContent` parameter.
 */
@Composable
fun getSubtitleInlineContent() = mapOf(
    "open_in_new" to InlineTextContent(
        placeholder = Placeholder(
            width = MaterialTheme.typography.bodyMedium.fontSize,
            height = MaterialTheme.typography.bodyMedium.fontSize,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.customColors.onSurfaceVariantLink,
        )
    }
)

@Composable
fun LongPressMenuButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // TODO possibly make it so that when you long-press on the button, the label appears on-screen
    //  as a small popup, so in case the label text is cut off the users can still read it in full
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(start = 3.dp, top = 8.dp, end = 3.dp, bottom = 2.dp),
        border = null,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            FixedHeightCenteredText(
                text = text,
                lines = 2,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@ExperimentalLayoutApi
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun LongPressMenuButtonPreviews() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            FlowRow {
                for (entry in LongPressAction.Type.entries) {
                    LongPressMenuButton(
                        icon = entry.icon,
                        text = stringResource(entry.label),
                        onClick = { },
                        modifier = Modifier.size(86.dp)
                    )
                }
            }
        }
    }
}

private class LongPressablePreviews : CollectionPreviewParameterProvider<LongPressable>(
    listOf(
        LongPressable(
            title = "Big Buck Bunny",
            url = "https://www.youtube.com/watch?v=YE7VzlLtp-4",
            thumbnailUrl = "https://i.ytimg.com/vi_webp/YE7VzlLtp-4/maxresdefault.webp",
            uploader = "Blender",
            uploaderUrl = "https://www.youtube.com/@BlenderOfficial",
            viewCount = 8765432,
            streamType = null,
            uploadDate = Either.left("16 years ago"),
            decoration = LongPressable.Decoration.Playlist(12),
        ),
        LongPressable(
            title = LoremIpsum().values.first(),
            url = "https://www.youtube.com/watch?v=YE7VzlLtp-4",
            thumbnailUrl = null,
            uploader = "Blender",
            uploaderUrl = "https://www.youtube.com/@BlenderOfficial",
            viewCount = 8765432,
            streamType = StreamType.VIDEO_STREAM,
            uploadDate = Either.left("16 years ago"),
            decoration = LongPressable.Decoration.Duration(500),
        ),
        LongPressable(
            title = LoremIpsum().values.first(),
            url = "https://www.youtube.com/watch?v=YE7VzlLtp-4",
            thumbnailUrl = null,
            uploader = null,
            uploaderUrl = "https://www.youtube.com/@BlenderOfficial",
            viewCount = null,
            streamType = null,
            uploadDate = null,
            decoration = null,
        ),
        LongPressable(
            title = LoremIpsum().values.first(),
            url = "https://www.youtube.com/watch?v=YE7VzlLtp-4",
            thumbnailUrl = "https://i.ytimg.com/vi_webp/YE7VzlLtp-4/maxresdefault.webp",
            uploader = null,
            uploaderUrl = null,
            viewCount = null,
            streamType = StreamType.AUDIO_STREAM,
            uploadDate = null,
            decoration = LongPressable.Decoration.Duration(500),
        ),
        LongPressable(
            title = LoremIpsum().values.first(),
            url = "https://www.youtube.com/watch?v=YE7VzlLtp-4",
            thumbnailUrl = "https://i.ytimg.com/vi_webp/YE7VzlLtp-4/maxresdefault.webp",
            uploader = null,
            uploaderUrl = null,
            viewCount = null,
            streamType = StreamType.LIVE_STREAM,
            uploadDate = null,
            decoration = LongPressable.Decoration.Live,
        ),
        LongPressable(
            title = LoremIpsum().values.first(),
            url = "https://www.youtube.com/watch?v=YE7VzlLtp-4",
            thumbnailUrl = null,
            uploader = null,
            uploaderUrl = null,
            viewCount = null,
            streamType = StreamType.AUDIO_LIVE_STREAM,
            uploadDate = Either.right(OffsetDateTime.now().minusSeconds(12)),
            decoration = LongPressable.Decoration.Playlist(1500),
        ),
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

    // the incorrect theme is set when running the preview in an emulator for some reason...
    val initialUseDarkTheme = isSystemInDarkTheme()
    var useDarkTheme by remember { mutableStateOf(initialUseDarkTheme) }

    AppTheme(useDarkTheme = useDarkTheme) {
        // longPressable is null when running the preview in an emulator for some reason...
        @Suppress("USELESS_ELVIS")
        LongPressMenuContent(
            longPressable = longPressable ?: LongPressablePreviews().values.first(),
            longPressActions = LongPressAction.Type.entries
                // disable Enqueue actions just to show it off
                .map { t -> t.buildAction({ t != EnqueueNext }) { } },
            onDismissRequest = {},
        )
    }
}
