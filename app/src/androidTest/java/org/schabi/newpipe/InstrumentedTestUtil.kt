package org.schabi.newpipe

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail

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
