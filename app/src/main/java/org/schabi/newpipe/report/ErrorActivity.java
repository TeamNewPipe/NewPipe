package org.schabi.newpipe.report;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;

import com.google.android.material.snackbar.Snackbar;
import com.grack.nanojson.JsonWriter;

import org.acra.ReportField;
import org.acra.data.CrashReportData;
import org.schabi.newpipe.ActivityCommunicator;
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

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

public class ErrorActivity extends AppCompatActivity {
    // LOG TAGS
    public static final String TAG = ErrorActivity.class.toString();
    // BUNDLE TAGS
    public static final String ERROR_INFO = "error_info";
    public static final String ERROR_LIST = "error_list";

    public static final String ERROR_EMAIL_ADDRESS = "crashreport@newpipe.schabi.org";
    public static final String ERROR_EMAIL_SUBJECT
            = "Exception in NewPipe " + BuildConfig.VERSION_NAME;
    private String[] errorList;
    private ErrorInfo errorInfo;
    private Class returnActivity;
    private String currentTimeStamp;
    private EditText userCommentBox;

    public static void reportUiError(final AppCompatActivity activity, final Throwable el) {
        reportError(activity, el, activity.getClass(), null, ErrorInfo.make(UserAction.UI_ERROR,
                "none", "", R.string.app_ui_crash));
    }

    public static void reportError(final Context context, final List<Throwable> el,
                                   final Class returnActivity, final View rootView,
                                   final ErrorInfo errorInfo) {
        if (rootView != null) {
            Snackbar.make(rootView, R.string.error_snackbar_message, 3 * 1000)
                    .setActionTextColor(Color.YELLOW)
                    .setAction(context.getString(R.string.error_snackbar_action).toUpperCase(), v ->
                            startErrorActivity(returnActivity, context, errorInfo, el)).show();
        } else {
            startErrorActivity(returnActivity, context, errorInfo, el);
        }
    }

    private static void startErrorActivity(final Class returnActivity, final Context context,
                                           final ErrorInfo errorInfo, final List<Throwable> el) {
        ActivityCommunicator ac = ActivityCommunicator.getCommunicator();
        ac.setReturnActivity(returnActivity);
        Intent intent = new Intent(context, ErrorActivity.class);
        intent.putExtra(ERROR_INFO, errorInfo);
        intent.putExtra(ERROR_LIST, elToSl(el));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void reportError(final Context context, final Throwable e,
                                   final Class returnActivity, final View rootView,
                                   final ErrorInfo errorInfo) {
        List<Throwable> el = null;
        if (e != null) {
            el = new Vector<>();
            el.add(e);
        }
        reportError(context, el, returnActivity, rootView, errorInfo);
    }

    // async call
    public static void reportError(final Handler handler, final Context context,
                                   final Throwable e, final Class returnActivity,
                                   final View rootView, final ErrorInfo errorInfo) {

        List<Throwable> el = null;
        if (e != null) {
            el = new Vector<>();
            el.add(e);
        }
        reportError(handler, context, el, returnActivity, rootView, errorInfo);
    }

    // async call
    public static void reportError(final Handler handler, final Context context,
                                   final List<Throwable> el, final Class returnActivity,
                                   final View rootView, final ErrorInfo errorInfo) {
        handler.post(() -> reportError(context, el, returnActivity, rootView, errorInfo));
    }

    public static void reportError(final Context context, final CrashReportData report,
                                   final ErrorInfo errorInfo) {
        String[] el = new String[]{report.getString(ReportField.STACK_TRACE)};

        Intent intent = new Intent(context, ErrorActivity.class);
        intent.putExtra(ERROR_INFO, errorInfo);
        intent.putExtra(ERROR_LIST, el);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    // errorList to StringList
    private static String[] elToSl(final List<Throwable> stackTraces) {
        String[] out = new String[stackTraces.size()];
        for (int i = 0; i < stackTraces.size(); i++) {
            out[i] = getStackTrace(stackTraces.get(i));
        }
        return out;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        assureCorrectAppLanguage(this);
        super.onCreate(savedInstanceState);
        ThemeHelper.setTheme(this);
        setContentView(R.layout.activity_error);

        Intent intent = getIntent();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.error_report_title);
            actionBar.setDisplayShowTitleEnabled(true);
        }

        Button reportButton = findViewById(R.id.errorReportButton);
        userCommentBox = findViewById(R.id.errorCommentBox);
        TextView errorView = findViewById(R.id.errorView);
        TextView infoView = findViewById(R.id.errorInfosView);
        TextView errorMessageView = findViewById(R.id.errorMessageView);

        ActivityCommunicator ac = ActivityCommunicator.getCommunicator();
        returnActivity = ac.getReturnActivity();
        errorInfo = intent.getParcelableExtra(ERROR_INFO);
        errorList = intent.getStringArrayExtra(ERROR_LIST);

        // important add guru meditation
        addGuruMeditaion();
        currentTimeStamp = getCurrentTimeStamp();

        reportButton.setOnClickListener((View v) -> {
            Context context = this;
            new AlertDialog.Builder(context)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.privacy_policy_title)
                    .setMessage(R.string.start_accept_privacy_policy)
                    .setCancelable(false)
                    .setNeutralButton(R.string.read_privacy_policy, (dialog, which) -> {
                        Intent webIntent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(context.getString(R.string.privacy_policy_url))
                        );
                        context.startActivity(webIntent);
                    })
                    .setPositiveButton(R.string.accept, (dialog, which) -> {
                        final Intent i = new Intent(Intent.ACTION_SENDTO)
                                .setData(Uri.parse("mailto:")) // only email apps should handle this
                                .putExtra(Intent.EXTRA_EMAIL, new String[]{ERROR_EMAIL_ADDRESS})
                                .putExtra(Intent.EXTRA_SUBJECT, ERROR_EMAIL_SUBJECT)
                                .putExtra(Intent.EXTRA_TEXT, buildJson());
                        if (i.resolveActivity(getPackageManager()) != null) {
                            startActivity(i);
                        }

                    })
                    .setNegativeButton(R.string.decline, (dialog, which) -> {
                        // do nothing
                    })
                    .show();

        });

        // normal bugreport
        buildInfo(errorInfo);
        if (errorInfo.message != 0) {
            errorMessageView.setText(errorInfo.message);
        } else {
            errorMessageView.setVisibility(View.GONE);
            findViewById(R.id.messageWhatHappenedView).setVisibility(View.GONE);
        }

        errorView.setText(formErrorText(errorList));

        //print stack trace once again for debugging:
        for (String e : errorList) {
            Log.e(TAG, e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.error_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                goToReturnActivity();
                break;
            case R.id.menu_item_share_error:
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, buildJson());
                intent.setType("text/plain");
                startActivity(Intent.createChooser(intent, getString(R.string.share_dialog_title)));
                break;
        }
        return false;
    }

    private String formErrorText(final String[] el) {
        StringBuilder text = new StringBuilder();
        if (el != null) {
            for (String e : el) {
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

    private void goToReturnActivity() {
        Class<? extends Activity> checkedReturnActivity = getReturnActivity(returnActivity);
        if (checkedReturnActivity == null) {
            super.onBackPressed();
        } else {
            Intent intent = new Intent(this, checkedReturnActivity);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            NavUtils.navigateUpTo(this, intent);
        }
    }

    private void buildInfo(final ErrorInfo info) {
        TextView infoLabelView = findViewById(R.id.errorInfoLabelsView);
        TextView infoView = findViewById(R.id.errorInfosView);
        String text = "";

        infoLabelView.setText(getString(R.string.info_labels).replace("\\n", "\n"));

        text += getUserActionString(info.userAction) + "\n"
                + info.request + "\n"
                + getContentLangString() + "\n"
                + info.serviceName + "\n"
                + currentTimeStamp + "\n"
                + getPackageName() + "\n"
                + BuildConfig.VERSION_NAME + "\n"
                + getOsString();

        infoView.setText(text);
    }

    private String buildJson() {
        try {
            return JsonWriter.string()
                    .object()
                    .value("user_action", getUserActionString(errorInfo.userAction))
                    .value("request", errorInfo.request)
                    .value("content_language", getContentLangString())
                    .value("service", errorInfo.serviceName)
                    .value("package", getPackageName())
                    .value("version", BuildConfig.VERSION_NAME)
                    .value("os", getOsString())
                    .value("time", currentTimeStamp)
                    .array("exceptions", Arrays.asList(errorList))
                    .value("user_comment", userCommentBox.getText().toString())
                    .end()
                    .done();
        } catch (Throwable e) {
            Log.e(TAG, "Error while erroring: Could not build json");
            e.printStackTrace();
        }

        return "";
    }

    private String getUserActionString(final UserAction userAction) {
        if (userAction == null) {
            return "Your description is in another castle.";
        } else {
            return userAction.getMessage();
        }
    }

    private String getContentLangString() {
        String contentLanguage = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(this.getString(R.string.content_country_key), "none");
        if (contentLanguage.equals(getString(R.string.default_localization_key))) {
            contentLanguage = Locale.getDefault().toString();
        }
        return contentLanguage;
    }

    private String getOsString() {
        String osBase = Build.VERSION.SDK_INT >= 23 ? Build.VERSION.BASE_OS : "Android";
        return System.getProperty("os.name")
                + " " + (osBase.isEmpty() ? "Android" : osBase)
                + " " + Build.VERSION.RELEASE
                + " - " + Build.VERSION.SDK_INT;
    }

    private void addGuruMeditaion() {
        //just an easter egg
        TextView sorryView = findViewById(R.id.errorSorryView);
        String text = sorryView.getText().toString();
        text += "\n" + getString(R.string.guru_meditation);
        sorryView.setText(text);
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
        goToReturnActivity();
    }

    public String getCurrentTimeStamp() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        return df.format(new Date());
    }

    public static class ErrorInfo implements Parcelable {
        public static final Parcelable.Creator<ErrorInfo> CREATOR
                = new Parcelable.Creator<ErrorInfo>() {
            @Override
            public ErrorInfo createFromParcel(final Parcel source) {
                return new ErrorInfo(source);
            }

            @Override
            public ErrorInfo[] newArray(final int size) {
                return new ErrorInfo[size];
            }
        };

        final UserAction userAction;
        public final String request;
        final String serviceName;
        @StringRes
        public final int message;

        private ErrorInfo(final UserAction userAction, final String serviceName,
                          final String request, @StringRes final int message) {
            this.userAction = userAction;
            this.serviceName = serviceName;
            this.request = request;
            this.message = message;
        }

        protected ErrorInfo(final Parcel in) {
            this.userAction = UserAction.valueOf(in.readString());
            this.request = in.readString();
            this.serviceName = in.readString();
            this.message = in.readInt();
        }

        public static ErrorInfo make(final UserAction userAction, final String serviceName,
                                     final String request, @StringRes final int message) {
            return new ErrorInfo(userAction, serviceName, request, message);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeString(this.userAction.name());
            dest.writeString(this.request);
            dest.writeString(this.serviceName);
            dest.writeInt(this.message);
        }
    }
}
