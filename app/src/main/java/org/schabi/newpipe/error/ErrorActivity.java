package org.schabi.newpipe.error;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import androidx.core.app.NotificationCompat;
import com.grack.nanojson.JsonWriter;
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ActivityErrorBinding;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.util.ErrorMatcher;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static org.schabi.newpipe.extractor.NewPipe.getDownloader;
import static org.schabi.newpipe.extractor.NewPipe.getYoutubePlayerClient;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

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

    public static final String ERROR_EMAIL_ADDRESS = "feedback@pipeplay.dev";
    public static final String ERROR_EMAIL_SUBJECT = "Exception in ";

    public static final String ERROR_GITHUB_ISSUE_URL
            = "https://github.com/InfinityLoop1308/PipePlay/issues";

    public static final DateTimeFormatter CURRENT_TIMESTAMP_FORMATTER
            = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");


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

        Context context = this;

        if(false) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Downloader downloader = getDownloader();
                    try {
                        String resp = downloader.get(ErrorMatcher.BASE_URL).responseBody();
                        String[] stackTraces = errorInfo.getStackTraces();
                        String matchKind = stackTraces[0].split(":")[0];
                        String targetUrl = new ErrorMatcher(resp).getMatch(matchKind, String.join("", stackTraces));
                        if (targetUrl != null) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(targetUrl));
                            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                                    PendingIntent.FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT : FLAG_UPDATE_CURRENT);

                            String channelId = getString(R.string.notification_channel_id);

                            // Create a notification builder
                            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                                    .setSmallIcon(R.drawable.ic_pipeplay)
                                    .setContentTitle(getString(R.string.error_match_notification_title))
                                    .setContentText("Last update: "
                                            + utils.convertDateToYYYYMMDD(targetUrl.split("-")[targetUrl.split("-").length - 1])
                                            + " - " + getString(R.string.error_match_notification_text))
                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                    .setContentIntent(pendingIntent)
                                    .setAutoCancel(true); // Auto-cancel the notification when clicked

                            // Show the notification
                            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                            ;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                // Define the notification channel
                                NotificationChannel channel = new NotificationChannel(channelId, getString(R.string.error_match_notification_title), NotificationManager.IMPORTANCE_DEFAULT);
                                notificationManager.createNotificationChannel(channel);
                            }

                            notificationManager.notify(0, builder.build());

                        }
                    } catch (Exception ignored) {

                    }
                }
            }).start();
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
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.menu_item_share_error) {
            ShareUtils.shareText(getApplicationContext(),
                    getString(R.string.error_report_title), buildJson());
            return true;
        } else {
            return false;
        }
    }

    private void openPrivacyPolicyDialog(final Context context, final String action) {
        if (action.equals("EMAIL")) { // send on email
            final Intent i = new Intent(Intent.ACTION_SENDTO)
                    .setData(Uri.parse("mailto:")) // only email apps should handle this
                    .putExtra(Intent.EXTRA_EMAIL, new String[]{ERROR_EMAIL_ADDRESS})
                    .putExtra(Intent.EXTRA_SUBJECT, ERROR_EMAIL_SUBJECT
                            + getString(R.string.app_name) + " "
                            + BuildConfig.VERSION_NAME)
                    .putExtra(Intent.EXTRA_TEXT, buildJson());
            ShareUtils.openIntentInApp(context, i, true);
        } else if (action.equals("GITHUB")) { // open the NewPipe issue page on GitHub
            ShareUtils.openUrlInBrowser(this, ERROR_GITHUB_ISSUE_URL, false);
        }
    }

    private String formErrorText(final String[] el) {
        final StringBuilder text = new StringBuilder();
        if (el != null) {
            for (final String e : el) {
                text.append("-------------------------------------\n").append(e);
            }
        }
        text.append("-------------------------------------");
        return text.toString();
    }

    /**
     * Get the checked activity.
     *
     * @param returnActivity the activity to return to
     * @return the casted return activity or null
     */
    @Nullable
    static Class<? extends Activity> getReturnActiIssuesvity(final Class<?> returnActivity) {
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
                    .value("endpoint", getEndpoint())
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
                    .append("\n* __Endpoint:__ ").append(getEndpoint())
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

    private String getEndpoint() {
        return getYoutubePlayerClient();
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
