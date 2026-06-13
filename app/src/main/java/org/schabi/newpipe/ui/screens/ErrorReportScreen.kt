/*
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.os.Build
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.ui.components.common.PrivacyPolicyDialog
import org.schabi.newpipe.ui.components.common.ScaffoldWithToolbar
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization

private const val ACTION_EMAIL = "EMAIL"
private const val ACTION_GITHUB = "GITHUB"

sealed interface ErrorReportEvent {
    data class ReportViaEmail(val comment: String) : ErrorReportEvent
    data class CopyForGitHub(val comment: String) : ErrorReportEvent
    data object ReportOnGitHub : ErrorReportEvent
    data object ReadPrivacyPolicy : ErrorReportEvent
    data class ShareError(val comment: String) : ErrorReportEvent
    data object NavigateUp : ErrorReportEvent
}

@Composable
fun ErrorReportScreen(
    errorInfo: ErrorInfo,
    onEvent: (ErrorReportEvent) -> Unit
) {
    val context = LocalContext.current

    ErrorReportContent(
        errorMessage = errorInfo.getMessage(context).toString(),
        infoLabels = stringResource(R.string.info_labels),
        infoValues = buildInfoString(context, errorInfo),
        errorDetails = formErrorText(errorInfo.stackTraces),
        onEvent = onEvent
    )
}

@Composable
private fun ErrorReportContent(
    errorMessage: String,
    infoLabels: String,
    infoValues: String,
    errorDetails: String,
    onEvent: (ErrorReportEvent) -> Unit
) {
    var comment by rememberSaveable { mutableStateOf("") }
    var privacyDialogAction by rememberSaveable { mutableStateOf<String?>(null) }

    privacyDialogAction?.let { action ->
        PrivacyPolicyDialog(
            onAccept = {
                privacyDialogAction = null
                when (action) {
                    ACTION_EMAIL -> onEvent(ErrorReportEvent.ReportViaEmail(comment))
                    ACTION_GITHUB -> onEvent(ErrorReportEvent.ReportOnGitHub)
                }
            },
            onDecline = { privacyDialogAction = null },
            onReadPrivacyPolicy = { onEvent(ErrorReportEvent.ReadPrivacyPolicy) }
        )
    }

    ScaffoldWithToolbar(
        title = stringResource(R.string.error_report_title),
        onBackClick = { onEvent(ErrorReportEvent.NavigateUp) },
        actions = {
            IconButton(onClick = { onEvent(ErrorReportEvent.ShareError(comment)) }) {
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
                text = stringResource(R.string.sorry_string),
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
                onClick = { privacyDialogAction = ACTION_EMAIL },
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
                onClick = { onEvent(ErrorReportEvent.CopyForGitHub(comment)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.copy_for_github))
            }

            // Report on GitHub button
            Button(
                onClick = { privacyDialogAction = ACTION_GITHUB },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.error_report_open_issue_button_text))
            }
        }
    }
}

private fun buildInfoString(context: Context, errorInfo: ErrorInfo): String {
    val contentLanguage = Localization.getPreferredLocalization(context).localizationCode
    val contentCountry = Localization.getPreferredContentCountry(context).countryCode
    val appLanguage = Localization.getAppLocale().toString()
    val osName = System.getProperty("os.name")!!
    val osBase = Build.VERSION.BASE_OS.ifEmpty { "Android" }
    val osString = "$osName $osBase ${Build.VERSION.RELEASE} - ${Build.VERSION.SDK_INT}"
    val timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    return errorInfo.userAction.message + "\n" +
        errorInfo.request + "\n" +
        contentLanguage + "\n" +
        contentCountry + "\n" +
        appLanguage + "\n" +
        errorInfo.getServiceName() + "\n" +
        timestamp + "\n" +
        context.packageName + "\n" +
        BuildConfig.VERSION_NAME + "\n" +
        osString
}

private fun formErrorText(stackTraces: Array<String>): String {
    val separator = "-------------------------------------"
    return stackTraces.joinToString(separator + "\n", separator + "\n", separator)
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ErrorReportScreenPreview() {
    AppTheme {
        ErrorReportContent(
            errorMessage = "Requested list not handled",
            infoLabels = "What:\nRequest:\nContent Language:\nContent Country:\nApp Language:\nService:\nTimestamp:\nPackage:\nVersion:\nOS version:",
            infoValues = "Requested list\nnone\nen\nUS\nen_US\nYouTube\n2026-04-17T12:00:00Z\norg.schabi.newpipe\n0.27.5\nAndroid 14 - 34",
            errorDetails = "-------------------------------------\njava.lang.IllegalArgumentException: ...\n\tat org.schabi.newpipe.SomeClass.method(SomeClass.kt:42)\n-------------------------------------",
            onEvent = {}
        )
    }
}
