package org.schabi.newpipe.util.text;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.util.external_communication.ShareUtils;

final class UrlLongPressClickableSpan extends LongPressClickableSpan {

    @NonNull
    private final Context context;
    @NonNull
    private final String url;
    @Nullable
    private final Runnable onAfterTimestampClick;

    UrlLongPressClickableSpan(@NonNull final Context context,
                              @NonNull final String url,
                              @Nullable final Runnable onAfterTimestampClick) {
        this.context = context;
        this.url = url;
        this.onAfterTimestampClick = onAfterTimestampClick;
    }

    @Override
    public void onClick(@NonNull final View view) {
        // handleUrlDescriptionTimestamp returns true when the URL is a timestamp link and the
        // player seek was dispatched; only then scroll back to the player. Regular URLs fall
        // through to the browser/app chooser and should not trigger a scroll.
        if (InternalUrlsHandler.handleUrlDescriptionTimestamp(context, url)) {
            if (onAfterTimestampClick != null) {
                onAfterTimestampClick.run();
            }
        } else {
            ShareUtils.openUrlInApp(context, url);
        }
    }

    @Override
    public void onLongClick(@NonNull final View view) {
        ShareUtils.copyToClipboard(context, url);
    }
}
