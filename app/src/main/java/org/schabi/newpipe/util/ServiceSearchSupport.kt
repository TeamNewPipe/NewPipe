package org.schabi.newpipe.util

import android.view.MenuItem
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory

/** A missing search factory is a service capability, not an empty search result. */
object ServiceSearchSupport {
    @JvmStatic
    fun supportsSearch(serviceId: Int): Boolean = NewPipe.getService(serviceId).searchQHFactory != null

    @JvmStatic
    fun requireSearchFactory(serviceId: Int): SearchQueryHandlerFactory {
        val service = NewPipe.getService(serviceId)
        return service.searchQHFactory
            ?: throw UnsupportedOperationException("${service.serviceInfo.name} keyword search is not supported")
    }

    @JvmStatic
    fun configureAction(item: MenuItem, serviceId: Int, localSearch: Boolean) {
        val search = localSearch || supportsSearch(serviceId)
        item.setTitle(if (search) R.string.search else R.string.open_link)
        item.setIcon(if (search) R.drawable.ic_search else R.drawable.ic_link)
    }
}
