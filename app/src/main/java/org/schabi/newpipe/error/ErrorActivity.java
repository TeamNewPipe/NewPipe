package org.schabi.newpipe.error;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ActivityErrorBinding;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

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
 * This activity is used to show error details and allow reporting them in various ways. Use {@link
 * ErrorUtil#openActivity(Context, ErrorInfo)} to correctly open this activity.
 */
public class ErrorActivity extends AppCompatActivity {
    // LOG TAGS
    public static final String TAG = ErrorActivity.class.toString();
    // BUNDLE TAGS
    public static final String ERROR_INFO = "error_info";

    public static final String ERROR_EMAIL_ADDRESS = "crashreport@newpipe.schabi.org";
    public static final String ERROR_EMAIL_SUBJECT = "Exception in ";

    public static final String ERROR_GITHUB_ISSUE_URL =
            "https://github.com/TeamNewPipe/NewPipe/issues";

    public static final DateTimeFormatter CURRENT_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");


    private ErrorInfo errorInfo;
    private String currentTimeStamp;

    private ActivityErrorBinding activityErrorBinding;


    ////////////////////////////////////////////////////////////////////////
    // Activity lifecycle
    ////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        assureCorrectAppLanguage(this);
        super.onCreate(savedInstanceState);

        ThemeHelper.setDayNightMode(this);
        ThemeHelper.setTheme(this);

        activityErrorBinding = ActivityErrorBinding.inflate(getLayoutInflater());
        setContentView(activityErrorBinding.getRoot());

        final Intent intent = getIntent();

        setSupportActionBar(activityErrorBinding.toolbarLayout.toolbar);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.error_report_title);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        errorInfo = intent.getParcelableExtra(ERROR_INFO);

        // important add guru meditation
        addGuruMeditation();
        currentTimeStamp = CURRENT_TIMESTAMP_FORMATTER.format(LocalDateTime.now());

        activityErrorBinding.errorReportEmailButton.setOnClickListener(v ->
                openPrivacyPolicyDialog(this, "EMAIL"));

        activityErrorBinding.errorReportCopyButton.setOnClickListener(v ->
                ShareUtils.copyToClipboard(this, buildMarkdown()));

        activityErrorBinding.errorReportGitHubButton.setOnClickListener(v ->
                openPrivacyPolicyDialog(this, "GITHUB"));

        // normal bugreport
        buildInfo(errorInfo);
        activityErrorBinding.errorMessageView.setText(errorInfo.getMessageStringId());
        activityErrorBinding.errorView.setText(formErrorText(errorInfo.getStackTraces()));

        // print stack trace once again for debugging:
        for (final String e : errorInfo.getStackTraces()) {
            Log.e(TAG, e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.error_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_item_share_error:
                ShareUtils.shareText(getApplicationContext(),
                        getString(R.string.error_report_title), buildJson());
                return true;
            default:
                return false;
        }
    }

    private void openPrivacyPolicyDialog(final Context context, final String action) {
        new AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.privacy_policy_title)
                .setMessage(R.string.start_accept_privacy_policy)
                .setCancelable(false)
                .setNeutralButton(R.string.read_privacy_policy, (dialog, which) ->
                        ShareUtils.openUrlInApp(context,
                                context.getString(R.string.privacy_policy_url)))
                .setPositiveButton(R.string.accept, (dialog, which) -> {
                    if (action.equals("EMAIL")) { // send on email
                        final Intent i = new Intent(Intent.ACTION_SENDTO)
                                .setData(Uri.parse("mailto:")) // only email apps should handle this
                                .putExtra(Intent.EXTRA_EMAIL, new String[]{ERROR_EMAIL_ADDRESS})
                                .putExtra(Intent.EXTRA_SUBJECT, ERROR_EMAIL_SUBJECT
                                        + getString(R.string.app_name) + " "
                                        + BuildConfig.VERSION_NAME)
                                .putExtra(Intent.EXTRA_TEXT, buildJson());
                        ShareUtils.openIntentInApp(context, i);
                    } else if (action.equals("GITHUB")) { // open the NewPipe issue page on GitHub
                        ShareUtils.openUrlInApp(this, ERROR_GITHUB_ISSUE_URL);
                    }
                })
                .setNegativeButton(R.string.decline, null)
                .show();
    }

    private String formErrorText(final String[] el) {
        final String separator = "-------------------------------------";
        return Arrays.stream(el)
                .collect(Collectors.joining(separator + "\n", separator + "\n", separator));
    }

    /**
     * Get the checked activity.
     *
     * @param returnActivity the activity to return to
     * @return the casted return activity or null
     */
    @Nullable
    static Class<? extends Activity> getReturnActivity(final Class<?> returnActivity) {
        Class<? extends Activity> checkedReturnActivity = null;
        if (returnActivity != null) {
            if (Activity.class.isAssignableFrom(returnActivity)) {
                checkedReturnActivity = returnActivity.asSubclass(Activity.class);
            } else {
                checkedReturnActivity = MainActivity.class;
            }
        }
        return checkedReturnActivity;
    }

    private void buildInfo(final ErrorInfo info) {
        String text = "";

        activityErrorBinding.errorInfoLabelsView.setText(getString(R.string.info_labels)
                .replace("\\n", "\n"));

        text += getUserActionString(info.getUserAction()) + "\n"
                + info.getRequest() + "\n"
                + getContentLanguageString() + "\n"
                + getContentCountryString() + "\n"
                + getAppLanguage() + "\n"
                + info.getServiceName() + "\n"
                + currentTimeStamp + "\n"
                + getPackageName() + "\n"
                + BuildConfig.VERSION_NAME + "\n"
                + getOsString();

        activityErrorBinding.errorInfosView.setText(text);
    }

    private String buildJson() {
        try {
            return JsonWriter.string()
                    .object()
                    .value("user_action", getUserActionString(errorInfo.getUserAction()))
                    .value("request", errorInfo.getRequest())
                    .value("content_language", getContentLanguageString())
                    .value("content_country", getContentCountryString())
                    .value("app_language", getAppLanguage())
                    .value("service", errorInfo.getServiceName())
                    .value("package", getPackageName())
                    .value("version", BuildConfig.VERSION_NAME)
                    .value("os", getOsString())
                    .value("time", currentTimeStamp)
                    .array("exceptions", Arrays.asList(errorInfo.getStackTraces()))
                    .value("user_comment", activityErrorBinding.errorCommentBox.getText()
                            .toString())
                    .end()
                    .done();
        } catch (final Throwable e) {
            Log.e(TAG, "Error while erroring: Could not build json");
            e.printStackTrace();
        }

        return "";
    }

    private String buildMarkdown() {
        try {
            final StringBuilder htmlErrorReport = new StringBuilder();

            final String userComment = activityErrorBinding.errorCommentBox.getText().toString();
            if (!userComment.isEmpty()) {
                htmlErrorReport.append(userComment).append("\n");
            }

            // basic error info
            htmlErrorReport
                    .append("## Exception")
                    .append("\n* __User Action:__ ")
                        .append(getUserActionString(errorInfo.getUserAction()))
                    .append("\n* __Request:__ ").append(errorInfo.getRequest())
                    .append("\n* __Content Country:__ ").append(getContentCountryString())
                    .append("\n* __Content Language:__ ").append(getContentLanguageString())
                    .append("\n* __App Language:__ ").append(getAppLanguage())
                    .append("\n* __Service:__ ").append(errorInfo.getServiceName())
                    .append("\n* __Version:__ ").append(BuildConfig.VERSION_NAME)
                    .append("\n* __OS:__ ").append(getOsString()).append("\n");


            // Collapse all logs to a single paragraph when there are more than one
            // to keep the GitHub issue clean.
            if (errorInfo.getStackTraces().length > 1) {
                htmlErrorReport
                        .append("<details><summary><b>Exceptions (")
                        .append(errorInfo.getStackTraces().length)
                        .append(")</b></summary><p>\n");
            }

            // add the logs
            for (int i = 0; i < errorInfo.getStackTraces().length; i++) {
                htmlErrorReport.append("<details><summary><b>Crash log ");
                if (errorInfo.getStackTraces().length > 1) {
                    htmlErrorReport.append(i + 1);
                }
                htmlErrorReport.append("</b>")
                        .append("</summary><p>\n")
                        .append("\n```\n").append(errorInfo.getStackTraces()[i]).append("\n```\n")
                        .append("</details>\n");
            }

            // make sure to close everything
            if (errorInfo.getStackTraces().length > 1) {
                htmlErrorReport.append("</p></details>\n");
            }
            htmlErrorReport.append("<hr>\n");
            return htmlErrorReport.toString();
        } catch (final Throwable e) {
            Log.e(TAG, "Error while erroring: Could not build markdown");
            e.printStackTrace();
            return "";
        }
    }

    private String getUserActionString(final UserAction userAction) {
        if (userAction == null) {
            return "Your description is in another castle.";
        } else {
            return userAction.getMessage();
        }
    }

    private String getContentCountryString() {
        return Localization.getPreferredContentCountry(this).getCountryCode();
    }

    private String getContentLanguageString() {
        return Localization.getPreferredLocalization(this).getLocalizationCode();
    }

    private String getAppLanguage() {
        return Localization.getAppLocale(getApplicationContext()).toString();
    }

    private String getOsString() {
        final String osBase = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? Build.VERSION.BASE_OS : "Android";
        return System.getProperty("os.name")
                + " " + (osBase.isEmpty() ? "Android" : osBase)
                + " " + Build.VERSION.RELEASE
                + " - " + Build.VERSION.SDK_INT;
    }

    private void addGuruMeditation() {
        //just an easter egg
        String text = activityErrorBinding.errorSorryView.getText().toString();
        text += "\n" + getString(R.string.guru_meditation);
        activityErrorBinding.errorSorryView.setText(text);
    }
}
