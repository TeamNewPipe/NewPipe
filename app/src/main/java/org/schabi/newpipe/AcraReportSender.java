package org.schabi.newpipe;

import android.content.Context;
import android.util.Log;

import org.acra.collector.CrashReportData;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;

/**
 * Created by the-scrabi on 13.09.16.
 */
public class AcraReportSender implements ReportSender {

    @Override
    public void send(Context context, CrashReportData report) throws ReportSenderException {
        Log.e("Newpipe UI ERROR", report.toString());
        try {
            ((String)null).length();
        } catch(Exception e) {
            ErrorActivity.reportError(context, e, null, null,
                    ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,"none",
                            "App crash, UI failure", R.string.app_ui_crash));
        }
    }
}
