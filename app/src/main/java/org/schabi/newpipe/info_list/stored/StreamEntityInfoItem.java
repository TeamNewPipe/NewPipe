package org.schabi.newpipe.info_list.stored;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

public class StreamEntityInfoItem extends StreamInfoItem {
    protected final long streamId;

    public StreamEntityInfoItem(final long streamId, final int serviceId,
                                final String url, final String name, final StreamType type) {
        super(serviceId, url, name, type);
        this.streamId = streamId;
    }

    public long getStreamId() {
        return streamId;
    }
}
