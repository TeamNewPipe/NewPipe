package org.schabi.newpipe.info_list.stored;

import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Date;

public final class StreamStatisticsInfoItem extends StreamEntityInfoItem {
    private Date latestAccessDate;
    private long watchCount;

    public StreamStatisticsInfoItem(final long streamId, final int serviceId,
                                    final String url, final String name, final StreamType type) {
        super(streamId, serviceId, url, name, type);
    }

    public Date getLatestAccessDate() {
        return latestAccessDate;
    }

    public void setLatestAccessDate(Date latestAccessDate) {
        this.latestAccessDate = latestAccessDate;
    }

    public long getWatchCount() {
        return watchCount;
    }

    public void setWatchCount(long watchCount) {
        this.watchCount = watchCount;
    }
}
