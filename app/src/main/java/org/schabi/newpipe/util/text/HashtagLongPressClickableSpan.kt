package org.schabi.newpipe.util.text

import android.content.Context
import android.view.View
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

internal class HashtagLongPressClickableSpan(private val context: Context,
                                             private val parsedHashtag: String,
                                             private val relatedInfoServiceId: Int) : LongPressClickableSpan() {
    override fun onClick(view: View) {
        NavigationHelper.openSearch(context, relatedInfoServiceId, parsedHashtag)
    }

    override fun onLongClick(view: View) {
        ShareUtils.copyToClipboard(context, parsedHashtag)
    }
}
