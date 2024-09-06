package org.schabi.newpipe.ui.components.common.link

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.core.net.toUri
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.util.NavigationHelper

class YouTubeLinkHandler(private val context: Context) : LinkInteractionListener {
    override fun onClick(link: LinkAnnotation) {
        val uri = (link as LinkAnnotation.Url).url.toUri()

        // TODO: Handle other links in NewPipe as well.
        if ("hashtag" in uri.pathSegments) {
            NavigationHelper.openSearch(
                context, ServiceList.YouTube.serviceId, "#${uri.lastPathSegment}"
            )
        } else {
            // Open link in custom browser tab.
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
        }
    }
}
