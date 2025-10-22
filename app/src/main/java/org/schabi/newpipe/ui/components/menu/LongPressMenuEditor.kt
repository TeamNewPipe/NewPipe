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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.detectDragGestures
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.text.FixedHeightCenteredText
import kotlin.math.min

// TODO implement accessibility for this, to allow using this with a DPAD (e.g. Android TV)
@Composable
fun LongPressMenuEditor() {
    // We get the current arrangement once and do not observe on purpose
    // TODO load from settings
    val headerEnabled = remember { false } // true }
    val actionArrangement = remember { LongPressAction.Type.entries } // DefaultEnabledActions }
    val items = remember(headerEnabled, actionArrangement) {
        sequence {
            yield(ItemInList.EnabledCaption)
            if (headerEnabled) {
                yield(ItemInList.HeaderBox)
            }
            yieldAll(
                actionArrangement
                    .map { ItemInList.Action(it) }
                    .ifEmpty { if (headerEnabled) listOf() else listOf(ItemInList.NoneMarker) }
            )
            yield(ItemInList.HiddenCaption)
            if (!headerEnabled) {
                yield(ItemInList.HeaderBox)
            }
            yieldAll(
                LongPressAction.Type.entries
                    .filter { !actionArrangement.contains(it) }
                    .map { ItemInList.Action(it) }
                    .ifEmpty { if (headerEnabled) listOf(ItemInList.NoneMarker) else listOf() }
            )
        }.toList().toMutableStateList()
    }

    val gridState = rememberLazyGridState()
    var activeDragItem by remember { mutableStateOf<ItemInList?>(null) }
    var activeDragPosition by remember { mutableStateOf(IntOffset.Zero) }
    var activeDragSize by remember { mutableStateOf(IntSize.Zero) }
    var currentlyFocusedItem by remember { mutableIntStateOf(-1) }

    fun findItemForOffsetOrClosestInRow(offset: IntOffset): LazyGridItemInfo? {
        var closestItemInRow: LazyGridItemInfo? = null
        // Using manual for loop with indices instead of firstOrNull() because this method gets
        // called a lot and firstOrNull allocates an iterator for each call
        for (index in gridState.layoutInfo.visibleItemsInfo.indices) {
            val item = gridState.layoutInfo.visibleItemsInfo[index]
            if (offset.y in item.offset.y..(item.offset.y + item.size.height)) {
                if (offset.x in item.offset.x..(item.offset.x + item.size.width)) {
                    return item
                }
                closestItemInRow = item
            }
        }
        return closestItemInRow
    }

    fun beginDragGesture(pos: IntOffset) {
        val rawItem = findItemForOffsetOrClosestInRow(pos) ?: return
        val item = items.getOrNull(rawItem.index) ?: return
        if (item.isDraggable) {
            items[rawItem.index] = ItemInList.DragMarker(item.columnSpan)
            activeDragItem = item
            activeDragPosition = pos
            activeDragSize = rawItem.size
        }
    }

    fun handleDragGestureChange(pos: IntOffset, posChange: Offset) {
        val dragItem = activeDragItem
        if (dragItem == null) {
            // when the user clicks outside of any draggable item, let the list be scrolled
            gridState.dispatchRawDelta(-posChange.y)
            return
        }
        activeDragPosition = pos
        val rawItem = findItemForOffsetOrClosestInRow(pos) ?: return

        // compute where the DragMarker will go (we need to do special logic to make sure the
        // HeaderBox always sticks right after EnabledCaption or HiddenCaption)
        val nextDragMarkerIndex = if (dragItem == ItemInList.HeaderBox) {
            val hiddenCaptionIndex = items.indexOf(ItemInList.HiddenCaption)
            if (rawItem.index < hiddenCaptionIndex) {
                1 // i.e. right after the EnabledCaption
            } else {
                hiddenCaptionIndex + 1 // i.e. right after the HiddenCaption
            }
        } else {
            var i = rawItem.index
            // make sure it is not possible to move items in between a *Caption and a HeaderBox
            if (!items[i].isDraggable) i += 1
            if (items[i] == ItemInList.HeaderBox) i += 1
            i
        }

        // adjust the position of the DragMarker
        items.removeIf { it is ItemInList.DragMarker }
        items.add(min(nextDragMarkerIndex, items.size), ItemInList.DragMarker(dragItem.columnSpan))

        // add or remove NoneMarkers as needed
        items.removeIf { it is ItemInList.NoneMarker }
        val hiddenCaptionIndex = items.indexOf(ItemInList.HiddenCaption)
        if (hiddenCaptionIndex == items.size - 1) {
            items.add(ItemInList.NoneMarker)
        } else if (hiddenCaptionIndex == 1) {
            items.add(1, ItemInList.NoneMarker)
        }
    }

    fun completeDragGestureAndCleanUp() {
        val dragItem = activeDragItem
        if (dragItem != null) {
            val dragMarkerIndex = items.indexOfFirst { it is ItemInList.DragMarker }
            if (dragMarkerIndex >= 0) {
                items[dragMarkerIndex] = dragItem
            }
        }
        activeDragItem = null
        activeDragPosition = IntOffset.Zero
        activeDragSize = IntSize.Zero
    }

    DisposableEffect(Unit) {
        onDispose {
            completeDragGestureAndCleanUp()
            // TODO save to settings
        }
    }

    LazyVerticalGrid(
        modifier = Modifier
            .safeDrawingPadding()
            .detectDragGestures(
                beginDragGesture = ::beginDragGesture,
                handleDragGestureChange = ::handleDragGestureChange,
                endDragGesture = ::completeDragGestureAndCleanUp,
            ),
        // same width as the LongPressMenu
        columns = GridCells.Adaptive(MinButtonWidth),
        userScrollEnabled = false,
        state = gridState,
    ) {
        itemsIndexed(
            items,
            key = { _, item -> item.stableUniqueKey() },
            span = { _, item -> GridItemSpan(item.columnSpan ?: maxLineSpan) },
        ) { i, item ->
            ItemInListUi(
                item = item,
                selected = currentlyFocusedItem == i,
                modifier = Modifier.animateItem()
            )
        }
    }
    if (activeDragItem != null) {
        val size = with(LocalDensity.current) {
            remember(activeDragSize) { activeDragSize.toSize().toDpSize() }
        }
        ItemInListUi(
            item = activeDragItem!!,
            selected = true,
            modifier = Modifier
                .size(size)
                .offset { activeDragPosition }
                .offset(-size.width / 2, -size.height / 2),
        )
    }
}

sealed class ItemInList(val isDraggable: Boolean, open val columnSpan: Int? = 1) {
    // decoration items (i.e. text subheaders)
    object EnabledCaption : ItemInList(isDraggable = false, columnSpan = null /* i.e. all line */)
    object HiddenCaption : ItemInList(isDraggable = false, columnSpan = null /* i.e. all line */)

    // actual draggable actions (+ a header)
    object HeaderBox : ItemInList(isDraggable = true, columnSpan = 2)
    data class Action(val type: LongPressAction.Type) : ItemInList(isDraggable = true)

    // markers
    object NoneMarker : ItemInList(isDraggable = false)
    data class DragMarker(override val columnSpan: Int?) : ItemInList(isDraggable = false)

    fun stableUniqueKey(): Int {
        return when (this) {
            is Action -> this.type.ordinal
            NoneMarker -> LongPressAction.Type.entries.size + 0
            HeaderBox -> LongPressAction.Type.entries.size + 1
            EnabledCaption -> LongPressAction.Type.entries.size + 2
            HiddenCaption -> LongPressAction.Type.entries.size + 3
            is DragMarker -> LongPressAction.Type.entries.size + 4 + (this.columnSpan ?: 0)
        }
    }
}

inline fun <T> T.letIf(condition: Boolean, block: T.() -> T): T =
    if (condition) block(this) else this

@Composable
private fun Subheader(
    selected: Boolean,
    @StringRes title: Int,
    @StringRes description: Int,
    modifier: Modifier = Modifier,
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
            style = MaterialTheme.typography.bodyMedium,
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
    horizontalPadding: Dp = 3.dp,
) {
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(2.dp, contentColor).takeIf { selected },
        modifier = modifier.padding(
            horizontal = horizontalPadding,
            vertical = 5.dp,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            FixedHeightCenteredText(
                text = stringResource(text),
                lines = 2,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ItemInListUi(
    item: ItemInList,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    when (item) {
        ItemInList.EnabledCaption -> {
            Subheader(
                modifier = modifier,
                selected = selected,
                title = R.string.long_press_menu_enabled_actions,
                description = R.string.long_press_menu_enabled_actions_description,
            )
        }
        ItemInList.HiddenCaption -> {
            Subheader(
                modifier = modifier,
                selected = selected,
                title = R.string.long_press_menu_hidden_actions,
                description = R.string.long_press_menu_hidden_actions_description,
            )
        }
        is ItemInList.Action -> {
            ActionOrHeaderBox(
                modifier = modifier,
                selected = selected,
                icon = item.type.icon,
                text = item.type.label,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        ItemInList.HeaderBox -> {
            ActionOrHeaderBox(
                modifier = modifier,
                selected = selected,
                icon = Icons.Default.ArtTrack,
                text = R.string.header,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                horizontalPadding = 12.dp,
            )
        }
        ItemInList.NoneMarker -> {
            ActionOrHeaderBox(
                modifier = modifier,
                selected = selected,
                icon = Icons.Default.Close,
                text = R.string.none,
                // 0.38f is the same alpha that the Material3 library applies for disabled buttons
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
        is ItemInList.DragMarker -> {
            ActionOrHeaderBox(
                modifier = modifier,
                selected = selected,
                icon = Icons.Default.DragHandle,
                text = R.string.detail_drag_description,
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            )
        }
    }
}

@Preview
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
