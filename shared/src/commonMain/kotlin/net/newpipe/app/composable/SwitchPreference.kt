/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.composable

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.preview.ThemePreviewProvider

private const val DISABLED_ALPHA = 0.38f

/**
 * A preference row with a trailing switch, mirroring AndroidX SwitchPreferenceCompat.
 *
 * @param title Title text of the preference
 * @param checked Current checked state
 * @param onCheckedChange Called with the new state when toggled
 * @param modifier Modifier applied to the row
 * @param summary Optional secondary line under the title
 * @param enabled Whether the row can be toggled
 */
@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    ListItem(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        headlineContent = { Text(text = title) },
        supportingContent = summary?.let { text -> { Text(text = text) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun SwitchPreferencePreview() {
    SwitchPreference(
        title = "Show \"Hold to enqueue\" tip",
        summary = "Show tip when pressing the background button",
        checked = true,
        onCheckedChange = {}
    )
}
