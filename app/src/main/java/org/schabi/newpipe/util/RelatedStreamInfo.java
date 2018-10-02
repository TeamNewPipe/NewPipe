package org.schabi.newpipe.util;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.Collections;

public class RelatedStreamInfo extends ListInfo<InfoItem> {


    public RelatedStreamInfo(int serviceId, ListLinkHandler listUrlIdHandler, String name) {
        super(serviceId, listUrlIdHandler, name);
    }

    public static RelatedStreamInfo getInfo(StreamInfo info) {
        ListLinkHandler handler = new ListLinkHandler(info.getOriginalUrl(), info.getUrl(), info.getId(), Collections.emptyList(), null);
        RelatedStreamInfo relatedStreamInfo = new RelatedStreamInfo(info.getServiceId(), handler, info.getName());
        relatedStreamInfo.setRelatedItems(info.getRelatedStreams());
        return  relatedStreamInfo;
    }
}
