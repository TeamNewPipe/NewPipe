package org.schabi.newpipe.error

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ActivityErrorBinding
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Arrays
import java.util.stream.Collectors

/*
 * Created by Christian Schabesberger on 24.10.15.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * ErrorActivity.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * This activity is used to show error details and allow reporting them in various ways. Use [ ][ErrorUtil.openActivity] to correctly open this activity.
 */
class ErrorActivity : AppCompatActivity() {
    private var errorInfo: ErrorInfo? = null
    private var currentTimeStamp: String? = null
    private var activityErrorBinding: ActivityErrorBinding? = null

    // //////////////////////////////////////////////////////////////////////
    // Activity lifecycle
    // //////////////////////////////////////////////////////////////////////
    override fun onCreate(savedInstanceState: Bundle?) {
        Localization.assureCorrectAppLanguage(this)
        super.onCreate(savedInstanceState)
        ThemeHelper.setDayNightMode(this)
        ThemeHelper.setTheme(this)
        activityErrorBinding = ActivityErrorBinding.inflate(
            layoutInflater
        )
        setContentView(activityErrorBinding!!.root)
        val intent = intent
        setSupportActionBar(activityErrorBinding!!.toolbarLayout.toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setTitle(R.string.error_report_title)
            actionBar.setDisplayShowTitleEnabled(true)
        }
        errorInfo = IntentCompat.getParcelableExtra(intent, ERROR_INFO, ErrorInfo::class.java)

        // important add guru meditation
        addGuruMeditation()
        currentTimeStamp = CURRENT_TIMESTAMP_FORMATTER.format(LocalDateTime.now())
        activityErrorBinding!!.errorReportEmailButton.setOnClickListener { v: View? ->
            openPrivacyPolicyDialog(
                this,
                "EMAIL"
            )
        }
        activityErrorBinding!!.errorReportCopyButton.setOnClickListener { v: View? ->
            ShareUtils.copyToClipboard(
                this,
                buildMarkdown()
            )
        }
        activityErrorBinding!!.errorReportGitHubButton.setOnClickListener { v: View? ->
            openPrivacyPolicyDialog(
                this,
                "GITHUB"
            )
        }

        // normal bugreport
        buildInfo(errorInfo)
        activityErrorBinding!!.errorMessageView.setText(errorInfo!!.messageStringId)
        activityErrorBinding!!.errorView.text = formErrorText(errorInfo!!.stackTraces)

        // print stack trace once again for debugging:
        for (e in errorInfo!!.stackTraces) {
            Log.e(TAG, e)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.error_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }

            R.id.menu_item_share_error -> {
                ShareUtils.shareText(
                    applicationContext,
                    getString(R.string.error_report_title), buildJson()
                )
                true
            }

            else -> false
        }
    }

    private fun openPrivacyPolicyDialog(context: Context, action: String) {
        AlertDialog.Builder(context)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.privacy_policy_title)
            .setMessage(R.string.start_accept_privacy_policy)
            .setCancelable(false)
            .setNeutralButton(R.string.read_privacy_policy) { dialog: DialogInterface?, which: Int ->
                ShareUtils.openUrlInApp(
                    context,
                    context.getString(R.string.privacy_policy_url)
                )
            }
            .setPositiveButton(R.string.accept) { dialog: DialogInterface?, which: Int ->
                if (action == "EMAIL") { // send on email
                    val i = Intent(Intent.ACTION_SENDTO)
                        .setData(Uri.parse("mailto:")) // only email apps should handle this
                        .putExtra(Intent.EXTRA_EMAIL, arrayOf(ERROR_EMAIL_ADDRESS))
                        .putExtra(
                            Intent.EXTRA_SUBJECT,
                            ERROR_EMAIL_SUBJECT +
                                getString(R.string.app_name) + " " +
                                BuildConfig.VERSION_NAME
                        )
                        .putExtra(Intent.EXTRA_TEXT, buildJson())
                    ShareUtils.openIntentInApp(context, i)
                } else if (action == "GITHUB") { // open the NewPipe issue page on GitHub
                    ShareUtils.openUrlInApp(this, ERROR_GITHUB_ISSUE_URL)
                }
            }
            .setNegativeButton(R.string.decline, null)
            .show()
    }

    private fun formErrorText(el: Array<String>): String {
        val separator = "-------------------------------------"
        return Arrays.stream(el)
            .collect(
                Collectors.joining(
                    """
    $separator
    
                    """.trimIndent(),
                    """
     $separator
     
                    """.trimIndent(),
                    separator
                )
            )
    }

    private fun buildInfo(info: ErrorInfo?) {
        var text = ""
        activityErrorBinding!!.errorInfoLabelsView.text = getString(R.string.info_labels)
            .replace("\\n", "\n")
        text += """
            ${getUserActionString(info!!.userAction)}
            ${info.request}
            $contentLanguageString
            $contentCountryString
            $appLanguage
            ${info.serviceName}
            $currentTimeStamp
            $packageName
            ${BuildConfig.VERSION_NAME}
            $osString
        """.trimIndent()
        activityErrorBinding!!.errorInfosView.text = text
    }

    private fun buildJson(): String {
        try {
            return JsonWriter.string()
                .`object`()
                .value("user_action", getUserActionString(errorInfo!!.userAction))
                .value("request", errorInfo!!.request)
                .value("content_language", contentLanguageString)
                .value("content_country", contentCountryString)
                .value("app_language", appLanguage)
                .value("service", errorInfo!!.serviceName)
                .value("package", packageName)
                .value("version", BuildConfig.VERSION_NAME)
                .value("os", osString)
                .value("time", currentTimeStamp)
                .array("exceptions", Arrays.asList(*errorInfo!!.stackTraces))
                .value(
                    "user_comment",
                    activityErrorBinding!!.errorCommentBox.text
                        .toString()
                )
                .end()
                .done()
        } catch (e: Throwable) {
            Log.e(TAG, "Error while erroring: Could not build json")
            e.printStackTrace()
        }
        return ""
    }

    private fun buildMarkdown(): String {
        return try {
            val htmlErrorReport = StringBuilder()
            val userComment = activityErrorBinding!!.errorCommentBox.text.toString()
            if (!userComment.isEmpty()) {
                htmlErrorReport.append(userComment).append("\n")
            }

            // basic error info
            htmlErrorReport
                .append("## Exception")
                .append("\n* __User Action:__ ")
                .append(getUserActionString(errorInfo!!.userAction))
                .append("\n* __Request:__ ").append(errorInfo!!.request)
                .append("\n* __Content Country:__ ").append(contentCountryString)
                .append("\n* __Content Language:__ ").append(contentLanguageString)
                .append("\n* __App Language:__ ").append(appLanguage)
                .append("\n* __Service:__ ").append(errorInfo!!.serviceName)
                .append("\n* __Version:__ ").append(BuildConfig.VERSION_NAME)
                .append("\n* __OS:__ ").append(osString).append("\n")

            // Collapse all logs to a single paragraph when there are more than one
            // to keep the GitHub issue clean.
            if (errorInfo!!.stackTraces.size > 1) {
                htmlErrorReport
                    .append("<details><summary><b>Exceptions (")
                    .append(errorInfo!!.stackTraces.size)
                    .append(")</b></summary><p>\n")
            }

            // add the logs
            for (i in errorInfo!!.stackTraces.indices) {
                htmlErrorReport.append("<details><summary><b>Crash log ")
                if (errorInfo!!.stackTraces.size > 1) {
                    htmlErrorReport.append(i + 1)
                }
                htmlErrorReport.append("</b>")
                    .append("</summary><p>\n")
                    .append("\n```\n").append(errorInfo!!.stackTraces[i]).append("\n```\n")
                    .append("</details>\n")
            }

            // make sure to close everything
            if (errorInfo!!.stackTraces.size > 1) {
                htmlErrorReport.append("</p></details>\n")
            }
            htmlErrorReport.append("<hr>\n")
            htmlErrorReport.toString()
        } catch (e: Throwable) {
            Log.e(TAG, "Error while erroring: Could not build markdown")
            e.printStackTrace()
            ""
        }
    }

    private fun getUserActionString(userAction: UserAction?): String? {
        return if (userAction == null) {
            "Your description is in another castle."
        } else {
            userAction.message
        }
    }

    private val contentCountryString: String
        private get() = Localization.getPreferredContentCountry(this).countryCode
    private val contentLanguageString: String
        private get() = Localization.getPreferredLocalization(this).localizationCode
    private val appLanguage: String
        private get() = Localization.getAppLocale(applicationContext).toString()
    private val osString: String
        private get() {
            val osBase =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.BASE_OS else "Android"
            return (
                System.getProperty("os.name") +
                    " " + (if (osBase.isEmpty()) "Android" else osBase) +
                    " " + Build.VERSION.RELEASE +
                    " - " + Build.VERSION.SDK_INT
                )
        }

    private fun addGuruMeditation() {
        // just an easter egg
        var text = activityErrorBinding!!.errorSorryView.text.toString()
        text += """
            
            ${getString(R.string.guru_meditation)}
        """.trimIndent()
        activityErrorBinding!!.errorSorryView.text = text
    }

    companion object {
        // LOG TAGS
        val TAG = ErrorActivity::class.java.toString()

        // BUNDLE TAGS
        const val ERROR_INFO = "error_info"
        const val ERROR_EMAIL_ADDRESS = "crashreport@newpipe.schabi.org"
        const val ERROR_EMAIL_SUBJECT = "Exception in "
        const val ERROR_GITHUB_ISSUE_URL = "https://github.com/TeamNewPipe/NewPipe/issues"
        val CURRENT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        /**
         * Get the checked activity.
         *
         * @param returnActivity the activity to return to
         * @return the casted return activity or null
         */
        @JvmStatic
        fun getReturnActivity(returnActivity: Class<*>?): Class<out Activity?>? {
            var checkedReturnActivity: Class<out Activity?>? = null
            if (returnActivity != null) {
                checkedReturnActivity = if (Activity::class.java.isAssignableFrom(returnActivity)) {
                    returnActivity.asSubclass(Activity::class.java)
                } else {
                    MainActivity::class.java
                }
            }
            return checkedReturnActivity
        }
    }
}
