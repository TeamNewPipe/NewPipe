/*
* SPDX-FileCopyrightText: 2017-2025 NewPipe contributors <https://newpipe.net>
* SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.composable

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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.theme.spaceXSmall

@Composable
fun SwitchPreference(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(spaceSmall)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PreferenceText(title = title, summary = summary, enabled = enabled)
        }
        Spacer(modifier = Modifier.width(spaceXSmall))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun SwitchPreferenceCheckedPreview() {
    SwitchPreference(
        title = "Watch history",
        summary = "Keep track of watched videos",
        isChecked = true,
        onCheckedChange = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun SwitchPreferenceUncheckedPreview() {
    SwitchPreference(
        title = "Use external video player",
        summary = "Some formats are unavailable when this is on",
        isChecked = false,
        onCheckedChange = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun SwitchPreferenceNoSummaryPreview() {
    SwitchPreference(
        title = "Use external audio player",
        isChecked = false,
        onCheckedChange = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun SwitchPreferenceDisabledPreview() {
    SwitchPreference(
        title = "Resume playback",
        summary = "Restore last playback position",
        isChecked = true,
        onCheckedChange = {},
        enabled = false
    )
}