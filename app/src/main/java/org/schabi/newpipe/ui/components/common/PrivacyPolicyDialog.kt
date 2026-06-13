/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui.components.common

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun PrivacyPolicyDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onReadPrivacyPolicy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = stringResource(R.string.privacy_policy_title)
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.start_accept_privacy_policy)
                )
                TextButton(onClick = onReadPrivacyPolicy) {
                    Text(
                        text = stringResource(R.string.read_privacy_policy),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(
                    text = stringResource(R.string.accept)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(
                    text = stringResource(R.string.decline),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PrivacyPolicyDialogPreview() {
    AppTheme {
        PrivacyPolicyDialog(
            onAccept = {},
            onDecline = {},
            onReadPrivacyPolicy = {}
        )
    }
}
