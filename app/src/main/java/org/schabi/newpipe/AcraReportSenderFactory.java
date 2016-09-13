package org.schabi.newpipe;

import android.content.Context;

import org.acra.config.ACRAConfiguration;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderFactory;

/**
 * Created by the-scrabi on 13.09.16.
 */
public class AcraReportSenderFactory implements ReportSenderFactory {
    public ReportSender create(Context context, ACRAConfiguration config) {
        return new AcraReportSender();
    }
}
