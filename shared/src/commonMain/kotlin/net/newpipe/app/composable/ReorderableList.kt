/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

private const val DRAGGED_ITEM_Z_INDEX = 1f

@Stable
class ReorderableListState internal constructor(
    val lazyListState: LazyListState,
    private val onMove: (fromKey: Any, toKey: Any) -> Unit
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    var dragOffset by mutableFloatStateOf(0f)
        private set

    internal fun onDragStart(key: Any) {
        draggingKey = key
        dragOffset = 0f
    }

    internal fun onDrag(delta: Float) {
        val key = draggingKey ?: return
        dragOffset += delta

        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val dragged = visibleItems.firstOrNull { item -> item.key == key } ?: return
        val middle = dragged.offset + dragOffset + dragged.size / 2f
        val target = visibleItems.firstOrNull { item ->
            item.key != key &&
                item.contentType == dragged.contentType &&
                middle >= item.offset &&
                middle <= item.offset + item.size
        } ?: return

        // The dragged item is laid out at the target's place next, so keep it under the finger
        dragOffset += dragged.offset - target.offset
        onMove(key, target.key)
    }

    internal fun onDragEnd() {
        draggingKey = null
        dragOffset = 0f
    }
}

@Composable
fun rememberReorderableListState(
    onMove: (fromKey: Any, toKey: Any) -> Unit,
    lazyListState: LazyListState = rememberLazyListState()
): ReorderableListState {
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(lazyListState) {
        ReorderableListState(lazyListState) { fromKey, toKey -> currentOnMove(fromKey, toKey) }
    }
}

/**
 * Wraps one item of a reorderable list. [content] gets the modifier to put on the drag handle.
 */
@Composable
fun LazyItemScope.ReorderableItem(
    state: ReorderableListState,
    key: Any,
    modifier: Modifier = Modifier,
    content: @Composable (handleModifier: Modifier) -> Unit
) {
    val itemModifier = if (state.draggingKey == key) {
        Modifier
            .zIndex(DRAGGED_ITEM_Z_INDEX)
            .graphicsLayer { translationY = state.dragOffset }
    } else {
        Modifier.animateItem()
    }

    Box(modifier = modifier.then(itemModifier)) {
        content(
            Modifier.pointerInput(state, key) {
                detectVerticalDragGestures(
                    onDragStart = { state.onDragStart(key) },
                    onDragEnd = state::onDragEnd,
                    onDragCancel = state::onDragEnd,
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        state.onDrag(dragAmount)
                    }
                )
            }
        )
    }
}
