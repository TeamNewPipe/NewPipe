package org.schabi.newpipe;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.commons.lang.exception.ExceptionUtils;

import java.util.List;
import java.util.Vector;

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
    public static final String SEARCHED_STRING = "Searched";
    public static final String REQUESTED_STREAM_STRING = "Requested Stream";

    private List<Exception> errorList;
    private ErrorInfo errorInfo;
    private Class returnActivity;

    // views
    private TextView errorView;

    public static void reportError(final Context context, final List<Exception> el,
                                   final Class returnAcitivty, View rootView, final ErrorInfo errorInfo) {
        if(rootView != null) {
            Snackbar.make(rootView, R.string.error_snackbar_message, Snackbar.LENGTH_LONG)
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
            Intent intent = new Intent(context, ErrorActivity.class);
            context.startActivity(intent);
        }
    }

    public static void reportError(final Context context, final Exception e,
                                   final Class returnAcitivty, View rootView, final ErrorInfo errorInfo) {
        List<Exception> el = new Vector<>();
        el.add(e);
        reportError(context, el, returnAcitivty, rootView, errorInfo);
    }

    // async call
    public static void reportError(Handler handler, final Context context, final Exception e,
                                   final Class returnAcitivty, final View rootView, final ErrorInfo errorInfo) {
        List<Exception> el = new Vector<>();
        el.add(e);
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

        errorView = (TextView) findViewById(R.id.errorView);

        errorView.setText(formErrorText(errorList));

        //importand add gurumeditaion
        addGuruMeditaion();
        buildInfo(errorInfo);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            goToReturnActivity();
            return true;
        }
        return false;
    }

    private String formErrorText(List<Exception> el) {
        String text = "";
        for(Exception e : el) {
            text += "-------------------------------------\n"
                    + ExceptionUtils.getStackTrace(e);
        }
        text += "-------------------------------------";
        return text;
    }

    private void goToReturnActivity() {
        if(returnActivity == null) {
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

        String whatString = "";
        switch (info.userAction) {
            case REQUESTED_STREAM:
                whatString = REQUESTED_STREAM_STRING;
                break;
            case SEARCHED:
                whatString = SEARCHED_STRING;
                break;
            default:
                whatString = "Your description is in another castle.";
        }

        String contentLang = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(this.getString(R.string.search_language_key), "none");

        String osBase = Build.VERSION.SDK_INT >= 23 ? Build.VERSION.BASE_OS : "Android";
        String osString = (osBase.isEmpty() ? "Android" : osBase)
                + " " + Build.VERSION.RELEASE;

        text += whatString
                + "\n" + info.request
                + "\n" + contentLang
                + "\n" + info.serviceName
                + "\n" + BuildConfig.VERSION_NAME
                + "\n" + osString;

        infoView.setText(text);
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
}
