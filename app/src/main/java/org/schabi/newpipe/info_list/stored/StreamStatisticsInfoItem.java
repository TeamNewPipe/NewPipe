package org.schabi.newpipe.info_list.stored;

import org.schabi.newpipe.extractor.InfoItem;

import java.util.Date;

public class StreamStatisticsInfoItem extends InfoItem {
    private final long streamId;

    private Date latestAccessDate;
    private long watchCount;

    public StreamStatisticsInfoItem(final long streamId, final int serviceId,
                                    final String url, final String name) {
        super(InfoType.STREAM, serviceId, url, name);
        this.streamId = streamId;
    }

    public long getStreamId() {
        return streamId;
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
