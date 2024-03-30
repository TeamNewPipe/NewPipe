package org.schabi.newpipe.error

import android.content.Context
import com.google.auto.service.AutoService
import org.acra.config.CoreConfiguration
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderFactory
import org.schabi.newpipe.App

/*
 * Created by Christian Schabesberger on 13.09.16.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * AcraReportSenderFactory.java is part of NewPipe.
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
/**
 * Used by ACRA in [App].initAcra() as the factory for report senders.
 */
@AutoService(ReportSenderFactory::class)
class AcraReportSenderFactory() : ReportSenderFactory {
    public override fun create(context: Context,
                               config: CoreConfiguration): ReportSender {
        return AcraReportSender()
    }
}
