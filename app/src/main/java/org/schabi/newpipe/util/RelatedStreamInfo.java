package org.schabi.newpipe.util;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RelatedStreamInfo extends ListInfo<InfoItem> {

    private StreamInfoItem nextStream;

    public RelatedStreamInfo(int serviceId, ListLinkHandler listUrlIdHandler, String name) {
        super(serviceId, listUrlIdHandler, name);
    }

    public static RelatedStreamInfo getInfo(StreamInfo info) {
        ListLinkHandler handler = new ListLinkHandler(info.getOriginalUrl(), info.getUrl(), info.getId(), Collections.emptyList(), null);
        RelatedStreamInfo relatedStreamInfo = new RelatedStreamInfo(info.getServiceId(), handler, info.getName());
        List<InfoItem> streams = new ArrayList<>();
        if(info.getNextVideo() != null){
            streams.add(info.getNextVideo());
        }
        streams.addAll(info.getRelatedStreams());
        relatedStreamInfo.setRelatedItems(streams);
        relatedStreamInfo.setNextStream(info.getNextVideo());
        return relatedStreamInfo;
    }

    public StreamInfoItem getNextStream() {
        return nextStream;
    }

    public void setNextStream(StreamInfoItem nextStream) {
        this.nextStream = nextStream;
    }
}
