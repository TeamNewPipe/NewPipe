package org.schabi.newpipe.util.text;

import static org.schabi.newpipe.util.text.InternalUrlsHandler.playOnPopup;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

final class TimestampLongPressClickableSpan extends LongPressClickableSpan {

    @NonNull
    private final Context context;
    @NonNull
    private final String descriptionText;
    @NonNull
    private final CompositeDisposable disposables;
    @NonNull
    private final StreamingService relatedInfoService;
    @NonNull
    private final String relatedStreamUrl;
    @NonNull
    private final TimestampExtractor.TimestampMatchDTO timestampMatchDTO;

    TimestampLongPressClickableSpan(
            @NonNull final Context context,
            @NonNull final String descriptionText,
            @NonNull final CompositeDisposable disposables,
            @NonNull final StreamingService relatedInfoService,
            @NonNull final String relatedStreamUrl,
            @NonNull final TimestampExtractor.TimestampMatchDTO timestampMatchDTO) {
        this.context = context;
        this.descriptionText = descriptionText;
        this.disposables = disposables;
        this.relatedInfoService = relatedInfoService;
        this.relatedStreamUrl = relatedStreamUrl;
        this.timestampMatchDTO = timestampMatchDTO;
    }

    @Override
    public void onClick(@NonNull final View view) {
        playOnPopup(context, relatedStreamUrl, relatedInfoService,
                timestampMatchDTO.seconds(), disposables);
    }

    @Override
    public void onLongClick(@NonNull final View view) {
        ShareUtils.copyToClipboard(context, getTimestampTextToCopy(
                relatedInfoService, relatedStreamUrl, descriptionText, timestampMatchDTO));
    }

    @NonNull
    private static String getTimestampTextToCopy(
            @NonNull final StreamingService relatedInfoService,
            @NonNull final String relatedStreamUrl,
            @NonNull final String descriptionText,
            @NonNull final TimestampExtractor.TimestampMatchDTO timestampMatchDTO) {
        // TODO: use extractor methods to get timestamps when this feature will be implemented in it
        if (relatedInfoService == ServiceList.YouTube) {
            return relatedStreamUrl + "&t=" + timestampMatchDTO.seconds();
        } else if (relatedInfoService == ServiceList.SoundCloud
                || relatedInfoService == ServiceList.MediaCCC) {
            return relatedStreamUrl + "#t=" + timestampMatchDTO.seconds();
        } else if (relatedInfoService == ServiceList.PeerTube) {
            return relatedStreamUrl + "?start=" + timestampMatchDTO.seconds();
        }

        // Return timestamp text for other services
        return descriptionText.subSequence(timestampMatchDTO.timestampStart(),
                timestampMatchDTO.timestampEnd()).toString();
    }
}
