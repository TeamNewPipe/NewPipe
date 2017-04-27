package org.schabi.newpipe.subscription;

class SubscribedChannelInfo {
    private String name, link, avatar;
    private int serviceID;
    SubscribedChannelInfo(int serviceID, String name, String link, String avatar) {
        this.serviceID = serviceID;
        this.name = name;
        this.link = link;
        this.avatar = avatar;
    }

    int getID() {return serviceID;}

    public String getName() {
        return name;
    }

    public String getLink() {
        return link;
    }

    String getAvatar() {
        return avatar;
    }

}
