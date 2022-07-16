package org.schabi.newpipe.util.external_communication;

import static org.schabi.newpipe.util.external_communication.InternalUrlsHandler.playOnPopup;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.views.LongPressClickableSpan;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

final class TimestampLongPressClickableSpan extends LongPressClickableSpan {

    @NonNull
    private final Context context;
    @NonNull
    private final String descriptionText;
    @NonNull
    private final CompositeDisposable disposables;
    @NonNull
    private final StreamInfo streamInfo;
    @NonNull
    private final TimestampExtractor.TimestampMatchDTO timestampMatchDTO;

    TimestampLongPressClickableSpan(
            @NonNull final Context context,
            @NonNull final String descriptionText,
            @NonNull final CompositeDisposable disposables,
            @NonNull final StreamInfo streamInfo,
            @NonNull final TimestampExtractor.TimestampMatchDTO timestampMatchDTO) {
        this.context = context;
        this.descriptionText = descriptionText;
        this.disposables = disposables;
        this.streamInfo = streamInfo;
        this.timestampMatchDTO = timestampMatchDTO;
    }

    @Override
    public void onClick(@NonNull final View view) {
        playOnPopup(context, streamInfo.getUrl(), streamInfo.getService(),
                timestampMatchDTO.seconds(), disposables);
    }

    @Override
    public void onLongClick(@NonNull final View view) {
        ShareUtils.copyToClipboard(context,
                getTimestampTextToCopy(streamInfo, descriptionText, timestampMatchDTO));
    }

    @NonNull
    private static String getTimestampTextToCopy(
            @NonNull final StreamInfo relatedInfo,
            @NonNull final String descriptionText,
            @NonNull final TimestampExtractor.TimestampMatchDTO timestampMatchDTO) {
        // TODO: use extractor methods to get timestamps when this feature will be implemented in it
        final StreamingService streamingService = relatedInfo.getService();
        if (streamingService == ServiceList.YouTube) {
            return relatedInfo.getUrl() + "&t=" + timestampMatchDTO.seconds();
        } else if (streamingService == ServiceList.SoundCloud
                || streamingService == ServiceList.MediaCCC) {
            return relatedInfo.getUrl() + "#t=" + timestampMatchDTO.seconds();
        } else if (streamingService == ServiceList.PeerTube) {
            return relatedInfo.getUrl() + "?start=" + timestampMatchDTO.seconds();
        }

        // Return timestamp text for other services
        return descriptionText.subSequence(timestampMatchDTO.timestampStart(),
                timestampMatchDTO.timestampEnd()).toString();
    }
}
