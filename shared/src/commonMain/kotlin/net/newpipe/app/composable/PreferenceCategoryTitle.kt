/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.newpipe.app.preview.ThemePreviewProvider
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.panic_destructive_actions_summary
import newpipe.shared.generated.resources.panic_destructive_actions_title
import newpipe.shared.generated.resources.settings_category_player_title
import org.jetbrains.compose.resources.stringResource

/**
 * Section header inside a settings screen
 */
@Composable
fun PreferenceCategoryTitle(title: String, modifier: Modifier = Modifier, summary: String? = null) {
    Column {
        Text(
            text = title,
            modifier = modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall
        )
        if (summary != null) {
            Text(
                text = summary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PreferenceCategoryTitlePreview() {
    PreferenceCategoryTitle(title = stringResource(Res.string.settings_category_player_title))
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PreferenceCategoryTitleWithSummaryPreview() {
    PreferenceCategoryTitle(
        title = stringResource(Res.string.panic_destructive_actions_title),
        summary = stringResource(Res.string.panic_destructive_actions_summary)
    )
}
