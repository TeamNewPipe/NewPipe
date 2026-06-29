/*
   * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
   * SPDX-License-Identifier: GPL-3.0-or-later
   */

package net.newpipe.app.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import newpipe.shared.generated.resources.ok
import org.jetbrains.compose.resources.stringResource

/**
 * One option in a [MultiSelectListPreference].
 **/
data class MultiSelectListPreferenceOption(
    val value: String,
    val label: String
)

/**
 * Row that opens a checkbox dialog letting the user pick zero or more values
 * from [options]. Pending selection is held locally so Cancel reverts.
 */
@Composable
fun MultiSelectListPreference(
    title: String,
    options: List<MultiSelectListPreferenceOption>,
    selectedValues: Set<String>,
    onValuesChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    val effectiveSummary = summary ?: options
        .filter { it.value in selectedValues }
        .joinToString(", ") { it.label }
        .ifEmpty { null }

    TextPreference(
        title = title,
        summary = effectiveSummary,
        onClick = { dialogOpen = true },
        modifier = modifier,
        enabled = enabled
    )

    if (dialogOpen) {
        var pending by remember { mutableStateOf(selectedValues) }
        LaunchedEffect(selectedValues) { pending = selectedValues }

        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(text = title) },
            text = {
                Column(modifier =
                    Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        val checked = option.value in pending
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    onValueChange = { isChecked ->
                                        pending = if (isChecked) pending +
                                                option.value
                                        else pending - option.value
                                    }
                                )
                                .padding(vertical = spaceSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                text = option.label,
                                style =
                                    MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start =
                                    spaceSmall)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onValuesChange(pending)
                    dialogOpen = false
                }) {
                    Text(text = stringResource(Res.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun MultiSelectListPreferencePreview() {
    val options = listOf(
        MultiSelectListPreferenceOption("a", "Apple"),
        MultiSelectListPreferenceOption("b", "Banana"),
        MultiSelectListPreferenceOption("c", "Cherry")
    )
    MultiSelectListPreference(
        title = "Pick fruits",
        options = options,
        selectedValues = setOf("a", "c"),
        onValuesChange = {}
    )
}