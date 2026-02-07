package org.schabi.newpipe

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider

val ctx: Context
    get() = ApplicationProvider.getApplicationContext<Context>()

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
