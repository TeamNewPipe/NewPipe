package org.schabi.newpipe.util.external_communication;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.views.LongPressClickableSpan;

final class HashtagLongPressClickableSpan extends LongPressClickableSpan {

    @NonNull
    private final Context context;
    @NonNull
    private final String parsedHashtag;
    @NonNull
    private final Info relatedInfo;

    HashtagLongPressClickableSpan(@NonNull final Context context,
                                  @NonNull final String parsedHashtag,
                                  @NonNull final Info relatedInfo) {
        this.context = context;
        this.parsedHashtag = parsedHashtag;
        this.relatedInfo = relatedInfo;
    }

    @Override
    public void onClick(@NonNull final View view) {
        NavigationHelper.openSearch(context, relatedInfo.getServiceId(), parsedHashtag);
    }

    @Override
    public void onLongClick(@NonNull final View view) {
        ShareUtils.copyToClipboard(context, parsedHashtag);
    }
}
