package org.schabi.newpipe.fragments.list.kiosk

import android.os.Bundle
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.kiosk.KioskList
import org.schabi.newpipe.util.KioskTranslator
import org.schabi.newpipe.util.ServiceHelper

class DefaultKioskFragment() : KioskFragment() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (serviceId < 0) {
            updateSelectedDefaultKiosk()
        }
    }

    public override fun onResume() {
        super.onResume()
        if (serviceId != ServiceHelper.getSelectedServiceId(requireContext())) {
            if (currentWorker != null) {
                currentWorker!!.dispose()
            }
            updateSelectedDefaultKiosk()
            reloadContent()
        }
    }

    private fun updateSelectedDefaultKiosk() {
        try {
            serviceId = ServiceHelper.getSelectedServiceId(requireContext())
            val kioskList: KioskList = NewPipe.getService(serviceId).getKioskList()
            kioskId = kioskList.getDefaultKioskId()
            url = kioskList.getListLinkHandlerFactoryByType(kioskId).fromId(kioskId).getUrl()
            kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, requireContext())
            name = kioskTranslatedName
            currentInfo = null
            currentNextPage = null
        } catch (e: ExtractionException) {
            showError(ErrorInfo(e, UserAction.REQUESTED_KIOSK,
                    "Loading default kiosk for selected service"))
        }
    }
}
