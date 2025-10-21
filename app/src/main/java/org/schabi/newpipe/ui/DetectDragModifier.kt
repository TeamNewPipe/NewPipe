package org.schabi.newpipe.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset

/**
 * Detects a drag gesture **without** trying to filter out any misclicks. This is useful in menus
 * where items are dragged around, where the usual misclick guardrails would cause unexpected lags
 * or strange behaviors when dragging stuff around quickly. For other use cases, use
 * [androidx.compose.foundation.gestures.detectDragGestures] or
 * [androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress].
 *
 * @param beginDragGesture called when the user first touches the screen (down event) with the
 * pointer position, should return `true` if the receiver wants to handle this gesture, `false`
 * otherwise.
 * @param handleDragGestureChange called with the current pointer position, every time the user
 * moves the finger after [beginDragGesture] has returned `true`.
 * @param endDragGesture called when the drag gesture finishes after [beginDragGesture] has returned
 * `true`.
 */
fun Modifier.detectDragGestures(
    beginDragGesture: (IntOffset) -> Boolean,
    handleDragGestureChange: (IntOffset) -> Unit,
    endDragGesture: () -> Unit
): Modifier {
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val pointerId = down.id
            if (!beginDragGesture(down.position.toIntOffset())) {
                return@awaitEachGesture
            }
            while (true) {
                val change = awaitPointerEvent().changes.find { it.id == pointerId }
                if (change == null || !change.pressed) {
                    break
                }
                handleDragGestureChange(change.position.toIntOffset())
                change.consume()
            }
            endDragGesture()
        }
    }
}

private fun Offset.toIntOffset() = IntOffset(this.x.toInt(), this.y.toInt())
