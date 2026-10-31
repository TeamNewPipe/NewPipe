/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.newpipe.app.preview.ThemePreviewProvider
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.cancel
import newpipe.shared.generated.resources.ok
import org.jetbrains.compose.resources.stringResource

/**
 * A preference row that opens a multiple-choice dialog, mirroring AndroidX MultiSelectListPreference
 *
 * @param title Title text of the preference and of the dialog
 * @param entries Selectable options
 * @param selectedValues Currently stored values
 * @param onValuesSelected Called with the chosen values once the dialog is confirmed
 * @param modifier Modifier applied to the row
 * @param summary Optional secondary line under the title
 * @param enabled Whether the row can be opened
 */
@Composable
fun MultiSelectListPreference(
    title: String,
    entries: List<ListPreferenceEntry>,
    selectedValues: Set<String>,
    onValuesSelected: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }

    PreferenceRow(
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        var pendingValues by remember { mutableStateOf(selectedValues) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title) },
            text = {
                LazyColumn {
                    items(items = entries, key = { entry -> entry.value }) { entry ->
                        val checked = entry.value in pendingValues
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    role = Role.Checkbox,
                                    onValueChange = { isChecked ->
                                        pendingValues = when {
                                            isChecked -> pendingValues + entry.value
                                            else -> pendingValues - entry.value
                                        }
                                    }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(text = entry.title, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValuesSelected(pendingValues)
                        showDialog = false
                    }
                ) {
                    Text(text = stringResource(Res.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun MultiSelectListPreferencePreview() {
    MultiSelectListPreference(
        title = "Search suggestions",
        summary = "Choose the suggestions to show when searching",
        entries = listOf(
            ListPreferenceEntry("local", "Local search suggestions"),
            ListPreferenceEntry("remote", "Remote search suggestions")
        ),
        selectedValues = setOf("local"),
        onValuesSelected = {}
    )
}
