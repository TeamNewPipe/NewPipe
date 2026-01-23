/*
 * SPDX-FileCopyrightText: 2017-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.schabi.newpipe.ui.theme.SizeTokens

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    @StringRes title: Int,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @StringRes summary: Int? = null,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(SizeTokens.SpacingSmall)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            TextBase(title = title, summary = summary, enabled = enabled)
        }
        Spacer(modifier = Modifier.width(SizeTokens.SpacingExtraSmall))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
