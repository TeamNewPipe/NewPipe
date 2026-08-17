package org.schabi.newpipe.util.text

import android.content.Context
import android.view.View
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.views.LongPressClickableSpan

internal class UrlLongPressClickableSpan(
    private val context: Context,
    private val url: String
) : LongPressClickableSpan() {

    override fun onClick(view: View) {
        if (!InternalUrlsHandler.handleUrlDescriptionTimestamp(context, url)) {
            ShareUtils.openUrlInApp(context, url)
        }
    }

    override fun onLongClick(view: View) {
        ShareUtils.copyToClipboard(context, url)
    }
}
