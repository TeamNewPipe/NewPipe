/*
 * SPDX-FileCopyrightText: 2015-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.error

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.grack.nanojson.JsonWriter
import dagger.hilt.android.AndroidEntryPoint
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.BaseActivity
import org.schabi.newpipe.ui.screens.ErrorReportScreen
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * This activity is used to show error details and allow reporting them in various ways.
 * Use [ErrorUtil.openActivity] to correctly open this activity.
 */
@AndroidEntryPoint
class ErrorActivity : BaseActivity() {
    private lateinit var errorInfo: ErrorInfo
    private lateinit var currentTimeStamp: String

    private val contentCountryString: String
        get() = Localization.getPreferredContentCountry(this).countryCode

    private val contentLanguageString: String
        get() = Localization.getPreferredLocalization(this).localizationCode

    private val appLanguage: String
        get() = Localization.getAppLocale().toString()

    private val osString: String
        get() {
            val name = System.getProperty("os.name")!!
            val osBase = Build.VERSION.BASE_OS.ifEmpty { "Android" }
            return "$name $osBase ${Build.VERSION.RELEASE} - ${Build.VERSION.SDK_INT}"
        }

    private val errorEmailSubject: String
        get() = "$ERROR_EMAIL_SUBJECT ${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}"

    // /////////////////////////////////////////////////////////////////////
    // Activity lifecycle
    // /////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        errorInfo = IntentCompat.getParcelableExtra(intent, ERROR_INFO, ErrorInfo::class.java)!!

        // print current time, as zoned ISO8601 timestamp
        currentTimeStamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        // print stack trace once again for debugging:
        errorInfo.stackTraces.forEach { Log.e(TAG, it) }

        val sorryMessage = getString(R.string.sorry_string)
        val errorMessage = errorInfo.getMessage(this).toString()
        val infoLabels = getString(R.string.info_labels)
        val infoValues = buildInfoString()
        val errorDetails = formErrorText(errorInfo.stackTraces)

        composeSetContent {
            ErrorReportScreen(
                sorryMessage = sorryMessage,
                errorMessage = errorMessage,
                infoLabels = infoLabels,
                infoValues = infoValues,
                errorDetails = errorDetails,
                onBackClick = { finish() },
                onReportViaEmail = { comment -> sendErrorEmail(comment) },
                onCopyForGitHub = { comment ->
                    ShareUtils.copyToClipboard(this, buildMarkdown(comment))
                },
                onReportOnGitHub = {
                    ShareUtils.openUrlInApp(this, ERROR_GITHUB_ISSUE_URL)
                },
                onReadPrivacyPolicy = {
                    ShareUtils.openUrlInApp(this, getString(R.string.privacy_policy_url))
                },
                onShareError = { comment ->
                    ShareUtils.shareText(
                        applicationContext,
                        getString(R.string.error_report_title),
                        buildJson(comment)
                    )
                }
            )
        }
    }

    private fun sendErrorEmail(comment: String) {
        val intent = Intent(Intent.ACTION_SENDTO)
            .setData("mailto:".toUri())
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(ERROR_EMAIL_ADDRESS))
            .putExtra(Intent.EXTRA_SUBJECT, errorEmailSubject)
            .putExtra(Intent.EXTRA_TEXT, buildJson(comment))
        ShareUtils.openIntentInApp(this, intent)
    }

    private fun formErrorText(stacktrace: Array<String>): String {
        val separator = "-------------------------------------"
        return stacktrace.joinToString(separator + "\n", separator + "\n", separator)
    }

    private fun buildInfoString(): String {
        return errorInfo.userAction.message + "\n" +
            errorInfo.request + "\n" +
            contentLanguageString + "\n" +
            contentCountryString + "\n" +
            appLanguage + "\n" +
            errorInfo.getServiceName() + "\n" +
            currentTimeStamp + "\n" +
            packageName + "\n" +
            BuildConfig.VERSION_NAME + "\n" +
            osString
    }

    private fun buildJson(comment: String): String {
        try {
            return JsonWriter.string()
                .`object`()
                .value("user_action", errorInfo.userAction.message)
                .value("request", errorInfo.request)
                .value("content_language", contentLanguageString)
                .value("content_country", contentCountryString)
                .value("app_language", appLanguage)
                .value("service", errorInfo.getServiceName())
                .value("package", packageName)
                .value("version", BuildConfig.VERSION_NAME)
                .value("os", osString)
                .value("time", currentTimeStamp)
                .array("exceptions", errorInfo.stackTraces.toList())
                .value("user_comment", comment)
                .end()
                .done()
        } catch (exception: Exception) {
            Log.e(TAG, "Error while erroring: Could not build json", exception)
        }

        return ""
    }

    private fun buildMarkdown(comment: String): String {
        try {
            return buildString(1024) {
                if (comment.isNotEmpty()) {
                    appendLine(comment)
                }

                // basic error info
                appendLine("## Exception")
                appendLine("* __User Action:__ ${errorInfo.userAction.message}")
                appendLine("* __Request:__ ${errorInfo.request}")
                appendLine("* __Content Country:__ $contentCountryString")
                appendLine("* __Content Language:__ $contentLanguageString")
                appendLine("* __App Language:__ $appLanguage")
                appendLine("* __Service:__ ${errorInfo.getServiceName()}")
                appendLine("* __Timestamp:__ $currentTimeStamp")
                appendLine("* __Package:__ $packageName")
                appendLine("* __Version:__ ${BuildConfig.VERSION_NAME}")
                appendLine("* __OS:__ $osString")

                // Collapse all logs to a single paragraph when there are more than one
                // to keep the GitHub issue clean.
                if (errorInfo.stackTraces.size > 1) {
                    append("<details><summary><b>Exceptions (")
                    append(errorInfo.stackTraces.size)
                    append(")</b></summary><p>\n")
                }

                // add the logs
                errorInfo.stackTraces.forEachIndexed { index, stacktrace ->
                    append("<details><summary><b>Crash log ")
                    if (errorInfo.stackTraces.size > 1) {
                        append(index + 1)
                    }
                    append("</b>")
                    append("</summary><p>\n")
                    append("\n```\n${stacktrace}\n```\n")
                    append("</details>\n")
                }

                // make sure to close everything
                if (errorInfo.stackTraces.size > 1) {
                    append("</p></details>\n")
                }

                append("<hr>\n")
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Error while erroring: Could not build markdown", exception)
            return ""
        }
    }

    companion object {
        private val TAG = ErrorActivity::class.java.toString()

        const val ERROR_INFO = "error_info"

        private const val ERROR_EMAIL_ADDRESS = "crashreport@newpipe.schabi.org"
        private const val ERROR_EMAIL_SUBJECT = "Exception in "

        private const val ERROR_GITHUB_ISSUE_URL = "https://github.com/TeamNewPipe/NewPipe/issues"
    }
}
