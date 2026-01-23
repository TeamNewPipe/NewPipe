/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.R

/**
 * A base composable that displays a title and an optional summary text. Used in settings preference
 * items such as TextPreference and SwitchPreference
 *
 * @param title the resource ID of the string to be used as the title
 * @param summary the optional resource ID of the string to be used as the summary
 * @param enabled whether the text should be displayed in an enabled or disabled state
 */
@Composable
internal fun TextBase(
    @StringRes title: Int,
    @StringRes summary: Int?,
    enabled: Boolean = true
) {
    Column {
        Text(
            text = stringResource(id = title),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Start,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        summary?.let {
            Text(
                text = stringResource(id = summary),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun TextBasePreview() {
    TextBase(R.string.settings_category_debug_title, R.string.settings_category_debug_title)
}
