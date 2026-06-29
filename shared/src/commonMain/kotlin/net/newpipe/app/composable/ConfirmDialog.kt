/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.preview.ThemePreviewProvider

/**
 * Generic yes/no confirmation dialog.
 *
 * @param title Dialog title.
 * @param message Body text shown above the buttons.
 * @param confirmLabel Label for the confirm button (defaults to "OK"-style).
 * @param dismissLabel Label for the dismiss button.
 * @param onConfirm Called when the user accepts.
 * @param onDismiss Called when the user dismisses or taps outside.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, color =
                    MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = dismissLabel) }
        }
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ConfirmDialogPreview() {
    ConfirmDialog(
        title = "Clear watch history",
        message = "Deletes the history of played streams and the playback positions.", confirmLabel = "Delete",
        dismissLabel = "Cancel",
        onConfirm = {},
        onDismiss = {}
    )
}

@Suppress("UnusedPrivateMember")
@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ConfirmDialogResetSettingsPreview() {
    ConfirmDialog(
        title = "Reset settings",
        message = "Resetting all settings will discard all of your preferred settings and " + "restart the app.\n\nAre you sure you want to proceed?",
        confirmLabel = "OK",
        dismissLabel = "Cancel",
        onConfirm = {},
        onDismiss = {}
    )
}