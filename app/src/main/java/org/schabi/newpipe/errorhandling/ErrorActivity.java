package org.schabi.newpipe.errorhandling;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.widget.TextView;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.schabi.newpipe.ActivityCommunicator;
import org.schabi.newpipe.R;
import org.schabi.newpipe.VideoItemListActivity;

import java.util.List;
import java.util.Vector;

public class ErrorActivity extends AppCompatActivity {

    private List<Exception> errorList;
    private Class returnActivity;

    // views
    private TextView errorView;

    public static void reportError(Context context, List<Exception> el, int message, Class returnAcitivty) {
        ActivityCommunicator ac = ActivityCommunicator.getCommunicator();
        ac.errorList = el;
        ac.returnActivity = returnAcitivty;
        Intent intent = new Intent(context, ErrorActivity.class);
        context.startActivity(intent);
    }

    public static void reportError(Context context, Exception e, int message, Class returnAcitivty) {
        List<Exception> el = new Vector<>();
        el.add(e);
        reportError(context, el, message, returnAcitivty);
    }

    // async call
    public static void reportError(Handler handler, final Context context,
                                   final Exception e, final int message, final Class returnAcitivty) {
        List<Exception> el = new Vector<>();
        el.add(e);
        reportError(handler, context, el, message, returnAcitivty);
    }

    // async call
    public static void reportError(Handler handler, final Context context,
                                   final List<Exception> el, final int message, final Class returnAcitivty) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                reportError(context, el, message, returnAcitivty);
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

        errorView = (TextView) findViewById(R.id.errorView);
        errorView.setText(formErrorText(errorList));

        //importand add gurumeditaion
        addGuruMeditaion();
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
        Intent intent;
        if(returnActivity != null &&
                returnActivity.isAssignableFrom(Activity.class)) {
            intent = new Intent(this, returnActivity);
        } else {
            intent = new Intent(this, VideoItemListActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        NavUtils.navigateUpTo(this, intent);
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
        super.onBackPressed();
        goToReturnActivity();
    }
}
