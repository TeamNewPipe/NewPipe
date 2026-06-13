package org.schabi.newpipe.util.text;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import org.schabi.newpipe.util.external_communication.ShareUtils;

final class UrlLongPressClickableSpan extends LongPressClickableSpan {

    @NonNull
    private final Context context;
    @NonNull
    private final String url;

    UrlLongPressClickableSpan(@NonNull final Context context,
                              @NonNull final String url) {
        this.context = context;
        this.url = url;
    }

    @Override
    public void onClick(@NonNull final View view) {
        if (!InternalUrlsHandler.handleUrlDescriptionTimestamp(context, url)) {
            ShareUtils.openUrlInApp(context, url);
        }
    }

    @Override
    public void onLongClick(@NonNull final View view) {
        ShareUtils.copyToClipboard(context, url);
    }
}
