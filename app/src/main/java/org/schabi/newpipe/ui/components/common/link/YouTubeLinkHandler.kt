package org.schabi.newpipe.ui.components.common.link

import android.content.Context
import androidx.compose.ui.platform.AndroidUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.core.net.toUri
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.util.NavigationHelper

class YouTubeLinkHandler(private val context: Context) : LinkInteractionListener {
    private val uriHandler = AndroidUriHandler(context)

    override fun onClick(link: LinkAnnotation) {
        val url = (link as LinkAnnotation.Url).url
        val uri = url.toUri()

        // TODO: Handle other links in NewPipe as well.
        if ("hashtag" in uri.pathSegments) {
            NavigationHelper.openSearch(
                context, ServiceList.YouTube.serviceId, "#${uri.lastPathSegment}"
            )
        } else {
            uriHandler.openUri(url)
        }
    }
}
