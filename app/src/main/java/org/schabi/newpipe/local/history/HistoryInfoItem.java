package org.schabi.newpipe.local.history;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

public class HistoryInfoItem extends StreamInfoItem {
    public HistoryInfoItem(int serviceId, String url, String name, StreamType streamType) {
        super(serviceId, url, name, streamType);
    }
}
