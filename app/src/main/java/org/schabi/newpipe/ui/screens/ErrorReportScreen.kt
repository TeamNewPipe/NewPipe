/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.common.ScaffoldWithToolbar
import org.schabi.newpipe.ui.theme.AppTheme

@Composable
fun ErrorReportScreen(
    sorryMessage: String,
    errorMessage: String,
    infoLabels: String,
    infoValues: String,
    errorDetails: String,
    onBackClick: () -> Unit,
    onReportViaEmail: (comment: String) -> Unit,
    onCopyForGitHub: (comment: String) -> Unit,
    onReportOnGitHub: () -> Unit,
    onShareError: (comment: String) -> Unit = {}
) {
    var comment by rememberSaveable { mutableStateOf("") }

    ScaffoldWithToolbar(
        title = stringResource(R.string.error_report_title),
        onBackClick = onBackClick,
        actions = {
            IconButton(onClick = { onShareError(comment) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = stringResource(R.string.share)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Sorry header
            Text(
                text = sorryMessage,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // What happened
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.what_happened_headline),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.primary
            )

            // Device info
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.what_device_headline),
                style = MaterialTheme.typography.titleMedium
            )
            Row {
                Text(
                    text = infoLabels,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = infoValues,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .horizontalScroll(rememberScrollState())
                )
            }

            // Error details
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.error_details_headline),
                style = MaterialTheme.typography.titleMedium
            )
            SelectionContainer {
                Text(
                    text = errorDetails,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }

            // User comment
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.your_comment),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                modifier = Modifier.fillMaxWidth()
            )

            // Report via email button
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onReportViaEmail(comment) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.error_report_button_text))
            }

            // GitHub notice
            Text(
                text = stringResource(R.string.error_report_open_github_notice),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 5.dp)
            )

            // Copy for GitHub button
            Button(
                onClick = { onCopyForGitHub(comment) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.copy_for_github))
            }

            // Report on GitHub button
            Button(
                onClick = onReportOnGitHub,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.error_report_open_issue_button_text))
            }
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ErrorReportScreenPreview() {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ErrorReportScreen(
                sorryMessage = "Sorry, that should not have happened.",
                errorMessage = "Requested list not handled",
                infoLabels = "What:\nRequest:\nContent Language:\nContent Country:\nApp Language:\nService:\nTimestamp:\nPackage:\nVersion:\nOS version:",
                infoValues = "Requested list\nnone\nen\nUS\nen_US\nYouTube\n2026-04-17T12:00:00Z\norg.schabi.newpipe\n0.27.5\nAndroid 14 - 34",
                errorDetails = "java.lang.IllegalArgumentException: ...\n\tat org.schabi.newpipe.SomeClass.method(SomeClass.kt:42)",
                onBackClick = {},
                onReportViaEmail = {},
                onCopyForGitHub = {},
                onReportOnGitHub = {}
            )
        }
    }
}
