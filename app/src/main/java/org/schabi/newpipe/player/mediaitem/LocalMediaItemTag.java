package org.schabi.newpipe.player.mediaitem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Metadata for media played directly without an online extractor. */
public final class LocalMediaItemTag implements MediaItemTag {
    @NonNull
    private final PlayQueueItem item;
    @Nullable
    private final Object extras;
    private final List<StreamSegment> chapters;

    private LocalMediaItemTag(@NonNull final PlayQueueItem item,
                              @Nullable final Object extras,
                              @NonNull final List<StreamSegment> chapters) {
        this.item = item;
        this.extras = extras;
        this.chapters = Collections.unmodifiableList(new ArrayList<>(chapters));
    }

    @NonNull
    public static LocalMediaItemTag of(@NonNull final PlayQueueItem item) {
        return of(item, Collections.emptyList());
    }

    @NonNull
    public static LocalMediaItemTag of(@NonNull final PlayQueueItem item,
            @NonNull final List<StreamSegment> chapters) {
        if (!item.isDirectMedia()) {
            throw new IllegalArgumentException("Expected a direct media queue item");
        }
        return new LocalMediaItemTag(item, null, chapters);
    }

    @Override
    public List<StreamSegment> getStreamSegments() {
        return chapters;
    }

    @NonNull
    public PlayQueueItem getItem() {
        return item;
    }

    @Override
    public List<Exception> getErrors() {
        return Collections.emptyList();
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

    @Nullable
    @Override
    public String getThumbnailUrl() {
        return item.getLocalThumbnailUrl();
    }

    @Nullable
    @Override
    public String getUploaderUrl() {
        return null;
    }

    @Nullable
    @Override
    public String getAlbumTitle() {
        return item.getAlbum();
    }

    @Override
    public StreamType getStreamType() {
        return item.getStreamType();
    }

    @Override
    public <T> Optional<T> getMaybeExtras(@NonNull final Class<T> type) {
        return Optional.ofNullable(extras).filter(type::isInstance).map(type::cast);
    }

    @Override
    public LocalMediaItemTag withExtras(@NonNull final Object extra) {
        return new LocalMediaItemTag(item, extra, chapters);
    }
}
