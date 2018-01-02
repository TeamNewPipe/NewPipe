package org.schabi.newpipe.playlist;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.Collections;

public final class SinglePlayQueue extends PlayQueue {
    public SinglePlayQueue(final StreamInfoItem item) {
        this(new PlayQueueItem(item));
    }

    public SinglePlayQueue(final StreamInfo info) {
        this(new PlayQueueItem(info));
    }

    private SinglePlayQueue(final PlayQueueItem playQueueItem) {
        super(0, Collections.singletonList(playQueueItem));
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void fetch() {}
}
