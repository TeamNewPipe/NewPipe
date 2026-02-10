@file:OptIn(ExperimentalMaterial3Api::class)

package org.schabi.newpipe.ui.components.menu

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction.LONG_PRESS_MENU_ACTION
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.ui.components.common.SimpleTooltipBox
import org.schabi.newpipe.ui.components.common.TooltipIconButton
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.EnqueueNext
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.ShowChannelDetails
import org.schabi.newpipe.ui.discardAllTouchesIf
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.ui.theme.customColors
import org.schabi.newpipe.util.Either
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.text.FixedHeightCenteredText
import org.schabi.newpipe.util.text.fadedMarquee

internal val MinButtonWidth = 86.dp
internal val ThumbnailHeight = 60.dp
private const val TAG = "LongPressMenu"

/**
 * Opens the long press menu from a View UI. From a Compose UI, use [LongPressMenu] directly.
 */
fun openLongPressMenuInActivity(
    activity: Activity,
    longPressable: LongPressable,
    longPressActions: List<LongPressAction>
) {
    val composeView = ComposeView(activity)
    composeView.setContent {
        AppTheme {
            LongPressMenu(
                longPressable = longPressable,
                longPressActions = longPressActions,
                onDismissRequest = { (composeView.parent as? ViewGroup)?.removeView(composeView) }
            )
        }
    }
    activity.addContentView(
        composeView,
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    )
}

/**
 * Shows a bottom sheet menu containing a small header with the information in [longPressable], and
 * then a list of actions that the user can perform on that item.
 *
 * @param longPressable contains information about the item that was just long-pressed, this
 * information will be shown in a small header at the top of the menu, unless the user disabled it
 * @param longPressActions should contain a list of all *applicable* actions for the item, and this
 * composable's implementation will take care of filtering out the actions that the user has not
 * disabled in settings. For more info see [LongPressAction]
 * @param onDismissRequest called when the [LongPressMenu] should be closed, because the user either
 * dismissed it or chose an action
 */
@Composable
fun LongPressMenu(
    longPressable: LongPressable,
    longPressActions: List<LongPressAction>,
    onDismissRequest: () -> Unit
) {
    // there are three possible states for the long press menu:
    // - the starting state, with the menu shown
    // - the loading state, after a user presses on an action that takes some time to be performed
    // - the editor state, after the user clicks on the editor button in the top right
    val viewModel: LongPressMenuViewModel = viewModel()
    val isHeaderEnabled by viewModel.isHeaderEnabled.collectAsState()
    val actionArrangement by viewModel.actionArrangement.collectAsState()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // intersection between applicable actions (longPressActions) and actions that the user
    // enabled in settings (actionArrangement)
    val enabledLongPressActions by remember {
        derivedStateOf {
            actionArrangement.mapNotNull { type ->
                longPressActions.firstOrNull { it.type == type }
            }
        }
    }

    val ctx = LocalContext.current
    // run actions on the main thread!
    val coroutineScope = rememberCoroutineScope { Dispatchers.Main }
    fun runActionAndDismiss(action: LongPressAction) {
        if (isLoading) {
            return // shouldn't be reachable, but just in case, prevent running two actions
        }
        isLoading = true
        coroutineScope.launch {
            try {
                action.action(ctx)
            } catch (_: CancellationException) {
                // the user canceled the action, e.g. by dismissing the dialog while loading
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Got CancellationException while running action ${action.type}")
                }
            } catch (t: Throwable) {
                ErrorUtil.showSnackbar(
                    ctx,
                    ErrorInfo(t, LONG_PRESS_MENU_ACTION, "Running action ${action.type}")
                )
            }
            onDismissRequest()
        }
    }

    // show a clickable uploader in the header if an uploader action is available and the
    // "show channel details" action is not enabled as a standalone action
    val onUploaderClick by remember {
        derivedStateOf {
            longPressActions.firstOrNull { it.type == ShowChannelDetails }
                ?.takeIf { !actionArrangement.contains(ShowChannelDetails) }
                ?.let { showChannelAction -> { runActionAndDismiss(showChannelAction) } }
        }
    }

    // takes care of showing either the actions or a loading indicator in a bottom sheet
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        dragHandle = { LongPressMenuDragHandle(onEditActions = { showEditor = true }) }
    ) {
        // this Box and the .matchParentSize() below make sure that once the loading starts, the
        // bottom sheet menu size remains the same and the loading button is shown in the middle
        Box(modifier = Modifier.discardAllTouchesIf(isLoading)) {
            LongPressMenuContent(
                header = longPressable.takeIf { isHeaderEnabled },
                onUploaderClick = onUploaderClick,
                actions = enabledLongPressActions,
                runActionAndDismiss = ::runActionAndDismiss
            )
            // importing makes the ColumnScope overload be resolved, so we use qualified path...
            androidx.compose.animation.AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // takes care of showing the editor screen
    if (showEditor && !isLoading) {
        // we can't put the editor in a bottom sheet, because it relies on dragging gestures and it
        // benefits from a bigger screen, so we use a fullscreen dialog instead
        Dialog(
            onDismissRequest = { showEditor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            LongPressMenuEditorPage { showEditor = false }
        }
    }
}

/**
 * Arranges the header and the buttons in a grid according to the following constraints:
 * - buttons have a minimum width, and all buttons should be exactly the same size
 * - as many buttons as possible should fit in a row, with no space between them, so misclicks can
 * still be caught and to leave more space for the button label text
 * - the header is exactly as large as `headerWidthInButtonsReducedSpan=4` buttons, but
 * `maxHeaderWidthInButtonsFullSpan=5` buttons wouldn't fit in a row then the header uses a full row
 * - if the header is not using a full row, then more buttons should fit with it on the same row,
 * so that the space is used efficiently e.g. in landscape or large screens
 * - the menu should be vertically scrollable if there are too many actions to fit on the screen
 *
 * Unfortunately all these requirements mean we can't simply use a [FlowRow] but have to build a
 * custom layout with [Row]s inside a [Column]. To make each item in the row have the appropriate
 * size, we use [androidx.compose.foundation.layout.RowScope.weight].
 */
@Composable
private fun LongPressMenuContent(
    header: LongPressable?,
    onUploaderClick: (() -> Unit)?,
    actions: List<LongPressAction>,
    runActionAndDismiss: (LongPressAction) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, bottom = 16.dp)
    ) {
        // landscape aspect ratio, 1:1 square in the limit
        val buttonHeight = MinButtonWidth
        // max width for the portrait/full-width header, measured in button widths
        val maxHeaderWidthInButtonsFullSpan = 5
        // width for the landscape/reduced header, measured in button widths
        val headerWidthInButtonsReducedSpan = 4
        val buttonsPerRow = (this.maxWidth / MinButtonWidth).toInt()
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .testTag("LongPressMenuGrid")
        ) {
            var actionIndex = if (header != null) -1 else 0 // -1 indicates the header
            while (actionIndex < actions.size) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("LongPressMenuGridRow")
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
                                    .weight((buttonsPerRow - rowIndex).toFloat())
                            )
                            break
                        } else if (actionIndex >= 0) {
                            val action = actions[actionIndex]
                            LongPressMenuButton(
                                icon = action.type.icon,
                                text = stringResource(action.type.label),
                                onClick = { runActionAndDismiss(action) },
                                enabled = action.enabled(),
                                modifier = Modifier
                                    .height(buttonHeight)
                                    .weight(1F)
                                    .testTag("LongPressMenuButton")
                            )
                            rowIndex += 1
                        } else if (maxHeaderWidthInButtonsFullSpan >= buttonsPerRow) {
                            // this branch is taken if the full-span header is going to fit on one
                            // line (i.e. on phones in portrait)
                            LongPressMenuHeader(
                                item = header!!, // surely not null since actionIndex < 0
                                onUploaderClick = onUploaderClick,
                                modifier = Modifier
                                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                                    // leave the height as small as possible, since it's the
                                    // only item on the row anyway
                                    .fillMaxWidth()
                                    .weight(maxHeaderWidthInButtonsFullSpan.toFloat())
                                    .testTag("LongPressMenuHeader")
                            )
                            rowIndex += maxHeaderWidthInButtonsFullSpan
                        } else {
                            // this branch is taken if the header will have some buttons to its
                            // right (i.e. on tablets, or on phones in landscape), and we have the
                            // header's reduced span be less than its full span so that when this
                            // branch is taken, at least two buttons will be on the right side of
                            // the header (just one button would look off).
                            LongPressMenuHeader(
                                item = header!!, // surely not null since actionIndex < 0
                                onUploaderClick = onUploaderClick,
                                modifier = Modifier
                                    .padding(start = 8.dp, top = 11.dp, bottom = 11.dp)
                                    .heightIn(min = ThumbnailHeight)
                                    .fillMaxWidth()
                                    .weight(headerWidthInButtonsReducedSpan.toFloat())
                                    .testTag("LongPressMenuHeader")
                            )
                            rowIndex += headerWidthInButtonsReducedSpan
                        }
                        actionIndex += 1
                    }
                }
            }
        }
    }
}

/**
 * A custom [BottomSheetDefaults.DragHandle] that also shows a small button on the right, that opens
 * the long press menu settings editor.
 */
@Composable
private fun LongPressMenuDragHandle(onEditActions: () -> Unit) {
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
            Box(
                Modifier
                    .size(1.dp)
                    .focusable()
                    .onFocusChanged { showFocusTrap = !it.isFocused }
            )
        }
        BottomSheetDefaults.DragHandle(
            modifier = Modifier.align(Alignment.Center)
        )

        // show a small button to open the editor, it's not an important button and it shouldn't
        // capture the user attention
        TooltipIconButton(
            onClick = onEditActions,
            icon = Icons.Default.Tune,
            contentDescription = stringResource(R.string.long_press_menu_actions_editor),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterEnd),
            iconModifier = Modifier
                .padding(2.dp)
                .size(16.dp)
        )
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

/**
 * A box that displays information about [item]: thumbnail, playlist item count, video duration,
 * title, channel, date, view count.
 *
 * @param item the item that was long pressed and whose info should be shown
 * @param onUploaderClick if not `null`, the [Text] containing the uploader will be made clickable
 * (even if `item.uploader` is `null`, in which case a placeholder uploader text will be shown)
 */
@Composable
private fun LongPressMenuHeader(
    item: LongPressable,
    onUploaderClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // thumbnail and decorations
            Box {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        placeholder = painterResource(R.drawable.placeholder_thumbnail_video),
                        error = painterResource(R.drawable.placeholder_thumbnail_video),
                        modifier = Modifier
                            .height(ThumbnailHeight)
                            .widthIn(max = ThumbnailHeight * 16 / 9) // 16:9 thumbnail at most
                            .clip(MaterialTheme.shapes.large)
                            .testTag("LongPressMenuHeaderThumbnail")
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
                                    .clip(MaterialTheme.shapes.medium)
                            ) {
                                Text(
                                    text = Localization.getDurationString(decoration.duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
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
                                    .clip(MaterialTheme.shapes.medium)
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
                                .size(width = 40.dp, height = ThumbnailHeight)
                                .clip(MaterialTheme.shapes.large)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = ctx.getString(
                                            R.string.items_in_playlist,
                                            decoration.itemCount
                                        )
                                    }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Default.PlaylistPlay,
                                    contentDescription = null
                                )
                                Text(
                                    text = Localization.localizeStreamCountMini(
                                        ctx,
                                        decoration.itemCount
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    null -> {}
                }
            }

            // title, channel and other textual information
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                // title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadedMarquee(edgeWidth = 12.dp)
                )

                // subtitle; see the javadocs of `getSubtitleAnnotatedString` and
                // `LongPressMenuHeaderSubtitle` to understand what is happening here
                val subtitle = getSubtitleAnnotatedString(
                    item = item,
                    showLink = onUploaderClick != null,
                    linkColor = MaterialTheme.customColors.onSurfaceVariantLink,
                    ctx = ctx
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))

                    if (onUploaderClick == null) {
                        LongPressMenuHeaderSubtitle(subtitle)
                    } else {
                        // only show the tooltip if the menu is actually clickable
                        val label = if (item.uploader != null) {
                            stringResource(R.string.show_channel_details_for, item.uploader)
                        } else {
                            stringResource(R.string.show_channel_details)
                        }
                        SimpleTooltipBox(
                            text = label
                        ) {
                            LongPressMenuHeaderSubtitle(
                                subtitle,
                                Modifier.clickable(onClick = onUploaderClick, onClickLabel = label)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Works in tandem with [getSubtitleAnnotatedString] and [getSubtitleInlineContent] to show the
 * subtitle line with a small material icon next to the uploader link.
 */
@Composable
private fun LongPressMenuHeaderSubtitle(subtitle: AnnotatedString, modifier: Modifier = Modifier) {
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        inlineContent = getSubtitleInlineContent(),
        modifier = modifier
            .fillMaxWidth()
            .fadedMarquee(edgeWidth = 12.dp)
            .testTag("ShowChannelDetails")
    )
}

/**
 * @param item information fields are from here and concatenated in a single string
 * @param showLink if true, a small material icon next to the uploader link; requires the [Text] to
 * use [getSubtitleInlineContent] later
 * @param linkColor which color to make the uploader link
 */
private fun getSubtitleAnnotatedString(
    item: LongPressable,
    showLink: Boolean,
    linkColor: Color,
    ctx: Context
) = buildAnnotatedString {
    var shouldAddSeparator = false

    // uploader (possibly with link)
    if (showLink) {
        withStyle(SpanStyle(color = linkColor)) {
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

    // localized upload date
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

    // localized view count
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
private fun getSubtitleInlineContent() = mapOf(
    "open_in_new" to InlineTextContent(
        placeholder = Placeholder(
            width = MaterialTheme.typography.bodyMedium.fontSize,
            height = MaterialTheme.typography.bodyMedium.fontSize,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.customColors.onSurfaceVariantLink
        )
    }
)

/**
 * A button to show in the long press menu with an [icon] and a label [text]. When pressed,
 * [onClick] will be called, and when long pressed a tooltip will appear with the full [text]. If
 * the button should appear disabled, make sure to set [enabled]`=false`.
 */
@Composable
private fun LongPressMenuButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    SimpleTooltipBox(
        text = text,
        modifier = modifier
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(start = 3.dp, top = 8.dp, end = 3.dp, bottom = 2.dp),
            border = null,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                FixedHeightCenteredText(
                    text = text,
                    lines = 2,
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
                        enabled = true,
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
            viewCount = 8765432,
            streamType = null,
            uploadDate = Either.left("16 years ago"),
            decoration = LongPressable.Decoration.Playlist(12)
        ),
        LongPressable(
            title = LoremIpsum().values.first(),
            url = "https://www.youtube.com/watch?v=YE7VzlLtp-4",
            thumbnailUrl = null,
            uploader = "Blender",
            viewCount = 8765432,
            streamType = StreamType.VIDEO_STREAM,
            uploadDate = Either.left("16 years ago"),
            decoration = LongPressable.Decoration.Duration(500)
        ),
        LongPressable(
            title = LoremIpsum().values.first(),
            url = "https://www.youtube.com/watch?v=YE7VzlLtp-4",
            thumbnailUrl = null,
            uploader = null,
            viewCount = null,
            streamType = null,
            uploadDate = null,
            decoration = null
        ),
        LongPressable(
            title = LoremIpsum().values.first(),
            url = "https://www.youtube.com/watch?v=YE7VzlLtp-4",
            thumbnailUrl = "https://i.ytimg.com/vi_webp/YE7VzlLtp-4/maxresdefault.webp",
            uploader = null,
            viewCount = null,
            streamType = StreamType.AUDIO_STREAM,
            uploadDate = null,
            decoration = LongPressable.Decoration.Duration(500)
        ),
        LongPressable(
            title = LoremIpsum().values.first(),
            url = "https://www.youtube.com/watch?v=YE7VzlLtp-4",
            thumbnailUrl = "https://i.ytimg.com/vi_webp/YE7VzlLtp-4/maxresdefault.webp",
            uploader = null,
            viewCount = null,
            streamType = StreamType.LIVE_STREAM,
            uploadDate = null,
            decoration = LongPressable.Decoration.Live
        ),
        LongPressable(
            title = LoremIpsum().values.first(),
            url = "https://www.youtube.com/watch?v=YE7VzlLtp-4",
            thumbnailUrl = null,
            uploader = null,
            viewCount = null,
            streamType = StreamType.AUDIO_LIVE_STREAM,
            uploadDate = Either.right(OffsetDateTime.now().minusSeconds(12)),
            decoration = LongPressable.Decoration.Playlist(1500)
        )
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
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            // longPressable is null when running the preview in an emulator for some reason...
            @Suppress("USELESS_ELVIS")
            LongPressMenuContent(
                header = longPressable ?: LongPressablePreviews().values.first(),
                onUploaderClick = {},
                actions = LongPressAction.Type.entries
                    // disable Enqueue actions just to show it off
                    .map { t -> LongPressAction(t, { t != EnqueueNext }) {} },
                runActionAndDismiss = {}
            )
        }
    }
}
