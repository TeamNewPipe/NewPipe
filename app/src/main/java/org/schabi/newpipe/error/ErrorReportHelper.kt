/*
 * SPDX-FileCopyrightText: 2015-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.error

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.grack.nanojson.JsonWriter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * Pure utility functions for building and sending error reports.
 * No Activity dependency — only requires a [Context].
 */
object ErrorReportHelper {

    private val TAG = ErrorReportHelper::class.java.simpleName

    private const val ERROR_EMAIL_ADDRESS = "crashreport@newpipe.schabi.org"
    private const val ERROR_EMAIL_SUBJECT = "Exception in "
    const val ERROR_GITHUB_ISSUE_URL = "https://github.com/TeamNewPipe/NewPipe/issues"

    fun buildJson(context: Context, errorInfo: ErrorInfo, comment: String): String {
        try {
            return JsonWriter.string()
                .`object`()
                .value("user_action", errorInfo.userAction.message)
                .value("request", errorInfo.request)
                .value("content_language", getContentLanguage(context))
                .value("content_country", getContentCountry(context))
                .value("app_language", getAppLanguage())
                .value("service", errorInfo.getServiceName())
                .value("package", context.packageName)
                .value("version", BuildConfig.VERSION_NAME)
                .value("os", getOsString())
                .value("time", getCurrentTimestamp())
                .array("exceptions", errorInfo.stackTraces.toList())
                .value("user_comment", comment)
                .end()
                .done()
        } catch (exception: Exception) {
            Log.e(TAG, "Could not build json", exception)
        }
        return ""
    }

    fun buildMarkdown(context: Context, errorInfo: ErrorInfo, comment: String): String {
        try {
            return buildString(1024) {
                if (comment.isNotEmpty()) {
                    appendLine(comment)
                }

                appendLine("## Exception")
                appendLine("* __User Action:__ ${errorInfo.userAction.message}")
                appendLine("* __Request:__ ${errorInfo.request}")
                appendLine("* __Content Country:__ ${getContentCountry(context)}")
                appendLine("* __Content Language:__ ${getContentLanguage(context)}")
                appendLine("* __App Language:__ ${getAppLanguage()}")
                appendLine("* __Service:__ ${errorInfo.getServiceName()}")
                appendLine("* __Timestamp:__ ${getCurrentTimestamp()}")
                appendLine("* __Package:__ ${context.packageName}")
                appendLine("* __Version:__ ${BuildConfig.VERSION_NAME}")
                appendLine("* __OS:__ ${getOsString()}")

                if (errorInfo.stackTraces.size > 1) {
                    append("<details><summary><b>Exceptions (")
                    append(errorInfo.stackTraces.size)
                    append(")</b></summary><p>\n")
                }

                errorInfo.stackTraces.forEachIndexed { index, stacktrace ->
                    append("<details><summary><b>Crash log ")
                    if (errorInfo.stackTraces.size > 1) {
                        append(index + 1)
                    }
                    append("</b></summary><p>\n")
                    append("\n```\n$stacktrace\n```\n")
                    append("</details>\n")
                }

                if (errorInfo.stackTraces.size > 1) {
                    append("</p></details>\n")
                }

                append("<hr>\n")
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Could not build markdown", exception)
            return ""
        }
    }

    fun sendErrorEmail(context: Context, errorInfo: ErrorInfo, comment: String) {
        val subject = "$ERROR_EMAIL_SUBJECT${context.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}"
        val intent = Intent(Intent.ACTION_SENDTO)
            .setData("mailto:".toUri())
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(ERROR_EMAIL_ADDRESS))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, buildJson(context, errorInfo, comment))
        ShareUtils.openIntentInApp(context, intent)
    }

    fun shareError(context: Context, errorInfo: ErrorInfo, comment: String) {
        ShareUtils.shareText(
            context,
            context.getString(R.string.error_report_title),
            buildJson(context, errorInfo, comment)
        )
    }

    fun copyForGitHub(context: Context, errorInfo: ErrorInfo, comment: String) {
        ShareUtils.copyToClipboard(context, buildMarkdown(context, errorInfo, comment))
    }

    fun openGitHubIssues(context: Context) {
        ShareUtils.openUrlInApp(context, ERROR_GITHUB_ISSUE_URL)
    }

    fun openPrivacyPolicy(context: Context) {
        ShareUtils.openUrlInApp(context, context.getString(R.string.privacy_policy_url))
    }

    private fun getContentLanguage(context: Context): String = Localization.getPreferredLocalization(context).localizationCode

    private fun getContentCountry(context: Context): String = Localization.getPreferredContentCountry(context).countryCode

    private fun getAppLanguage(): String = Localization.getAppLocale().toString()

    private fun getOsString(): String {
        val name = System.getProperty("os.name")!!
        val osBase = Build.VERSION.BASE_OS.ifEmpty { "Android" }
        return "$name $osBase ${Build.VERSION.RELEASE} - ${Build.VERSION.SDK_INT}"
    }

    private fun getCurrentTimestamp(): String = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
