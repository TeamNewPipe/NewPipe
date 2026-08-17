package org.schabi.newpipe.error

import android.content.Context
import org.acra.ReportField
import org.acra.data.CrashReportData
import org.acra.sender.ReportSender
import org.schabi.newpipe.R

class AcraReportSender : ReportSender {

    override fun send(context: Context, report: CrashReportData) {
        ErrorUtil.openActivity(
            context,
            ErrorInfo(
                arrayOf(report.getString(ReportField.STACK_TRACE) ?: ""),
                UserAction.UI_ERROR,
                "ACRA report",
                null,
                R.string.app_ui_crash
            )
        )
    }
}
