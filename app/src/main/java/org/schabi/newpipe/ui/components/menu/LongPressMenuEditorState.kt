package org.schabi.newpipe.ui.components.menu

import android.util.Log
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Companion.DefaultEnabledActions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * This class is very tied to [LongPressMenuEditor] and interacts with the UI layer through
 * [gridState]. Therefore it's not a view model but rather a state holder class, see
 * https://developer.android.com/topic/architecture/ui-layer/stateholders#ui-logic.
 *
 * See the javadoc of [LongPressMenuEditor] to understand which behaviors you should test for when
 * changing this class.
 */
@Stable
class LongPressMenuEditorState(
    val gridState: LazyGridState,
    val coroutineScope: CoroutineScope,
) {
    // We get the current arrangement once and do not observe on purpose
    val items = run {
        // TODO load from settings
        val headerEnabled = true
        val actionArrangement = DefaultEnabledActions
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
    var activeDragItem by mutableStateOf<ItemInList?>(null)
    var activeDragPosition by mutableStateOf(IntOffset.Zero)
    var activeDragSize by mutableStateOf(IntSize.Zero)
    var currentlyFocusedItem by mutableIntStateOf(-1)
    var autoScrollJob by mutableStateOf<Job?>(null)
    var autoScrollSpeed by mutableFloatStateOf(0f)

    private fun findItemForOffsetOrClosestInRow(offset: IntOffset): LazyGridItemInfo? {
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

    private fun autoScrollSpeedFromTouchPos(
        touchPos: IntOffset,
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

    /**
     * Called not just for drag gestures initiated by moving the finger, but also with DPAD's Enter.
     */
    private fun beginDragGesture(pos: IntOffset, rawItem: LazyGridItemInfo) {
        if (activeDragItem != null) return
        val item = items.getOrNull(rawItem.index) ?: return
        if (item.isDraggable) {
            items[rawItem.index] = ItemInList.DragMarker(item.columnSpan)
            activeDragItem = item
            activeDragPosition = pos
            activeDragSize = rawItem.size
        }
    }

    /**
     * This beginDragGesture() overload is only called when moving the finger (not on DPAD's Enter).
     */
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

    /**
     * Called not just for drag gestures by moving the finger, but also with DPAD's events.
     */
    private fun handleDragGestureChange(dragItem: ItemInList, rawItem: LazyGridItemInfo) {
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

    /**
     * This handleDragGestureChange() overload is only called when moving the finger
     * (not on DPAD's events).
     */
    fun handleDragGestureChange(pos: IntOffset, posChangeForScrolling: Offset) {
        val dragItem = activeDragItem
        if (dragItem == null) {
            // when the user clicks outside of any draggable item, or if the user did not long-press
            // on an item to begin with, let the list be scrolled
            gridState.dispatchRawDelta(-posChangeForScrolling.y)
            return
        }
        autoScrollSpeed = autoScrollSpeedFromTouchPos(pos)
        activeDragPosition = pos
        val rawItem = findItemForOffsetOrClosestInRow(pos) ?: return
        handleDragGestureChange(dragItem, rawItem)
    }

    /**
     * Called in multiple places, e.g. when the finger stops touching, or with DPAD events.
     */
    fun completeDragGestureAndCleanUp() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        autoScrollSpeed = 0f

        activeDragItem?.let { dragItem ->
            val dragMarkerIndex = items.indexOfFirst { it is ItemInList.DragMarker }
            if (dragMarkerIndex >= 0) {
                items[dragMarkerIndex] = dragItem
            }
        }
        activeDragItem = null
        activeDragPosition = IntOffset.Zero
        activeDragSize = IntSize.Zero
    }

    /**
     * Handles DPAD events on Android TVs.
     */
    fun onKeyEvent(event: KeyEvent, columns: Int): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            if (event.type == KeyEventType.KeyUp &&
                event.key == Key.DirectionDown &&
                currentlyFocusedItem < 0
            ) {
                currentlyFocusedItem = 0
            }
            return false
        }
        var focusedItem = currentlyFocusedItem
        when (event.key) {
            Key.DirectionUp -> {
                if (focusedItem < 0) {
                    return false
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
                    return false
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
                    return false
                } else {
                    focusedItem -= 1
                }
            }

            Key.DirectionRight -> {
                if (focusedItem >= items.size - 1) {
                    return false
                } else {
                    focusedItem += 1
                }
            }

            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> if (activeDragItem == null) {
                val rawItem = gridState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == focusedItem }
                    ?: return false
                beginDragGesture(rawItem.offset, rawItem)
                return true
            } else {
                completeDragGestureAndCleanUp()
                return true
            }

            else -> return false
        }

        currentlyFocusedItem = focusedItem
        if (focusedItem < 0) {
            // not checking for focusedItem>=items.size because it's impossible for it
            // to reach that value, and that's because we assume that there is nothing
            // else focusable *after* this view. This way we don't need to cleanup the
            // drag gestures when the user reaches the end, which would be confusing as
            // then there would be no indication of the current cursor position at all.
            completeDragGestureAndCleanUp()
            return false
        } else if (focusedItem >= items.size) {
            Log.w(TAG, "Invalid focusedItem $focusedItem: >= items size ${items.size}")
        }

        val rawItem = gridState.layoutInfo.visibleItemsInfo
            .minByOrNull { abs(it.index - focusedItem) }
            ?: return false // no item is visible at all, impossible case

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
        return true
    }

    fun onDispose() {
        completeDragGestureAndCleanUp()
        // TODO save to settings
    }
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
