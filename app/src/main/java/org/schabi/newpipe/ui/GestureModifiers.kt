package org.schabi.newpipe.ui

import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny

/**
 * Detects a drag gesture **without** trying to filter out any misclicks. This is useful in menus
 * where items are dragged around, where the usual misclick guardrails would cause unexpected lags
 * or strange behaviors when dragging stuff around quickly. Also detects whether a drag gesture
 * began with a long press or not, which can be useful to decide whether an item should be dragged
 * around (in case of long-press) or the view should be scrolled (otherwise). For other use cases,
 * use [androidx.compose.foundation.gestures.detectDragGestures] or
 * [androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress].
 *
 * @param beginDragGesture called when the user first touches the screen (down event) with the
 * pointer position and whether a long press was detected.
 * @param handleDragGestureChange called with the current pointer position and the difference from
 * the last position, every time the user moves the finger after [beginDragGesture] has been called.
 * @param endDragGesture called when the drag gesture finishes, after [beginDragGesture] has been
 * called.
 */
fun Modifier.detectDragGestures(
    beginDragGesture: (position: IntOffset, wasLongPressed: Boolean) -> Unit,
    handleDragGestureChange: (position: IntOffset, positionChange: Offset) -> Unit,
    endDragGesture: () -> Unit
): Modifier {
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val wasLongPressed = try {
                // code in this branch was taken from AwaitPointerEventScope.waitForLongPress(),
                // which unfortunately is private
                withTimeout(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.fastAll { it.changedToUp() }) {
                            // All pointers are up
                            break
                        }

                        if (event.classification == MotionEvent.CLASSIFICATION_DEEP_PRESS) {
                            return@withTimeout true
                        }

                        if (
                            event.changes.fastAny {
                                it.isConsumed || it.isOutOfBounds(IntSize(0, 0), extendedTouchPadding)
                            }
                        ) {
                            break
                        }
                    }
                    return@withTimeout false
                }
            } catch (_: PointerEventTimeoutCancellationException) {
                true
            }

            val pointerId = down.id
            beginDragGesture(down.position.toIntOffset(), wasLongPressed)
            while (true) {
                val change = awaitPointerEvent().changes.find { it.id == pointerId }
                if (change == null || !change.pressed) {
                    break
                }
                handleDragGestureChange(
                    change.position.toIntOffset(),
                    change.positionChange()
                )
                change.consume()
            }
            endDragGesture()
        }
    }
}

private fun Offset.toIntOffset() = IntOffset(this.x.toInt(), this.y.toInt())

/**
 * Discards all touches on child composables. See https://stackoverflow.com/a/69146178.
 * @param doDiscard whether this Modifier is active (touches discarded) or not (no effect).
 */
fun Modifier.discardAllTouchesIf(doDiscard: Boolean) = if (doDiscard) {
    pointerInput(Unit) {
        awaitPointerEventScope {
            // we should wait for all new pointer events
            while (true) {
                awaitPointerEvent(pass = PointerEventPass.Initial)
                    .changes
                    .forEach(PointerInputChange::consume)
            }
        }
    }
} else {
    this
}
