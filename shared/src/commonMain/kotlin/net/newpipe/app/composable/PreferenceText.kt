/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.preview.ThemePreviewProvider

private const val DISABLED_ALPHA = 0.38f

@Composable
internal fun PreferenceText(
    title: String,
    summary: String?,
    enabled: Boolean = true
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Start,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
        )
        summary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PreferenceTextEnabledPreview() {
    PreferenceText(
        title = "Default resolution",
        summary = "Best resolution",
        enabled = true
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PreferenceTextDisabledPreview() {
    PreferenceText(
        title = "Resume playback",
        summary = "Restore last playback position",
        enabled = false
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PreferenceTextNoSummaryPreview() {
    PreferenceText(
        title = "Crash the app",
        summary = null,
        enabled = true
    )
}