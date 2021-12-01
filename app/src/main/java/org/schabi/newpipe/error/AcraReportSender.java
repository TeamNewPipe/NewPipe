package org.schabi.newpipe.error;

import android.content.Context;

import androidx.annotation.NonNull;

import org.acra.ReportField;
import org.acra.data.CrashReportData;
import org.acra.sender.ReportSender;
import org.schabi.newpipe.R;

/*
 * Created by Christian Schabesberger  on 13.09.16.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * AcraReportSender.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class AcraReportSender implements ReportSender {

    @Override
    public void send(@NonNull final Context context, @NonNull final CrashReportData report) {
        ErrorUtil.openActivity(context, new ErrorInfo(
                new String[]{report.getString(ReportField.STACK_TRACE)},
                UserAction.UI_ERROR,
                ErrorInfo.SERVICE_NONE,
                "ACRA report",
                R.string.app_ui_crash));
    }
}
