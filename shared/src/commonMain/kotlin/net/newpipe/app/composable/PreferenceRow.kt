/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.composable

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.preview.ThemePreviewProvider
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_headset
import newpipe.shared.generated.resources.settings_category_video_audio_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A row that mirrors the standard AndroidX preference item
 *
 * @param title Title text of the preference
 * @param modifier Modifier applied to the row
 * @param icon Leading icon painter; null hides the leading slot
 * @param summary Optional secondary line under the title
 * @param onClick Action executed when the row is tapped
 */
@Composable
fun PreferenceRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    summary: String? = null,
    onClick: () -> Unit = {}
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(text = title) },
        supportingContent = summary?.let { text -> { Text(text = text) } },
        leadingContent = icon?.let { painter ->
            { Icon(painter = painter, contentDescription = null) }
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PreferenceRowPreview() {
    PreferenceRow(
        title = stringResource(Res.string.settings_category_video_audio_title),
        icon = painterResource(Res.drawable.ic_headset)
    )
}
