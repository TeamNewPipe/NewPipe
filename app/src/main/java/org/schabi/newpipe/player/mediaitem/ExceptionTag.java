package org.schabi.newpipe.player.mediaitem;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.List;
import java.util.Optional;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * This {@link MediaItemTag} object is designed to contain metadata for a stream
 * that has failed to load. It supplies metadata from an underlying
 * {@link PlayQueueItem}, which is used by the internal players to resolve actual
 * playback info.
 *
 * This {@link MediaItemTag} does not contain any {@link StreamInfo} that can be
 * used to start playback and can be detected by checking {@link ExceptionTag#getErrors()}
 * when in generic form.
 **/
public final class ExceptionTag implements MediaItemTag {
    @NonNull
    private final PlayQueueItem item;
    @NonNull
    private final List<Exception> errors;
    @Nullable
    private final Object extras;

    private ExceptionTag(@NonNull final PlayQueueItem item,
                         @NonNull final List<Exception> errors,
                         @Nullable final Object extras) {
        this.item = item;
        this.errors = errors;
        this.extras = extras;
    }

    public static ExceptionTag of(@NonNull final PlayQueueItem playQueueItem,
                                  @NonNull final List<Exception> errors) {
        return new ExceptionTag(playQueueItem, errors, null);
    }

    @NonNull
    @Override
    public List<Exception> getErrors() {
        return errors;
    }

    @Override
    public int getServiceId() {
        return item.getServiceId();
    }

    @Override
    public String getTitle() {
        return item.getTitle();
    }

    @Override
    public String getUploaderName() {
        return item.getUploader();
    }

    @Override
    public long getDurationSeconds() {
        return item.getDuration();
    }

    @Override
    public String getStreamUrl() {
        return item.getUrl();
    }

    @Override
    public String getThumbnailUrl() {
        return ImageStrategy.choosePreferredImage(item.getThumbnails());
    }

    @Override
    public String getUploaderUrl() {
        return item.getUploaderUrl();
    }

    @Override
    public StreamType getStreamType() {
        return item.getStreamType();
    }

    @Override
    public <T> Optional<T> getMaybeExtras(@NonNull final Class<T> type) {
        return Optional.ofNullable(extras).map(type::cast);
    }

    @Override
    public <T> MediaItemTag withExtras(@NonNull final T extra) {
        return new ExceptionTag(item, errors, extra);
    }
}
