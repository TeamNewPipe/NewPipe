

package org.schabi.newpipe.report;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.acra.ReportField;
import org.acra.collector.CrashReportData;
import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.ActivityCommunicator;
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.Parser;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Vector;

/**
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
    public static class ErrorInfo implements Parcelable {
        public int userAction;
        public String request;
        public String serviceName;
        public int message;

        public static ErrorInfo make(int userAction, String serviceName, String request, int message) {
            ErrorInfo info = new ErrorInfo();
            info.userAction = userAction;
            info.serviceName = serviceName;
            info.request = request;
            info.message = message;
            return info;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(this.userAction);
            dest.writeString(this.request);
            dest.writeString(this.serviceName);
            dest.writeInt(this.message);
        }

        public ErrorInfo() {
        }

        protected ErrorInfo(Parcel in) {
            this.userAction = in.readInt();
            this.request = in.readString();
            this.serviceName = in.readString();
            this.message = in.readInt();
        }

        public static final Parcelable.Creator<ErrorInfo> CREATOR = new Parcelable.Creator<ErrorInfo>() {
            @Override
            public ErrorInfo createFromParcel(Parcel source) {
                return new ErrorInfo(source);
            }

            @Override
            public ErrorInfo[] newArray(int size) {
                return new ErrorInfo[size];
            }
        };
    }

    // LOG TAGS
    public static final String TAG = ErrorActivity.class.toString();

    // BUNDLE TAGS
    public static final String ERROR_INFO = "error_info";
    public static final String ERROR_LIST = "error_list";

    // MESSAGE ID
    public static final int SEARCHED = 0;
    public static final int REQUESTED_STREAM = 1;
    public static final int GET_SUGGESTIONS = 2;
    public static final int SOMETHING_ELSE = 3;
    public static final int USER_REPORT = 4;
    public static final int LOAD_IMAGE = 5;
    public static final int UI_ERROR = 6;

    // MESSAGE STRING
    public static final String SEARCHED_STRING = "searched";
    public static final String REQUESTED_STREAM_STRING = "requested stream";
    public static final String GET_SUGGESTIONS_STRING = "get suggestions";
    public static final String SOMETHING_ELSE_STRING = "something";
    public static final String USER_REPORT_STRING = "user report";
    public static final String LOAD_IMAGE_STRING = "load image";
    public static final String UI_ERROR_STRING = "ui error";


    public static final String ERROR_EMAIL_ADDRESS = "crashreport@newpipe.schabi.org";
    public static final String ERROR_EMAIL_SUBJECT = "Exception in NewPipe " + BuildConfig.VERSION_NAME;

    private String[] errorList;
    private ErrorInfo errorInfo;
    private Class returnActivity;
    private String currentTimeStamp;
    private String globIpRange;
    Thread globIpRangeThread;

    // views
    private TextView errorView;
    private EditText userCommentBox;
    private Button reportButton;
    private TextView infoView;
    private TextView errorMessageView;

    public static void reportError(final Context context, final List<Throwable> el,
                                   final Class returnAcitivty, View rootView, final ErrorInfo errorInfo) {

        if (rootView != null) {
            Snackbar.make(rootView, R.string.error_snackbar_message, Snackbar.LENGTH_LONG)
                    .setActionTextColor(Color.YELLOW)
                    .setAction(R.string.error_snackbar_action, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ActivityCommunicator ac = ActivityCommunicator.getCommunicator();
                            ac.returnActivity = returnAcitivty;
                            Intent intent = new Intent(context, ErrorActivity.class);
                            intent.putExtra(ERROR_INFO, errorInfo);
                            intent.putExtra(ERROR_LIST, elToSl(el));
                            context.startActivity(intent);
                        }
                    }).show();
        } else {
            ActivityCommunicator ac = ActivityCommunicator.getCommunicator();
            ac.returnActivity = returnAcitivty;
            Intent intent = new Intent(context, ErrorActivity.class);
            intent.putExtra(ERROR_INFO, errorInfo);
            intent.putExtra(ERROR_LIST, elToSl(el));
            context.startActivity(intent);
        }
    }

    public static void reportError(final Context context, final Throwable e,
                                   final Class returnAcitivty, View rootView, final ErrorInfo errorInfo) {
        List<Throwable> el = null;
        if(e != null) {
            el = new Vector<>();
            el.add(e);
        }
        reportError(context, el, returnAcitivty, rootView, errorInfo);
    }

    // async call
    public static void reportError(Handler handler, final Context context, final Throwable e,
                                   final Class returnAcitivty, final View rootView, final ErrorInfo errorInfo) {

        List<Throwable> el = null;
        if(e != null) {
            el = new Vector<>();
            el.add(e);
        }
        reportError(handler, context, el, returnAcitivty, rootView, errorInfo);
    }

    // async call
    public static void reportError(Handler handler, final Context context, final List<Throwable> el,
                                   final Class returnAcitivty, final View rootView, final ErrorInfo errorInfo) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                reportError(context, el, returnAcitivty, rootView, errorInfo);
            }
        });
    }

    public static void reportError(final Context context, final CrashReportData report, final ErrorInfo errorInfo) {
        // get key first (don't ask about this solution)
        ReportField key = null;
        for(ReportField k : report.keySet()) {
            if(k.toString().equals("STACK_TRACE")) {
                key = k;
            }
        }
        String[] el = new String[] { report.get(key) };

        Intent intent = new Intent(context, ErrorActivity.class);
        intent.putExtra(ERROR_INFO, errorInfo);
        intent.putExtra(ERROR_LIST, el);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error);

        Intent intent = getIntent();

        try {
            ActionBar actionBar = getSupportActionBar();
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.error_report_title);
            actionBar.setDisplayShowTitleEnabled(true);
        } catch (Throwable e) {
            Log.e(TAG, "Error turing exception handling");
            e.printStackTrace();
        }

        reportButton = (Button) findViewById(R.id.errorReportButton);
        userCommentBox = (EditText) findViewById(R.id.errorCommentBox);
        errorView = (TextView) findViewById(R.id.errorView);
        infoView = (TextView) findViewById(R.id.errorInfosView);
        errorMessageView = (TextView) findViewById(R.id.errorMessageView);

        ActivityCommunicator ac = ActivityCommunicator.getCommunicator();
        returnActivity = ac.returnActivity;
        errorInfo = intent.getParcelableExtra(ERROR_INFO);
        errorList = intent.getStringArrayExtra(ERROR_LIST);

                //importand add gurumeditaion
        addGuruMeditaion();
        currentTimeStamp = getCurrentTimeStamp();

        reportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:" + ERROR_EMAIL_ADDRESS))
                        .putExtra(Intent.EXTRA_SUBJECT, ERROR_EMAIL_SUBJECT)
                        .putExtra(Intent.EXTRA_TEXT, buildJson());

                startActivity(Intent.createChooser(intent, "Send Email"));
            }
        });
        reportButton.setEnabled(false);

        globIpRangeThread = new Thread(new IpRagneRequester());
        globIpRangeThread.start();

        // normal bugreport
        buildInfo(errorInfo);
        if(errorInfo.message != 0) {
            errorMessageView.setText(errorInfo.message);
        } else {
            errorMessageView.setVisibility(View.GONE);
            findViewById(R.id.messageWhatHappenedView).setVisibility(View.GONE);
        }

        errorView.setText(formErrorText(errorList));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.error_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                goToReturnActivity();
                break;
            case R.id.menu_item_share_error: {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, buildJson());
                intent.setType("text/plain");
                startActivity(Intent.createChooser(intent, getString(R.string.share_dialog_title)));
            }
            break;
        }
        return false;
    }

    private static String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    private String formErrorText(String[] el) {
        String text = "";
        if(el != null) {
            for (String e : el) {
                text += "-------------------------------------\n"
                        + e;
            }
        }
        text += "-------------------------------------";
        return text;
    }

    private void goToReturnActivity() {
        if (returnActivity == null) {
            super.onBackPressed();
        } else {
            Intent intent;
            if (returnActivity != null &&
                    returnActivity.isAssignableFrom(Activity.class)) {
                intent = new Intent(this, returnActivity);
            } else {
                intent = new Intent(this, MainActivity.class);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            NavUtils.navigateUpTo(this, intent);
        }
    }

    private void buildInfo(ErrorInfo info) {
        TextView infoLabelView = (TextView) findViewById(R.id.errorInfoLabelsView);
        TextView infoView = (TextView) findViewById(R.id.errorInfosView);
        String text = "";

        infoLabelView.setText(getString(R.string.info_labels).replace("\\n", "\n"));

        text += getUserActionString(info.userAction)
                + "\n" + info.request
                + "\n" + getContentLangString()
                + "\n" + info.serviceName
                + "\n" + currentTimeStamp
                + "\n" + getPackageName()
                + "\n" + BuildConfig.VERSION_NAME
                + "\n" + getOsString();

        infoView.setText(text);
    }

    private String buildJson() {
        JSONObject errorObject = new JSONObject();

        try {
            errorObject.put("user_action", getUserActionString(errorInfo.userAction))
                    .put("request", errorInfo.request)
                    .put("content_language", getContentLangString())
                    .put("service", errorInfo.serviceName)
                    .put("package", getPackageName())
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("os", getOsString())
                    .put("time", currentTimeStamp)
                    .put("ip_range", globIpRange);

            JSONArray exceptionArray = new JSONArray();
            if(errorList != null) {
                for (String e : errorList) {
                    exceptionArray.put(e);
                }
            }

            errorObject.put("exceptions", exceptionArray);
            errorObject.put("user_comment", userCommentBox.getText().toString());

            return errorObject.toString(3);
        } catch (Throwable e) {
            Log.e(TAG, "Error while erroring: Could not build json");
            e.printStackTrace();
        }

        return "";
    }

    private String getUserActionString(int userAction) {
        switch (userAction) {
            case REQUESTED_STREAM:
                return REQUESTED_STREAM_STRING;
            case SEARCHED:
                return SEARCHED_STRING;
            case GET_SUGGESTIONS:
                return GET_SUGGESTIONS_STRING;
            case SOMETHING_ELSE:
                return SOMETHING_ELSE_STRING;
            case USER_REPORT:
                return USER_REPORT_STRING;
            case LOAD_IMAGE:
                return LOAD_IMAGE_STRING;
            case UI_ERROR:
                return UI_ERROR_STRING;
            default:
                return "Your description is in another castle.";
        }
    }

    private String getContentLangString() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getString(this.getString(R.string.search_language_key), "none");
    }

    private String getOsString() {
        String osBase = Build.VERSION.SDK_INT >= 23 ? Build.VERSION.BASE_OS : "Android";
        return System.getProperty("os.name")
                + " " + (osBase.isEmpty() ? "Android" : osBase)
                + " " + Build.VERSION.RELEASE
                + " - " + Integer.toString(Build.VERSION.SDK_INT);
    }

    private void addGuruMeditaion() {
        //just an easter egg
        TextView sorryView = (TextView) findViewById(R.id.errorSorryView);
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

    private class IpRagneRequester implements Runnable {
        Handler h = new Handler();
        public void run() {
            String ipRange = "none";
            try {
                Downloader dl = new Downloader();
                String ip = dl.download("https://ifcfg.me/ip");

                ipRange = Parser.matchGroup1("([0-9]*\\.[0-9]*\\.)[0-9]*\\.[0-9]*", ip)
                        + "0.0";
            } catch(Throwable e) {
                Log.d(TAG, "Error while error: could not get iprange");
                e.printStackTrace();
            } finally {
                h.post(new IpRageReturnRunnable(ipRange));
            }
        }
    }



    private class IpRageReturnRunnable implements Runnable {
        String ipRange;
        public IpRageReturnRunnable(String ipRange) {
            this.ipRange = ipRange;
        }
        public void run() {
            globIpRange = ipRange;
            if(infoView != null) {
                String text = infoView.getText().toString();
                text += "\n" + globIpRange;
                infoView.setText(text);
                reportButton.setEnabled(true);
            }
        }
    }

    // errorList to StringList
    private static String[] elToSl(List<Throwable> stackTraces) {
        String[] out = new String[stackTraces.size()];
        for(int i = 0; i < stackTraces.size(); i++) {
            out[i] = getStackTrace(stackTraces.get(i));
        }
        return out;
    }
}
