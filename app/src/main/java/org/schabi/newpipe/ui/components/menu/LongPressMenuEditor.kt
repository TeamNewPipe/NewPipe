/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import org.schabi.newpipe.ui.detectDragGestures
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.letIf
import org.schabi.newpipe.util.text.FixedHeightCenteredText

/**
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
 * @author This composable was originally copied from FlorisBoard.
 */
@Composable
fun LongPressMenuEditor(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val state = remember(gridState, coroutineScope) {
        LongPressMenuEditorState(context, gridState, coroutineScope)
    }

    DisposableEffect(Unit) {
        onDispose {
            state.onDispose(context)
        }
    }

    // test scrolling on Android TV by adding `.padding(horizontal = 350.dp)` here
    BoxWithConstraints(modifier) {
        // otherwise we wouldn't know the amount of columns to handle the Up/Down key events
        val columns = maxOf(1, floor(this.maxWidth / MinButtonWidth).toInt())

        LazyVerticalGrid(
            modifier = Modifier
                .safeDrawingPadding()
                .detectDragGestures(
                    beginDragGesture = state::beginDragGesture,
                    handleDragGestureChange = state::handleDragGestureChange,
                    endDragGesture = state::completeDragGestureAndCleanUp
                )
                // `.focusTarget().onKeyEvent()` handles DPAD on Android TVs
                .focusTarget()
                .onKeyEvent { event -> state.onKeyEvent(event, columns) },
            // same width as the LongPressMenu
            columns = GridCells.Adaptive(MinButtonWidth),
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
                    selected = state.currentlyFocusedItem == i,
                    // We only want placement animations: fade in/out animations interfere with
                    // items being replaced by a drag marker while being dragged around, and a fade
                    // in/out animation there does not make sense as the item was just "picked up".
                    // Furthermore there are strange moving animation artifacts when moving and
                    // releasing items quickly before their fade-out animation finishes.
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                )
            }
        }
        state.activeDragItem?.let { activeDragItem ->
            // draw it the same size as the selected item,
            val size = with(LocalDensity.current) {
                remember(state.activeDragSize) { state.activeDragSize.toSize().toDpSize() }
            }
            ItemInListUi(
                item = activeDragItem,
                selected = true,
                modifier = Modifier
                    .size(size)
                    .offset { state.activeDragPosition }
                    .offset(-size.width / 2, -size.height / 2)
                    .offset((-24).dp, (-24).dp)
            )
        }
    }
}

@Composable
private fun Subheader(
    selected: Boolean,
    @StringRes title: Int,
    @StringRes description: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .letIf(selected) { border(2.dp, LocalContentColor.current) }
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

@Composable
private fun ActionOrHeaderBox(
    selected: Boolean,
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
        border = BorderStroke(2.dp, contentColor.copy(alpha = 1f)).takeIf { selected },
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

@Composable
private fun ItemInListUi(
    item: ItemInList,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    when (item) {
        ItemInList.EnabledCaption -> {
            Subheader(
                modifier = modifier,
                selected = selected,
                title = R.string.long_press_menu_enabled_actions,
                description = R.string.long_press_menu_enabled_actions_description
            )
        }

        ItemInList.HiddenCaption -> {
            Subheader(
                modifier = modifier,
                selected = selected,
                title = R.string.long_press_menu_hidden_actions,
                description = R.string.long_press_menu_hidden_actions_description
            )
        }

        is ItemInList.Action -> {
            ActionOrHeaderBox(
                modifier = modifier,
                selected = selected,
                icon = item.type.icon,
                text = item.type.label,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }

        ItemInList.HeaderBox -> {
            ActionOrHeaderBox(
                modifier = modifier,
                selected = selected,
                icon = Icons.Default.ArtTrack,
                text = R.string.long_press_menu_header,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                horizontalPadding = 12.dp
            )
        }

        ItemInList.NoneMarker -> {
            ActionOrHeaderBox(
                modifier = modifier,
                selected = selected,
                icon = Icons.Default.Close,
                text = R.string.none,
                // 0.38f is the same alpha that the Material3 library applies for disabled buttons
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }

        is ItemInList.DragMarker -> {
            ActionOrHeaderBox(
                modifier = modifier,
                selected = selected,
                icon = Icons.Default.DragHandle,
                text = R.string.detail_drag_description,
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        }
    }
}

@Preview
@Preview(device = "spec:width=1080px,height=1000px,dpi=440")
@Composable
private fun LongPressMenuEditorPreview() {
    AppTheme {
        Surface {
            LongPressMenuEditor()
        }
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
                selected = itemInList.stableUniqueKey() % 2 == 0,
                modifier = Modifier.width(MinButtonWidth)
            )
        }
    }
}
