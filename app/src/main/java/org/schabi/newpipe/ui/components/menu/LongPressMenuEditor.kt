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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.menu.LongPressAction.Type.Companion.DefaultEnabledActions
import org.schabi.newpipe.ui.detectDragGestures
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.text.FixedHeightCenteredText

private const val ItemNotFound = -1

// TODO implement accessibility for this, to allow using this with a DPAD (e.g. Android TV)
@Composable
fun LongPressMenuEditor() {
    // We get the current arrangement once and do not observe on purpose
    // TODO load from settings
    var headerEnabled by remember { mutableStateOf(true) }
    val actionArrangement = remember { DefaultEnabledActions }
    val enabledActions = remember(actionArrangement) {
        actionArrangement
            .map { ActionOrMarker.Action(it) }
            .ifEmpty { listOf(ActionOrMarker.NoneMarker) }
            .toMutableStateList()
    }
    val hiddenActions = remember(actionArrangement) {
        LongPressAction.Type.entries
            .filter { !actionArrangement.contains(it) }
            .map { ActionOrMarker.Action(it) }
            .ifEmpty { listOf(ActionOrMarker.NoneMarker) }
            .toMutableStateList()
    }

    val gridState = rememberLazyGridState()
    var activeDragAction by remember { mutableStateOf<ActionOrMarker?>(null) }
    var activeDragPosition by remember { mutableStateOf(IntOffset.Zero) }
    var activeDragSize by remember { mutableStateOf(IntSize.Zero) }

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

    fun isIndexOfHeader(i: Int): Boolean {
        return if (headerEnabled) i == 1 else i == enabledActions.size + 2
    }

    fun indexOfEnabledAction(i: Int): Int {
        val base = if (headerEnabled) 2 else 1
        return if (i >= base && i < (enabledActions.size + base)) i - base else ItemNotFound
    }

    fun indexOfHiddenAction(i: Int): Int {
        val base = enabledActions.size + 3
        return if (i >= base && i < (hiddenActions.size + base)) i - base else ItemNotFound
    }

    fun removeAllMarkers() {
        enabledActions.remove(ActionOrMarker.DragMarker)
        if (enabledActions.isEmpty()) {
            enabledActions.add(ActionOrMarker.NoneMarker)
        }
        hiddenActions.remove(ActionOrMarker.DragMarker)
        if (hiddenActions.isEmpty()) {
            hiddenActions.add(ActionOrMarker.NoneMarker)
        }
    }

    fun beginDragGesture(pos: IntOffset) {
        val item = findItemForOffsetOrClosestInRow(pos) ?: return
        val i = item.index
        val enabledActionIndex = indexOfEnabledAction(i)
        val hiddenActionIndex = indexOfHiddenAction(i)
        if (isIndexOfHeader(i)) {
            activeDragAction = ActionOrMarker.Header
        } else if (enabledActionIndex != ItemNotFound && enabledActions[enabledActionIndex] != ActionOrMarker.NoneMarker) {
            activeDragAction = enabledActions[enabledActionIndex]
            enabledActions[enabledActionIndex] = ActionOrMarker.DragMarker
        } else if (hiddenActionIndex != ItemNotFound && hiddenActions[hiddenActionIndex] != ActionOrMarker.NoneMarker) {
            activeDragAction = hiddenActions[hiddenActionIndex]
            hiddenActions[hiddenActionIndex] = ActionOrMarker.DragMarker
        } else {
            return
        }
        activeDragPosition = pos
        activeDragSize = item.size
    }

    fun handleDragGestureChange(pos: IntOffset, posChange: Offset) {
        if (activeDragAction == null) {
            // when the user clicks outside of any draggable item, let the list be scrolled
            gridState.dispatchRawDelta(-posChange.y)
            return
        }
        activeDragPosition = pos
        val item = findItemForOffsetOrClosestInRow(pos) ?: return
        val i = item.index

        if (activeDragAction == ActionOrMarker.Header) {
            headerEnabled = i < enabledActions.size + 2
            return
        }

        val enabledActionIndex = indexOfEnabledAction(i)
        val hiddenActionIndex = indexOfHiddenAction(i)
        if (enabledActionIndex != ItemNotFound && enabledActions[enabledActionIndex] != ActionOrMarker.DragMarker) {
            if (enabledActions[enabledActionIndex] == ActionOrMarker.NoneMarker) {
                removeAllMarkers()
                enabledActions[enabledActionIndex] = ActionOrMarker.DragMarker
            } else {
                removeAllMarkers()
                enabledActions.add(enabledActionIndex, ActionOrMarker.DragMarker)
            }
        } else if (hiddenActionIndex != ItemNotFound && hiddenActions[hiddenActionIndex] != ActionOrMarker.DragMarker) {
            if (hiddenActions[hiddenActionIndex] == ActionOrMarker.NoneMarker) {
                removeAllMarkers()
                hiddenActions[hiddenActionIndex] = ActionOrMarker.DragMarker
            } else {
                removeAllMarkers()
                hiddenActions.add(hiddenActionIndex, ActionOrMarker.DragMarker)
            }
        }
    }

    fun completeDragGestureAndCleanUp() {
        val action = activeDragAction
        if (action != null && action != ActionOrMarker.Header) {
            val i = enabledActions.indexOf(ActionOrMarker.DragMarker)
            if (i >= 0) {
                enabledActions[i] = action
            } else {
                val j = hiddenActions.indexOf(ActionOrMarker.DragMarker)
                if (j >= 0) {
                    hiddenActions[j] = action
                }
            }
        }
        activeDragAction = null
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
        item(span = { GridItemSpan(maxLineSpan) }) {
            Subheader(
                title = R.string.long_press_menu_enabled_actions,
                description = R.string.long_press_menu_enabled_actions_description,
            )
        }
        if (headerEnabled) {
            item(span = { GridItemSpan(2) }) {
                ActionOrMarkerUi(
                    modifier = Modifier.animateItem(),
                    // if the header is being dragged, show a DragMarker in its place
                    action = if (activeDragAction == ActionOrMarker.Header)
                        ActionOrMarker.DragMarker
                    else
                        ActionOrMarker.Header,
                )
            }
        }
        itemsIndexed(enabledActions, key = { _, action -> action.stableUniqueKey() }) { _, action ->
            ActionOrMarkerUi(modifier = Modifier.animateItem(), action = action)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Subheader(
                title = R.string.long_press_menu_hidden_actions,
                description = R.string.long_press_menu_hidden_actions_description,
            )
        }
        if (!headerEnabled) {
            item(span = { GridItemSpan(2) }) {
                ActionOrMarkerUi(
                    modifier = Modifier.animateItem(),
                    // if the header is being dragged, show a DragMarker in its place
                    action = if (activeDragAction == ActionOrMarker.Header)
                        ActionOrMarker.DragMarker
                    else
                        ActionOrMarker.Header,
                )
            }
        }
        itemsIndexed(hiddenActions, key = { _, action -> action.stableUniqueKey() }) { _, action ->
            ActionOrMarkerUi(modifier = Modifier.animateItem(), action = action)
        }
        item {
            // make the grid size a bit bigger to let items be dragged at the bottom and to give
            // the view some space to resizing without jumping up and down
            Spacer(modifier = Modifier.height(MinButtonWidth))
        }
    }
    if (activeDragAction != null) {
        val size = with(LocalDensity.current) {
            remember(activeDragSize) { activeDragSize.toSize().toDpSize() }
        }
        ActionOrMarkerUi(
            modifier = Modifier
                .size(size)
                .offset { activeDragPosition }
                .offset(-size.width / 2, -size.height / 2),
            action = activeDragAction!!,
        )
    }
}

sealed interface ActionOrMarker {
    object NoneMarker : ActionOrMarker
    object DragMarker : ActionOrMarker
    object Header : ActionOrMarker
    data class Action(val type: LongPressAction.Type) : ActionOrMarker

    fun stableUniqueKey(): Any {
        return when (this) {
            is Action -> this.type.ordinal
            DragMarker -> LongPressAction.Type.entries.size
            NoneMarker -> LongPressAction.Type.entries.size + 1
            Header -> LongPressAction.Type.entries.size + 2
        }
    }
}

@Composable
private fun Subheader(@StringRes title: Int, @StringRes description: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
private fun ActionOrMarkerUi(action: ActionOrMarker, modifier: Modifier = Modifier) {
    Surface(
        color = when (action) {
            ActionOrMarker.Header -> MaterialTheme.colorScheme.surfaceVariant
            else -> Color.Transparent
        },
        contentColor = when (action) {
            is ActionOrMarker.Action -> MaterialTheme.colorScheme.primary
            ActionOrMarker.DragMarker -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            // 0.38f is the same alpha that the Material3 library applies for disabled buttons
            ActionOrMarker.NoneMarker -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ActionOrMarker.Header -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = MaterialTheme.shapes.large,
        modifier = modifier.padding(
            horizontal = if (action == ActionOrMarker.Header) 12.dp else 3.dp,
            vertical = 5.dp,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier,
        ) {
            Icon(
                imageVector = when (action) {
                    is ActionOrMarker.Action -> action.type.icon
                    ActionOrMarker.DragMarker -> Icons.Default.DragHandle
                    ActionOrMarker.NoneMarker -> Icons.Default.Close
                    ActionOrMarker.Header -> Icons.Default.ArtTrack
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            FixedHeightCenteredText(
                text = stringResource(
                    when (action) {
                        is ActionOrMarker.Action -> action.type.label
                        ActionOrMarker.DragMarker -> R.string.detail_drag_description
                        ActionOrMarker.NoneMarker -> R.string.none
                        ActionOrMarker.Header -> R.string.header
                    }
                ),
                lines = 2,
                style = MaterialTheme.typography.bodySmall,
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

private class ActionOrMarkerPreviewProvider : CollectionPreviewParameterProvider<ActionOrMarker>(
    listOf(ActionOrMarker.Header, ActionOrMarker.DragMarker, ActionOrMarker.NoneMarker) +
        LongPressAction.Type.entries.take(3).map { ActionOrMarker.Action(it) }
)

@Preview
@Composable
private fun QuickActionButtonPreview(
    @PreviewParameter(ActionOrMarkerPreviewProvider::class) actionOrMarker: ActionOrMarker
) {
    AppTheme {
        Surface {
            ActionOrMarkerUi(actionOrMarker, Modifier.width(MinButtonWidth))
        }
    }
}
