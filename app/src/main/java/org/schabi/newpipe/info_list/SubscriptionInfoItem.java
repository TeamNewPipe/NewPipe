package org.schabi.newpipe.info_list;

import org.schabi.newpipe.extractor.InfoItem;

public class SubscriptionInfoItem implements InfoItem {

    /* Unique identifiers */
    public int serviceId = -1;
    public String webPageUrl = "";

    /* Contents */
    public String channelName = "";
    public String thumbnailUrl = "";

    public InfoType infoType() {
        return InfoType.CHANNEL;
    }
    public String getTitle() {
        return channelName;
    }
    public String getLink() {
        return webPageUrl;
    }
}
