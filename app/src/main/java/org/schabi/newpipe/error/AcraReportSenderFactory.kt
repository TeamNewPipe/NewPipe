package org.schabi.newpipe.error

import android.content.Context
import com.google.auto.service.AutoService
import org.acra.config.CoreConfiguration
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderFactory

/**
 * Used by ACRA in [org.schabi.newpipe.App].initAcra() as the factory for report senders.
 */
@AutoService(ReportSenderFactory::class)
class AcraReportSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender {
        return AcraReportSender()
    }
}
