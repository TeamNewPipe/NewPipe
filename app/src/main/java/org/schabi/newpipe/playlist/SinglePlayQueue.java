package org.schabi.newpipe.playlist;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.Collections;

public final class SinglePlayQueue extends PlayQueue {
    public SinglePlayQueue(final StreamInfo info) {
        super(0, Collections.singletonList(new PlayQueueItem(info)));
    }

    public SinglePlayQueue(final StreamInfo info, final int qualityIndex) {
        super(0, Collections.singletonList(new PlayQueueItem(info, qualityIndex)));
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void fetch() {}
}
