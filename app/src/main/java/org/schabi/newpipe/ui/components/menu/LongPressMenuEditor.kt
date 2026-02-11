/*
 * SPDX-FileCopyrightText: 2022-2025 The FlorisBoard Contributors <https://florisboard.org>
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.schabi.newpipe.ui.components.menu

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArtTrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.floor
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.letIf
import org.schabi.newpipe.ui.components.common.ScaffoldWithToolbar
import org.schabi.newpipe.ui.components.common.TooltipIconButton
import org.schabi.newpipe.ui.detectDragGestures
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.text.FixedHeightCenteredText

/**
 * An editor for the actions shown in the [LongPressMenu], that also allows enabling or disabling
 * the header. It allows the user to arrange the actions in any way, and to disable them by dragging
 * them to a disabled section.
 *
 * When making changes to this composable and to [LongPressMenuEditorState], make sure to test the
 * following use cases, and check that they still work:
 * - both the actions and the header can be dragged around
 * - the header can only be dragged to the first position in each section
 * - when a section is empty the None marker will appear
 * - actions and header are loaded from and stored to settings properly
 * - it is possible to move items around using DPAD on Android TVs, and there are no strange bugs
 * - when dragging items around, a Drag marker appears at the would-be position of the item being
 *   dragged, and the item being dragged is "picked up" and shown below the user's finger (at an
 *   offset to ensure the user can see the thing being dragged under their finger)
 * - when the view does not fit the page, it is possible to scroll without moving any item, and
 *   dragging an item towards the top/bottom of the page scrolls up/down
 *
 * @author This composable was originally copied from FlorisBoard, but was modified significantly.
 */
@Composable
fun LongPressMenuEditorPage(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val state = remember(gridState, coroutineScope) {
        LongPressMenuEditorState(context, gridState, coroutineScope)
    }

    DisposableEffect(Unit) {
        onDispose {
            // saves to settings the action arrangement and whether the header is enabled
            state.onDispose(context)
        }
    }

    ScaffoldWithToolbar(
        title = stringResource(R.string.long_press_menu_actions_editor),
        onBackClick = onBackClick,
        actions = {
            ResetToDefaultsButton { state.resetToDefaults(context) }
        }
    ) { paddingValues ->
        // if you want to forcefully "make the screen smaller" to test scrolling on Android TVs with
        // DPAD, add `.padding(horizontal = 350.dp)` here
        BoxWithConstraints(Modifier.padding(paddingValues)) {
            // otherwise we wouldn't know the amount of columns to handle the Up/Down key events
            val columns = maxOf(1, floor(this.maxWidth / MinButtonWidth).toInt())

            LazyVerticalGrid(
                modifier = Modifier
                    .safeDrawingPadding()
                    // `.detectDragGestures()` handles touch gestures on phones/tablets
                    .detectDragGestures(
                        beginDragGesture = state::beginDragTouch,
                        handleDragGestureChange = state::handleDragChangeTouch,
                        endDragGesture = state::completeDragAndCleanUp
                    )
                    // `.focusTarget().onKeyEvent()` handles DPAD on Android TVs
                    .focusTarget()
                    .onKeyEvent { event -> state.onKeyEvent(event, columns) }
                    .testTag("LongPressMenuEditorGrid"),
                // same width as the LongPressMenu
                columns = GridCells.Adaptive(MinButtonWidth),
                // Scrolling is handled manually through `.detectDragGestures` above: if the user
                // long-presses an item and then moves the finger, the item itself moves; otherwise,
                // if the click is too short or the user didn't click on an item, the view scrolls.
                userScrollEnabled = false,
                state = gridState
            ) {
                itemsIndexed(
                    state.items,
                    key = { _, item -> item.stableUniqueKey() },
                    span = { _, item -> GridItemSpan(item.columnSpan ?: maxLineSpan) }
                ) { i, item ->
                    ItemInListUi(
                        item = item,
                        focused = state.currentlyFocusedItem == i,
                        beingDragged = false,
                        // We only want placement animations: fade in/out animations interfere with
                        // items being replaced by a drag marker while being dragged around, and a
                        // fade in/out animation there does not make sense as the item was just
                        // "picked up". Furthermore there were strange moving animation artifacts
                        // when moving and releasing items quickly before their fade-out animation
                        // finishes, so it looks much more polished without fade in/out animations.
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                    )
                }
            }
            state.activeDragItem?.let { activeDragItem ->
                // draw it the same size as the selected item, so it properly appears that the user
                // picked up the item and is controlling it with their finger
                val size = with(LocalDensity.current) {
                    remember(state.activeDragSize) { state.activeDragSize.toSize().toDpSize() }
                }
                ItemInListUi(
                    item = activeDragItem,
                    focused = false,
                    beingDragged = true,
                    modifier = Modifier
                        .size(size)
                        .offset { state.activeDragPosition }
                        .offset(-size.width / 2, -size.height / 2)
                        .offset((-24).dp, (-24).dp)
                )
            }
        }
    }
}

/**
 * A button that when clicked opens a confirmation dialog, and then calls [doReset] to reset the
 * actions arrangement and whether the header is enabled to their default values.
 */
@Composable
private fun ResetToDefaultsButton(doReset: () -> Unit) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            text = { Text(stringResource(R.string.long_press_menu_reset_to_defaults_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    doReset()
                    showDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    TooltipIconButton(
        onClick = { showDialog = true },
        icon = Icons.Default.RestartAlt,
        contentDescription = stringResource(R.string.reset_to_defaults)
    )
}

/**
 * Renders either [ItemInList.EnabledCaption] or [ItemInList.HiddenCaption], i.e. the full-width
 * captions separating enabled and hidden items in the list.
 */
@Composable
private fun Caption(
    focused: Boolean,
    @StringRes title: Int,
    @StringRes description: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .letIf(focused) { border(2.dp, LocalContentColor.current) }
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(description),
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Renders all [ItemInList] except captions, that is, all items using a slot of the grid (or two
 * horizontal slots in case of the header).
 */
@Composable
private fun ActionOrHeaderBox(
    focused: Boolean,
    icon: ImageVector,
    @StringRes text: Int,
    contentColor: Color,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    horizontalPadding: Dp = 3.dp
) {
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(2.dp, contentColor.copy(alpha = 1f)).takeIf { focused },
        modifier = modifier.padding(
            horizontal = horizontalPadding,
            vertical = 5.dp
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            FixedHeightCenteredText(
                text = stringResource(text),
                lines = 2,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * @param item the [ItemInList] to render using either [Caption] or [ActionOrHeaderBox] with
 * different parameters
 * @param focused if `true`, a box will be drawn around the item to indicate that it is focused
 * (this will only ever be `true` when the user is navigating with DPAD, e.g. on Android TVs)
 * @param beingDragged if `true`, draw a semi-transparent background to show that the item is being
 * dragged
 */
@Composable
private fun ItemInListUi(
    item: ItemInList,
    focused: Boolean,
    beingDragged: Boolean,
    modifier: Modifier
) {
    when (item) {
        ItemInList.EnabledCaption -> {
            Caption(
                modifier = modifier,
                focused = focused,
                title = R.string.long_press_menu_enabled_actions,
                description = R.string.long_press_menu_enabled_actions_description
            )
        }

        ItemInList.HiddenCaption -> {
            Caption(
                modifier = modifier,
                focused = focused,
                title = R.string.long_press_menu_hidden_actions,
                description = R.string.long_press_menu_hidden_actions_description
            )
        }

        is ItemInList.Action -> {
            ActionOrHeaderBox(
                modifier = modifier,
                focused = focused,
                icon = item.type.icon,
                text = item.type.label,
                contentColor = MaterialTheme.colorScheme.onSurface,
                backgroundColor = MaterialTheme.colorScheme.surface
                    .letIf(beingDragged) { copy(alpha = 0.7f) }
            )
        }

        ItemInList.HeaderBox -> {
            ActionOrHeaderBox(
                modifier = modifier,
                focused = focused,
                icon = Icons.Default.ArtTrack,
                text = R.string.long_press_menu_header,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer
                    .letIf(beingDragged) { copy(alpha = 0.85f) },
                horizontalPadding = 12.dp
            )
        }

        ItemInList.NoneMarker -> {
            ActionOrHeaderBox(
                modifier = modifier,
                focused = focused,
                icon = Icons.Default.Close,
                text = R.string.none,
                // 0.38f is the same alpha that the Material3 library applies for disabled buttons
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }

        is ItemInList.DragMarker -> {
            ActionOrHeaderBox(
                modifier = modifier,
                focused = focused,
                icon = Icons.Default.DragHandle,
                text = R.string.detail_drag_description,
                // this should be just barely visible, we could even decide to hide it completely
                // at some point, since it doesn't provide much of a useful hint
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        }
    }
}

@Preview
@Preview(device = "spec:width=1080px,height=1000px,dpi=440")
@Composable
private fun LongPressMenuEditorPagePreview() {
    AppTheme {
        LongPressMenuEditorPage { }
    }
}

private class ItemInListPreviewProvider : CollectionPreviewParameterProvider<ItemInList>(
    listOf(ItemInList.HeaderBox, ItemInList.DragMarker(1), ItemInList.NoneMarker) +
        LongPressAction.Type.entries.take(3).map { ItemInList.Action(it) }
)

@Preview
@Composable
private fun QuickActionButtonPreview(
    @PreviewParameter(ItemInListPreviewProvider::class) itemInList: ItemInList
) {
    AppTheme {
        Surface {
            ItemInListUi(
                item = itemInList,
                focused = itemInList.stableUniqueKey() % 2 == 0,
                beingDragged = false,
                modifier = Modifier.width(MinButtonWidth * (itemInList.columnSpan ?: 4))
            )
        }
    }
}
