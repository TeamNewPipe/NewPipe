package org.schabi.newpipe.playlist;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.Collections;

public final class SinglePlayQueue extends PlayQueue {
    public static final String STREAM = "stream";

    public SinglePlayQueue(final StreamInfo info) {
        super(0, Collections.singletonList(new PlayQueueItem(info)));
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void fetch() {}
}
