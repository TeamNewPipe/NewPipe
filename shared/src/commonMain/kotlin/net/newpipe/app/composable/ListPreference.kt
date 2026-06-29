/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.spaceSmall
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.cancel
import org.jetbrains.compose.resources.stringResource

/**
 * One entry in a [ListPreference].
 * @param value Persisted to [com.russhwolf.settings.ObservableSettings].
 * @param label Display label shown in the dialog and as the row summary.
 */
data class ListPreferenceOption(
    val value: String,
    val label: String
)

/**
 * Row that opens a radio-button dialog for picking a single value from
 * [options].
 *
 * @param summary Optional static summary. When `null`, the row's summary
 *   shows the label of the currently selected option (legacy parity with
 *   `useSimpleSummaryProvider="true"`). Pass a non-null string to mirror the
 *   legacy `android:summary="…"` static-description behavior.
 */
@Composable
fun ListPreference(
    title: String,
    options: List<ListPreferenceOption>,
    selectedValue: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    val effectiveSummary = summary ?: options.firstOrNull { it.value == selectedValue }?.label

    TextPreference(
        title = title,
        summary = effectiveSummary,
        onClick = { dialogOpen = true },
        modifier = modifier,
        enabled = enabled
    )

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(text = title) },
            text = {
                Column {
                    options.forEach { option ->
                        val isSelected = option.value == selectedValue
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        onValueChange(option.value)
                                        dialogOpen = false
                                    }
                                )
                                .padding(vertical = spaceSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = spaceSmall)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            }
        )
    }
}

private val previewOptions = listOf(
    ListPreferenceOption("best_resolution", "Best resolution"),
    ListPreferenceOption("1080p", "1080p"),
    ListPreferenceOption("720p", "720p"),
    ListPreferenceOption("480p", "480p")
)

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ListPreferenceSelectedLabelSummaryPreview() {
    ListPreference(
        title = "Default resolution",
        options = previewOptions,
        selectedValue = "720p",
        onValueChange = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ListPreferenceStaticSummaryPreview() {
    ListPreference(
        title = "Image quality",
        summary = "Choose the quality of images and whether to load images at all", options = previewOptions,
        selectedValue = "best_resolution",
        onValueChange = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ListPreferenceDisabledPreview() {
    ListPreference(
        title = "Night theme",
        options = previewOptions,
        selectedValue = "1080p",
        onValueChange = {},
        enabled = false
    )
}