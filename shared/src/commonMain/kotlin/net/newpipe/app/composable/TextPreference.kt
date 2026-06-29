/*
* SPDX-FileCopyrightText: 2017-2025 NewPipe contributors <https://newpipe.net>
* SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.preferenceMinHeight
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.theme.spaceXSmall
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_settings
import org.jetbrains.compose.resources.painterResource

private const val DISABLED_ALPHA = 0.38f

@Composable
fun TextPreference(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    summary: String? = null,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .fillMaxWidth()
            .padding(spaceSmall)
            .defaultMinSize(minHeight = preferenceMinHeight)
            .clickable(enabled = enabled) { onClick() }
    ) {
        icon?.let {
            Icon(
                painter = it,
                contentDescription = null,
                tint = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
            )
            Spacer(modifier = Modifier.width(spaceXSmall))
        }
        Column {
            PreferenceText(title = title, summary = summary, enabled = enabled)
        }
    }
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun TextPreferenceWithIconPreview() {
    TextPreference(
        title = "Player",
        summary = "Resolution, format, external player…",
        icon = painterResource(Res.drawable.ic_settings),
        onClick = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun TextPreferenceNoIconPreview() {
    TextPreference(
        title = "Reset settings",
        summary = "Reset all settings to their default values",
        onClick = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun TextPreferenceTitleOnlyPreview() {
    TextPreference(
        title = "Crash the app",
        onClick = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun TextPreferenceDisabledPreview() {
    TextPreference(
        title = "Show memory leaks",
        summary = "LeakCanary is not available in this build",
        onClick = {},
        enabled = false
    )
}

