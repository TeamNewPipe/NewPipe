package org.schabi.newpipe.util.text

import android.content.Context
import android.view.View
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.util.external_communication.ShareUtils

internal class UrlLongPressClickableSpan(private val context: Context,
                                         private val disposables: CompositeDisposable,
                                         private val url: String) : LongPressClickableSpan() {
    override fun onClick(view: View) {
        if (!InternalUrlsHandler.handleUrlDescriptionTimestamp(
                        disposables, context, url)) {
            ShareUtils.openUrlInApp(context, url)
        }
    }

    override fun onLongClick(view: View) {
        ShareUtils.copyToClipboard(context, url)
    }
}
