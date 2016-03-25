

package org.schabi.newpipe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NavUtils;
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

import org.json.JSONArray;
import org.json.JSONObject;
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
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * ErrorActivity.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class ErrorActivity extends AppCompatActivity {
    public static class ErrorInfo {
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
    }

    public static final String TAG = ErrorActivity.class.toString();
    public static final int SEARCHED = 0;
    public static final int REQUESTED_STREAM = 1;
    public static final int GET_SUGGESTIONS = 2;
    public static final int SOMETHING_ELSE = 3;
    public static final int USER_REPORT = 4;
    public static final String SEARCHED_STRING = "searched";
    public static final String REQUESTED_STREAM_STRING = "requested stream";
    public static final String GET_SUGGESTIONS_STRING = "get suggestions";
    public static final String SOMETHING_ELSE_STRING = "something";
    public static final String USER_REPORT_STRING = "user report";

    public static final String ERROR_EMAIL_ADDRESS = "crashreport@newpipe.schabi.org";
    public static final String ERROR_EMAIL_SUBJECT = "Exception in NewPipe " + BuildConfig.VERSION_NAME;

    private List<Exception> errorList;
    private ErrorInfo errorInfo;
    private Class returnActivity;
    private String currentTimeStamp;
    private String globIpRange;
    Thread globIpRangeThread = null;

    // views
    private TextView errorView;
    private EditText userCommentBox;
    private Button reportButton;
    private TextView infoView;
    private TextView errorMessageView;

    public static void reportError(final Context context, final List<Exception> el,
                                   final Class returnAcitivty, View rootView, final ErrorInfo errorInfo) {

        if (rootView != null) {
            Snackbar.make(rootView, R.string.error_snackbar_message, Snackbar.LENGTH_LONG)
                    .setActionTextColor(Color.YELLOW)
                    .setAction(R.string.error_snackbar_action, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ActivityCommunicator ac = ActivityCommunicator.getCommunicator();
                            ac.errorList = el;
                            ac.returnActivity = returnAcitivty;
                            ac.errorInfo = errorInfo;
                            Intent intent = new Intent(context, ErrorActivity.class);
                            context.startActivity(intent);
                        }
                    }).show();
        } else {
            ActivityCommunicator ac = ActivityCommunicator.getCommunicator();
            ac.errorList = el;
            ac.returnActivity = returnAcitivty;
            ac.errorInfo = errorInfo;
            Intent intent = new Intent(context, ErrorActivity.class);
            context.startActivity(intent);
        }
    }

    public static void reportError(final Context context, final Exception e,
                                   final Class returnAcitivty, View rootView, final ErrorInfo errorInfo) {
        List<Exception> el = null;
        if(e != null) {
            el = new Vector<>();
            el.add(e);
        }
        reportError(context, el, returnAcitivty, rootView, errorInfo);
    }

    // async call
    public static void reportError(Handler handler, final Context context, final Exception e,
                                   final Class returnAcitivty, final View rootView, final ErrorInfo errorInfo) {

        List<Exception> el = null;
        if(e != null) {
            el = new Vector<>();
            el.add(e);
        }
        reportError(handler, context, el, returnAcitivty, rootView, errorInfo);
    }

    // async call
    public static void reportError(Handler handler, final Context context, final List<Exception> el,
                                   final Class returnAcitivty, final View rootView, final ErrorInfo errorInfo) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                reportError(context, el, returnAcitivty, rootView, errorInfo);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ActivityCommunicator ac = ActivityCommunicator.getCommunicator();
        errorList = ac.errorList;
        returnActivity = ac.returnActivity;
        errorInfo = ac.errorInfo;

        reportButton = (Button) findViewById(R.id.errorReportButton);
        userCommentBox = (EditText) findViewById(R.id.errorCommentBox);
        errorView = (TextView) findViewById(R.id.errorView);
        infoView = (TextView) findViewById(R.id.errorInfosView);
        errorMessageView = (TextView) findViewById(R.id.errorMessageView);

        errorView.setText(formErrorText(errorList));

        //importand add gurumeditaion
        addGuruMeditaion();
        currentTimeStamp = getCurrentTimeStamp();
        buildInfo(errorInfo);

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

        if(errorInfo.message != 0) {
            errorMessageView.setText(errorInfo.message);
        } else {
            errorMessageView.setVisibility(View.GONE);
            findViewById(R.id.messageWhatHappenedView).setVisibility(View.GONE);
        }
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

    private String formErrorText(List<Exception> el) {
        String text = "";
        if(el != null) {
            for (Exception e : el) {
                text += "-------------------------------------\n"
                        + getStackTrace(e);
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
                intent = new Intent(this, VideoItemListActivity.class);
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
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("os", getOsString())
                    .put("time", currentTimeStamp)
                    .put("ip_range", globIpRange);

            JSONArray exceptionArray = new JSONArray();
            if(errorList != null) {
                for (Exception e : errorList) {
                    exceptionArray.put(getStackTrace(e));
                }
            }

            errorObject.put("exceptions", exceptionArray);
            errorObject.put("user_comment", userCommentBox.getText().toString());

            return errorObject.toString(3);
        } catch (Exception e) {
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
            } catch(Exception e) {
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
}
