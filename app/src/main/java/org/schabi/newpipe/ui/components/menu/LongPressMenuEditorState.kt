package org.schabi.newpipe.ui.components.menu

import android.content.Context
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
import kotlin.collections.ifEmpty
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "LongPressMenuEditorStat"

/**
 * Holds a list of items (from a fixed set of items, see [ItemInList]) to show in a `LazyGrid`, and
 * allows performing drag operations on this list, both via touch and via DPAD (e.g. Android TVs).
 * Loads the list state (composed of whether the header is enabled and of the action arrangement)
 * from settings upon initialization, and only persists changes back to settings when [onDispose] is
 * called.
 *
 * This class is very tied to [LongPressMenuEditorPage] and interacts with the UI layer through
 * [gridState]. Therefore it's not a view model but rather a state holder class, see
 * https://developer.android.com/topic/architecture/ui-layer/stateholders#ui-logic.
 *
 * See the javadoc of [LongPressMenuEditorPage] to understand which behaviors you should test for
 * when changing this class.
 */
@Stable
class LongPressMenuEditorState(
    context: Context,
    val gridState: LazyGridState,
    val coroutineScope: CoroutineScope
) {
    val items = run {
        // We get the current arrangement once and do not observe on purpose.
        val isHeaderEnabled = loadIsHeaderEnabledFromSettings(context)
        val actionArrangement = loadLongPressActionArrangementFromSettings(context)
        return@run buildItemsInList(isHeaderEnabled, actionArrangement).toMutableStateList()
    }

    // variables for handling drag, DPAD focus, and autoscrolling when finger is at top/bottom

    /** If not null, the [ItemInList] that the user picked up and is dragging around. */
    var activeDragItem by mutableStateOf<ItemInList?>(null)
        private set

    /** If [activeDragItem]`!=null`, contains the user's finger position. */
    var activeDragPosition by mutableStateOf(IntOffset.Zero)
        private set

    /** If [activeDragItem]`!=null`, the size it had in the list before being picked up. */
    var activeDragSize by mutableStateOf(IntSize.Zero)
        private set

    /** If `>=0`, the index of the list item currently focused via DPAD (e.g. on Android TVs). */
    var currentlyFocusedItem by mutableIntStateOf(-1)
        private set

    /**
     * It is `!=null` only when the user is dragging something via touch, and is used to scroll
     * up/down if the user's finger is close to the top/bottom of the list.
     */
    private var autoScrollJob by mutableStateOf<Job?>(null)

    /**
     * A value in range `[0, maxSpeed]`, computed with [autoScrollSpeedFromTouchPos], and used by
     * [autoScrollJob] to scroll faster or slower depending on how close the finger is to the
     * top/bottom of the list.
     */
    private var autoScrollSpeed by mutableFloatStateOf(0f)

    /**
     * Build the initial list of [ItemInList] given the [isHeaderEnabled] and [actionArrangement]
     * loaded from settings. A "hidden actions" caption will separate the enabled actions (at the
     * beginning of the list) from the disabled ones (at the end).
     *
     * @param isHeaderEnabled whether the header should come before or after the "hidden actions"
     * caption in the list
     * @param actionArrangement a list of **distinct** [LongPressAction.Type]s to show before the
     * "hidden actions"; items must be distinct because it wouldn't make sense to enable an action
     * twice, but also because the [LongPressAction.Type]`.ordinal`s are used as `LazyGrid` IDs in
     * the UI (see [ItemInList.stableUniqueKey]), which requires them to be unique, so any duplicate
     * items will be removed
     * @return a list with [ItemInList.Action]s of all [LongPressAction.Type]s, with a header, and
     * with two textual captions in between to distinguish between enabled and disabled items, for a
     * total of `#(`[LongPressAction.Type]`) + 3` items (`+ 1` if a [ItemInList.NoneMarker] is also
     * needed to indicate that no items are enabled or disabled)
     */
    private fun buildItemsInList(
        isHeaderEnabled: Boolean,
        actionArrangement: List<LongPressAction.Type>
    ): List<ItemInList> {
        return sequence {
            yield(ItemInList.EnabledCaption)
            if (isHeaderEnabled) {
                yield(ItemInList.HeaderBox)
            }
            yieldAll(
                actionArrangement
                    .distinct() // see in the javadoc why this is important
                    .map { ItemInList.Action(it) }
                    .ifEmpty { if (isHeaderEnabled) listOf() else listOf(ItemInList.NoneMarker) }
            )
            yield(ItemInList.HiddenCaption)
            if (!isHeaderEnabled) {
                yield(ItemInList.HeaderBox)
            }
            yieldAll(
                // these are trivially all distinct, so no need for distinct() here
                LongPressAction.Type.entries
                    .filter { !actionArrangement.contains(it) }
                    .map { ItemInList.Action(it) }
                    .ifEmpty { if (isHeaderEnabled) listOf(ItemInList.NoneMarker) else listOf() }
            )
        }.toList()
    }

    /**
     * Rebuilds the list state given the default action arrangement and header enabled status. Note
     * that this does not save anything to settings, but only changes the list shown in the UI, as
     * per the class javadoc.
     */
    fun resetToDefaults(context: Context) {
        items.clear()
        items.addAll(buildItemsInList(true, getDefaultLongPressActionArrangement(context)))
    }

    /**
     * @return the [ItemInList] at the position [offset] (relative to the start of the lazy grid),
     * or the closest item along the row of the grid intersecting with [offset], or `null` if no
     * such item exists
     */
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

    /**
     * @return a number between 0 and [maxSpeed] indicating how fast the view should auto-scroll
     * up/down while dragging an item, depending on how close the finger is to the top/bottom; uses
     * this piecewise linear function, where `x=`[touchPos]`.y/height`:
     * `f(x) = maxSpeed * max((x-1)/borderPercent + 1, min(x/borderPercent - 1, 0))`
     */
    private fun autoScrollSpeedFromTouchPos(
        touchPos: IntOffset,
        maxSpeed: Float = 20f,
        scrollIfCloseToBorderPercent: Float = 0.2f
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
     * Prepares the list state because user wants to pick up an item, by putting the selected item
     * in [activeDragItem] and replacing it in the view with a [ItemInList.DragMarker]. Called not
     * just for drag gestures initiated by moving the finger, but also with DPAD's Enter.
     * @param pos the touch position (for touch dragging), or the focus position (for DPAD moving)
     * @param rawItem the `LazyGrid` item the user selected (it's a parameter because it's
     * determined differently for touch and for DPAD)
     * @return `true` if the dragging could be initiated correctly, `false` otherwise (e.g. if the
     * item is not supposed to be draggable)
     */
    private fun beginDrag(pos: IntOffset, rawItem: LazyGridItemInfo): Boolean {
        if (activeDragItem != null) return false
        val item = items.getOrNull(rawItem.index) ?: return false
        if (!item.isDraggable) return false

        items[rawItem.index] = ItemInList.DragMarker(item.columnSpan)
        activeDragItem = item
        activeDragPosition = pos
        activeDragSize = rawItem.size
        return true
    }

    /**
     * Finds the item under the user's touch, and then just delegates to [beginDrag], and if that's
     * successful starts [autoScrollJob]. Only called on touch input, and not on DPAD input. Will
     * not do anything if [wasLongPressed] is `false`, because only long-press-then-move should be
     * used for moving items, note that however the touch events will still be forwarded to
     * [handleDragChangeTouch] to handle scrolling.
     */
    fun beginDragTouch(pos: IntOffset, wasLongPressed: Boolean) {
        if (!wasLongPressed) {
            // items can be dragged around only if they are long-pressed;
            // use the drag as scroll otherwise
            return
        }
        val rawItem = findItemForOffsetOrClosestInRow(pos) ?: return
        if (beginDrag(pos, rawItem)) {
            // only start the job if `beginDragGesture` was successful
            autoScrollSpeed = 0f
            autoScrollJob?.cancel() // just in case
            autoScrollJob = coroutineScope.launch {
                while (isActive) {
                    if (autoScrollSpeed != 0f) {
                        gridState.scrollBy(autoScrollSpeed)
                    }
                    delay(16L) // roughly 60 FPS
                }
            }
        }
    }

    /**
     * Called when the user's finger, or the DPAD focus, moves over a new item while a drag is
     * active (i.e. [activeDragItem]`!=null`). Moves the [ItemInList.DragMarker] in the list to be
     * at the current position of [rawItem]/[dragItem], and adds/removes [ItemInList.NoneMarker] if
     * needed.
     * @param dragItem the same as [activeDragItem], but `!= null`
     * @param rawItem the raw `LazyGrid` state of the [ItemInList] that the user is currently
     * passing over with touch or focus
     */
    private fun handleDragChange(dragItem: ItemInList, rawItem: LazyGridItemInfo) {
        val prevDragMarkerIndex = items.indexOfFirst { it is ItemInList.DragMarker }
            .takeIf { it >= 0 }
        if (prevDragMarkerIndex == null) {
            Log.w(TAG, "DragMarker not being in the list should be impossible")
            return
        }

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
     * Handles touch gesture movements, and scrolls the `LazyGrid` if no item is being actively
     * dragged, or otherwise delegates to [handleDragChange]. Also updates [activeDragPosition] (so
     * the dragged item can be shown at that offset in the UI) and [autoScrollSpeed]. This is only
     * called on touch input, and not on DPAD input.
     */
    fun handleDragChangeTouch(pos: IntOffset, posChangeForScrolling: Offset) {
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
        handleDragChange(dragItem, rawItem)
    }

    /**
     * Concludes the touch/DPAD drag, stops the [autoScrollJob] if any, and most importantly
     * "releases" the [activeDragItem] by putting it back in the list, replacing the
     * [ItemInList.DragMarker]. This function is called in multiple places, e.g. when the finger
     * stops touching, or with DPAD events.
     */
    fun completeDragAndCleanUp() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        autoScrollSpeed = 0f

        // activeDragItem could be null if the user did not long-press any item but is just
        // scrolling the view, see `beginDragTouch()` and `handleDragChangeTouch()`
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
     * Handles DPAD events on Android TVs (right, left, up, down, center). Items can be focused by
     * navigating with arrows and can be selected (thus initiating a drag) with center. Once
     * selected, arrow button presses will move the item around in the list, and pressing center
     * will release the item at the new position. When focusing or moving an item outside of the
     * screen, the `LazyGrid` will scroll to it.
     *
     * @param event the event to process
     * @param columns the number of columns in the `LazyGrid`, needed to correctly go one line
     * up/down when receiving the up/down events
     * @return `true` if the event was handled, `false` if it wasn't (if this function returns
     * `false`, the event is supposed to be handled by the focus mechanism of some external view,
     * e.g. to give focus back to views other than the `LazyGrid`)
     */
    fun onKeyEvent(event: KeyEvent, columns: Int): Boolean {
        // generally we only care about [KeyEventType.KeyDown] events, as is common on Android TVs,
        // but in the special case where the user has an external view in focus (i.e. a button in
        // the toolbar) and then presses the down-arrow to enter the `LazyGrid`, we will only
        // receive [KeyEventType.KeyUp] here, and we need to handle it
        if (event.type != KeyEventType.KeyDown) { // KeyDown means that the button was pressed
            if (event.type == KeyEventType.KeyUp && // KeyDown means that the button was released
                event.key == Key.DirectionDown && // DirectionDown indicates the down-arrow button
                currentlyFocusedItem < 0
            ) {
                currentlyFocusedItem = 0
            }
            return false
        }

        var focusedItem = currentlyFocusedItem // do operations on a local variable
        when (event.key) {
            Key.DirectionUp -> {
                if (focusedItem < 0) {
                    return false // already at the beginning,
                } else if (items[focusedItem].columnSpan == null) {
                    focusedItem -= 1 // this item uses the whole line, just go to the previous item
                } else {
                    // go to the item in the same column on the previous line
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
                    return false // already at the end
                } else if (focusedItem < 0 || items[focusedItem].columnSpan == null) {
                    focusedItem += 1 // this item uses the whole line, just go to the next item
                } else {
                    // go to the item in the same column on the next line
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
                    return false // already at the beginning
                } else {
                    focusedItem -= 1
                }
            }

            Key.DirectionRight -> {
                if (focusedItem >= items.size - 1) {
                    return false // already at the end
                } else {
                    focusedItem += 1
                }
            }

            // when pressing enter/center, either start a drag or complete the current one
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> if (activeDragItem == null) {
                val rawItem = gridState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == focusedItem }
                    ?: return false
                beginDrag(rawItem.center(), rawItem)
                return true
            } else {
                completeDragAndCleanUp()
                return true
            }

            else -> return false // we don't need this event
        }

        currentlyFocusedItem = focusedItem
        if (focusedItem < 0) {
            // there is no `if (focusedItem >= items.size)` because it's impossible for it
            // to reach that value, and that's because we assume that there is nothing
            // else focusable *after* this view. This way we don't need to cleanup the
            // drag gestures when the user reaches the end, which would be confusing as
            // then there would be no indication of the current cursor position at all.
            completeDragAndCleanUp()
            return false
        } else if (focusedItem >= items.size) {
            Log.w(TAG, "Invalid focusedItem $focusedItem: >= items size ${items.size}")
        }

        // find the item with the closest index to handle `focusedItem < 0` or `>= items.size` cases
        val rawItem = gridState.layoutInfo.visibleItemsInfo
            .minByOrNull { abs(it.index - focusedItem) }
            ?: return false // no item is visible at all, impossible case

        // If the item we are going to focus is not visible or is close to the boundary,
        // scroll to it. Note that this will cause the "drag item" to appear misplaced,
        // since the drag item's position is set to the position of the focused item
        // before scrolling. However, it's not worth overcomplicating the logic just for
        // correcting the UI position of a drag hint on Android TVs.
        val h = rawItem.size.height
        if (rawItem.index != focusedItem ||
            rawItem.offset.y <= gridState.layoutInfo.viewportStartOffset + 0.8 * h ||
            rawItem.offset.y + 1.8 * h >= gridState.layoutInfo.viewportEndOffset
        ) {
            coroutineScope.launch {
                gridState.scrollToItem(focusedItem, -(0.8 * h).toInt())
            }
        }

        activeDragItem?.let { dragItem ->
            // This will mostly bring the drag item to the right position, but will
            // misplace it if the view just scrolled (see above), or if the DragMarker's
            // position is moved past HiddenCaption by handleDragGestureChange() below.
            // However, it's not worth overcomplicating the logic just for correcting
            // the UI position of a drag hint on Android TVs.
            activeDragPosition = rawItem.center()
            handleDragChange(dragItem, rawItem)
        }
        return true
    }

    /**
     * Stops any currently active drag, and saves to settings the action arrangement and whether the
     * header is enabled.
     */
    fun onDispose(context: Context) {
        completeDragAndCleanUp()

        var isHeaderEnabled = false
        val actionArrangement = ArrayList<LongPressAction.Type>()
        // All of the items before the HiddenCaption are enabled.
        for (item in items) {
            when (item) {
                is ItemInList.Action -> actionArrangement.add(item.type)
                ItemInList.HeaderBox -> isHeaderEnabled = true
                ItemInList.HiddenCaption -> break
                else -> {}
            }
        }

        storeIsHeaderEnabledToSettings(context, isHeaderEnabled)
        storeLongPressActionArrangementToSettings(context, actionArrangement)
    }
}

sealed class ItemInList(
    val isDraggable: Boolean = false,
    val isCaption: Boolean = false,
    // if null, then the item will occupy all of the line
    open val columnSpan: Int? = 1
) {
    // decoration items (i.e. text subheaders)
    object EnabledCaption : ItemInList(isCaption = true, columnSpan = null) // i.e. span all line
    object HiddenCaption : ItemInList(isCaption = true, columnSpan = null) // i.e. span all line

    // actual draggable actions (+ a header)
    object HeaderBox : ItemInList(isDraggable = true, columnSpan = 2)
    data class Action(val type: LongPressAction.Type) : ItemInList(isDraggable = true)

    // markers
    object NoneMarker : ItemInList()
    data class DragMarker(override val columnSpan: Int?) : ItemInList()

    /**
     * @return a unique key for each [ItemInList], which can be used as a key for `Lazy` containers
     */
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

fun LazyGridItemInfo.center(): IntOffset {
    return offset + IntOffset(size.width / 2, size.height / 2)
}
