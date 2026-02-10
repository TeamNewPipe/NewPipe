package org.schabi.newpipe

import android.app.Instrumentation
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail

/**
 * Use this instead of calling `InstrumentationRegistry.getInstrumentation()` every time.
 */
val inst: Instrumentation
    get() = InstrumentationRegistry.getInstrumentation()

/**
 * Use this instead of passing contexts around in instrumented tests.
 */
val ctx: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

fun putBooleanInPrefs(@StringRes key: Int, value: Boolean) {
    PreferenceManager.getDefaultSharedPreferences(ctx)
        .edit().putBoolean(ctx.getString(key), value).apply()
}

fun putStringInPrefs(@StringRes key: Int, value: String) {
    PreferenceManager.getDefaultSharedPreferences(ctx)
        .edit().putString(ctx.getString(key), value).apply()
}

fun clearPrefs() {
    PreferenceManager.getDefaultSharedPreferences(ctx)
        .edit().clear().apply()
}

/**
 * E.g. useful to tap outside dialogs to see whether they close.
 */
fun tapAtAbsoluteXY(x: Float, y: Float) {
    val t = SystemClock.uptimeMillis()
    inst.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
    inst.sendPointerSync(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0))
}

/**
 * Same as the original `onNodeWithText` except that this takes a [StringRes] instead of a [String].
 */
fun SemanticsNodeInteractionsProvider.onNodeWithText(
    @StringRes text: Int,
    substring: Boolean = false,
    ignoreCase: Boolean = false,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction {
    return this.onNodeWithText(ctx.getString(text), substring, ignoreCase, useUnmergedTree)
}

/**
 * Same as the original `onNodeWithContentDescription` except that this takes a [StringRes] instead of a [String].
 */
fun SemanticsNodeInteractionsProvider.onNodeWithContentDescription(
    @StringRes text: Int,
    substring: Boolean = false,
    ignoreCase: Boolean = false,
    useUnmergedTree: Boolean = false
): SemanticsNodeInteraction {
    return this.onNodeWithContentDescription(ctx.getString(text), substring, ignoreCase, useUnmergedTree)
}

/**
 * Shorthand for `.fetchSemanticsNode().positionOnScreen`.
 */
fun SemanticsNodeInteraction.fetchPosOnScreen() = fetchSemanticsNode().positionOnScreen

/**
 * Asserts that [value] is in the range [[l], [r]] (both extremes included).
 */
fun <T : Comparable<T>> assertInRange(l: T, r: T, value: T) {
    if (l > r) {
        fail("Invalid range passed to `assertInRange`: [$l, $r]")
    }
    if (value !in l..r) {
        fail("Expected $value to be in range [$l, $r]")
    }
}

/**
 * Asserts that [value] is NOT in the range [[l], [r]] (both extremes included).
 */
fun <T : Comparable<T>> assertNotInRange(l: T, r: T, value: T) {
    if (l > r) {
        fail("Invalid range passed to `assertInRange`: [$l, $r]")
    }
    if (value in l..r) {
        fail("Expected $value to NOT be in range [$l, $r]")
    }
}

/**
 * Tries to scroll vertically in the container [this] and uses [itemInsideScrollingContainer] to
 * compute how much the container actually scrolled. Useful in tandem with [assertMoved] or
 * [assertDidNotMove].
 */
fun SemanticsNodeInteraction.scrollVerticallyAndGetOriginalAndFinalY(
    itemInsideScrollingContainer: SemanticsNodeInteraction,
    startY: TouchInjectionScope.() -> Float = { bottom },
    endY: TouchInjectionScope.() -> Float = { top }
): Pair<Float, Float> {
    val originalPosition = itemInsideScrollingContainer.fetchPosOnScreen()
    this.performTouchInput { swipeUp(startY = startY(), endY = endY()) }
    val finalPosition = itemInsideScrollingContainer.fetchPosOnScreen()
    assertEquals(originalPosition.x, finalPosition.x)
    return Pair(originalPosition.y, finalPosition.y)
}

/**
 * Simple assert on results from [scrollVerticallyAndGetOriginalAndFinalY].
 */
fun Pair<Float, Float>.assertMoved() {
    val (originalY, finalY) = this
    assertNotEquals(originalY, finalY)
}

/**
 * Simple assert on results from [scrollVerticallyAndGetOriginalAndFinalY].
 */
fun Pair<Float, Float>.assertDidNotMove() {
    val (originalY, finalY) = this
    assertEquals(originalY, finalY)
}
