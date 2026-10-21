/*
* SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.preview.ThemePreviewProvider
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.cancel
import newpipe.shared.generated.resources.ok
import org.jetbrains.compose.resources.stringResource

private const val DISABLED_ALPHA = 0.38f

/**
 * A preference row that opens a single-line text dialog, mirroring AndroidX EditTextPreference.
 *
 * @param title Title text of the preference and of the dialog
 * @param value Currently stored text
 * @param onValueChange Called with the edited text once the dialog is confirmed
 * @param modifier Modifier applied to the row
 * @param summary Secondary line under the title
 * @param enabled Whether the row can be opened
 */
@Composable
fun EditTextPreference(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled) { showDialog = true },
        headlineContent = { Text(text = title) },
        supportingContent = summary?.let { text -> { Text(text = text) } }
    )

    if (showDialog) {
        // The state lives inside the branch, so it is recreated on every opening and an edit
        // that is cancelled is discarded, like the AndroidX dialog does.
        val textFieldState = rememberTextFieldState(value)
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title) },
            text = {
                TextField(
                    state = textFieldState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    lineLimits = TextFieldLineLimits.SingleLine
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(textFieldState.text.toString())
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
private fun EditTextPreferencePreview() {
    EditTextPreference(
        title = "Replacement character",
        summary = "Invalid characters are replaced with this value",
        value = "_",
        onValueChange = {}
    )
}
