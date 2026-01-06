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

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Companion.DefaultEnabledActions
import org.schabi.newpipe.ui.detectDragGestures
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.text.FixedHeightCenteredText
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal const val TAG = "LongPressMenuEditor"

// TODO padding doesn't seem to work as expected when the list becomes scrollable?
@Composable
fun LongPressMenuEditor(modifier: Modifier = Modifier) {
    // We get the current arrangement once and do not observe on purpose
    // TODO load from settings
    val headerEnabled = remember { true }
    val actionArrangement = remember { DefaultEnabledActions }
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

    // variables for handling drag, focus, and autoscrolling when finger is at top/bottom
    val gridState = rememberLazyGridState()
    var activeDragItem by remember { mutableStateOf<ItemInList?>(null) }
    var activeDragPosition by remember { mutableStateOf(IntOffset.Zero) }
    var activeDragSize by remember { mutableStateOf(IntSize.Zero) }
    var currentlyFocusedItem by remember { mutableIntStateOf(-1) }
    val coroutineScope = rememberCoroutineScope()
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    var autoScrollSpeed by remember { mutableFloatStateOf(0f) } // -1, 0 or 1

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

    // called not just for drag gestures initiated by moving the finger, but also with DPAD's Enter
    fun beginDragGesture(pos: IntOffset, rawItem: LazyGridItemInfo) {
        if (activeDragItem != null) return
        val item = items.getOrNull(rawItem.index) ?: return
        if (item.isDraggable) {
            items[rawItem.index] = ItemInList.DragMarker(item.columnSpan)
            activeDragItem = item
            activeDragPosition = pos
            activeDragSize = rawItem.size
        }
    }

    // this beginDragGesture() overload is only called when moving the finger (not on DPAD's Enter)
    fun beginDragGesture(pos: IntOffset, wasLongPressed: Boolean) {
        if (!wasLongPressed) {
            // items can be dragged around only if they are long-pressed;
            // use the drag as scroll otherwise
            return
        }
        val rawItem = findItemForOffsetOrClosestInRow(pos) ?: return
        beginDragGesture(pos, rawItem)
        autoScrollSpeed = 0f
        autoScrollJob = coroutineScope.launch {
            while (isActive) {
                if (autoScrollSpeed != 0f) {
                    gridState.scrollBy(autoScrollSpeed)
                }
                delay(16L) // roughly 60 FPS
            }
        }
    }

    // called not just for drag gestures by moving the finger, but also with DPAD's events
    fun handleDragGestureChange(dragItem: ItemInList, rawItem: LazyGridItemInfo) {
        val prevDragMarkerIndex = items.indexOfFirst { it is ItemInList.DragMarker }
            .takeIf { it >= 0 } ?: return // impossible situation, DragMarker is always in the list

        // compute where the DragMarker will go (we need to do special logic to make sure the
        // HeaderBox always sticks right after EnabledCaption or HiddenCaption)
        val nextDragMarkerIndex = if (dragItem == ItemInList.HeaderBox) {
            val hiddenCaptionIndex = items.indexOf(ItemInList.HiddenCaption)
            if (rawItem.index < hiddenCaptionIndex) {
                1 // i.e. right after the EnabledCaption
            } else if (prevDragMarkerIndex < hiddenCaptionIndex) {
                hiddenCaptionIndex // i.e. right after the HiddenCaption
            } else {
                hiddenCaptionIndex + 1 // i.e. right after the HiddenCaption
            }
        } else {
            var i = rawItem.index
            // make sure it is not possible to move items in between a *Caption and a HeaderBox
            if (items[i].isCaption) i += 1
            if (i < items.size && items[i] == ItemInList.HeaderBox) i += 1
            if (rawItem.index in (prevDragMarkerIndex + 1)..<i) i -= 1
            i
        }

        // no need to do anything if the DragMarker is already at the right place
        if (prevDragMarkerIndex == nextDragMarkerIndex) {
            return
        }

        // adjust the position of the DragMarker
        items.removeAt(prevDragMarkerIndex)
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

    // this handleDragGestureChange() overload is only called when moving the finger
    // (not on DPAD's events)
    fun handleDragGestureChange(pos: IntOffset, posChangeForScrolling: Offset) {
        val dragItem = activeDragItem
        if (dragItem == null) {
            // when the user clicks outside of any draggable item, or if the user did not long-press
            // on an item to begin with, let the list be scrolled
            gridState.dispatchRawDelta(-posChangeForScrolling.y)
            return
        }
        autoScrollSpeed = autoScrollSpeedFromTouchPos(pos, gridState)
        activeDragPosition = pos
        val rawItem = findItemForOffsetOrClosestInRow(pos) ?: return
        handleDragGestureChange(dragItem, rawItem)
    }

    // called in multiple places both, e.g. when the finger stops touching, or with DPAD events
    fun completeDragGestureAndCleanUp() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        autoScrollSpeed = 0f

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

    // test scrolling on Android TV by adding `.padding(horizontal = 350.dp)` here
    BoxWithConstraints(modifier) {
        // otherwise we wouldn't know the amount of columns to handle the Up/Down key events
        val columns = maxOf(1, floor(this.maxWidth / MinButtonWidth).toInt())

        LazyVerticalGrid(
            modifier = Modifier
                .safeDrawingPadding()
                .detectDragGestures(
                    beginDragGesture = ::beginDragGesture,
                    handleDragGestureChange = ::handleDragGestureChange,
                    endDragGesture = ::completeDragGestureAndCleanUp,
                )
                // this huge .focusTarget().onKeyEvent() block just handles DPAD on Android TVs
                .focusTarget()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        if (event.type == KeyEventType.KeyUp &&
                            event.key == Key.DirectionDown &&
                            currentlyFocusedItem < 0
                        ) {
                            currentlyFocusedItem = 0
                        }
                        return@onKeyEvent false
                    }
                    var focusedItem = currentlyFocusedItem
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (focusedItem < 0) {
                                return@onKeyEvent false
                            } else if (items[focusedItem].columnSpan == null) {
                                focusedItem -= 1
                            } else {
                                // go to the previous line
                                var remaining = columns
                                while (true) {
                                    focusedItem -= 1
                                    if (focusedItem < 0) {
                                        break
                                    }
                                    remaining -= items[focusedItem].columnSpan ?: columns
                                    if (remaining <= 0) {
                                        break
                                    }
                                }
                            }
                        }

                        Key.DirectionDown -> {
                            if (focusedItem >= items.size - 1) {
                                return@onKeyEvent false
                            } else if (focusedItem < 0 || items[focusedItem].columnSpan == null) {
                                focusedItem += 1
                            } else {
                                // go to the next line
                                var remaining = columns
                                while (true) {
                                    focusedItem += 1
                                    if (focusedItem >= items.size - 1) {
                                        break
                                    }
                                    remaining -= items[focusedItem].columnSpan ?: columns
                                    if (remaining <= 0) {
                                        break
                                    }
                                }
                            }
                        }

                        Key.DirectionLeft -> {
                            if (focusedItem < 0) {
                                return@onKeyEvent false
                            } else {
                                focusedItem -= 1
                            }
                        }

                        Key.DirectionRight -> {
                            if (focusedItem >= items.size - 1) {
                                return@onKeyEvent false
                            } else {
                                focusedItem += 1
                            }
                        }

                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> if (activeDragItem == null) {
                            val rawItem = gridState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == focusedItem }
                                ?: return@onKeyEvent false
                            beginDragGesture(rawItem.offset, rawItem)
                            return@onKeyEvent true
                        } else {
                            completeDragGestureAndCleanUp()
                            return@onKeyEvent true
                        }

                        else -> return@onKeyEvent false
                    }

                    currentlyFocusedItem = focusedItem
                    if (focusedItem < 0) {
                        // not checking for focusedItem>=items.size because it's impossible for it
                        // to reach that value, and that's because we assume that there is nothing
                        // else focusable *after* this view. This way we don't need to cleanup the
                        // drag gestures when the user reaches the end, which would be confusing as
                        // then there would be no indication of the current cursor position at all.
                        completeDragGestureAndCleanUp()
                        return@onKeyEvent false
                    } else if (focusedItem >= items.size) {
                        Log.w(TAG, "Invalid focusedItem $focusedItem: >= items size ${items.size}")
                    }

                    val rawItem = gridState.layoutInfo.visibleItemsInfo
                        .minByOrNull { abs(it.index - focusedItem) }
                        ?: return@onKeyEvent false // no item is visible at all, impossible case

                    // If the item we are going to focus is not visible or is close to the boundary,
                    // scroll to it. Note that this will cause the "drag item" to appear misplaced,
                    // since the drag item's position is set to the position of the focused item
                    // before scrolling. However, it's not worth overcomplicating the logic just for
                    // correcting the position of a drag hint on Android TVs.
                    val h = rawItem.size.height
                    if (rawItem.index != focusedItem ||
                        rawItem.offset.y <= gridState.layoutInfo.viewportStartOffset + 0.8 * h ||
                        rawItem.offset.y + 1.8 * h >= gridState.layoutInfo.viewportEndOffset
                    ) {
                        coroutineScope.launch {
                            gridState.scrollToItem(focusedItem, -(0.8 * h).toInt())
                        }
                    }

                    val dragItem = activeDragItem
                    if (dragItem != null) {
                        // This will mostly bring the drag item to the right position, but will
                        // misplace it if the view just scrolled (see above), or if the DragMarker's
                        // position is moved past HiddenCaption by handleDragGestureChange() below.
                        // However, it's not worth overcomplicating the logic just for correcting
                        // the position of a drag hint on Android TVs.
                        activeDragPosition = rawItem.offset
                        handleDragGestureChange(dragItem, rawItem)
                    }
                    return@onKeyEvent true
                },
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
                    // We only want placement animations: fade in/out animations interfere with
                    // items being replaced by a drag marker while being dragged around, and a fade
                    // in/out animation there does not make sense as the item was just "picked up".
                    // Furthermore there are strange moving animation artifacts when moving and
                    // releasing items quickly before their fade-out animation finishes.
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                )
            }
        }
        if (activeDragItem != null) {
            // draw it the same size as the selected item,
            val size = with(LocalDensity.current) {
                remember(activeDragSize) { activeDragSize.toSize().toDpSize() }
            }
            ItemInListUi(
                item = activeDragItem!!,
                selected = true,
                modifier = Modifier
                    .size(size)
                    .offset { activeDragPosition }
                    .offset(-size.width / 2, -size.height / 2)
                    .offset((-24).dp, (-24).dp),
            )
        }
    }
}

fun autoScrollSpeedFromTouchPos(
    touchPos: IntOffset,
    gridState: LazyGridState,
    maxSpeed: Float = 20f,
    scrollIfCloseToBorderPercent: Float = 0.2f,
): Float {
    val heightPosRatio = touchPos.y.toFloat() /
            (gridState.layoutInfo.viewportEndOffset - gridState.layoutInfo.viewportStartOffset)
    // just a linear piecewise function, sets higher speeds the closer the finger is to the border
    return maxSpeed * max(
        // proportionally positive speed when close to the bottom border
        (heightPosRatio - 1) / scrollIfCloseToBorderPercent + 1,
        min(
            // proportionally negative speed when close to the top border
            heightPosRatio / scrollIfCloseToBorderPercent - 1,
            // don't scroll at all if not close to any border
            0f
        )
    )
}

sealed class ItemInList(
    val isDraggable: Boolean = false,
    val isCaption: Boolean = false,
    open val columnSpan: Int? = 1,
) {
    // decoration items (i.e. text subheaders)
    object EnabledCaption : ItemInList(isCaption = true, columnSpan = null /* i.e. all line */)
    object HiddenCaption : ItemInList(isCaption = true, columnSpan = null /* i.e. all line */)

    // actual draggable actions (+ a header)
    object HeaderBox : ItemInList(isDraggable = true, columnSpan = 2)
    data class Action(val type: LongPressAction.Type) : ItemInList(isDraggable = true)

    // markers
    object NoneMarker : ItemInList()
    data class DragMarker(override val columnSpan: Int?) : ItemInList()

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
        border = BorderStroke(2.dp, contentColor.copy(alpha = 1f)).takeIf { selected },
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
