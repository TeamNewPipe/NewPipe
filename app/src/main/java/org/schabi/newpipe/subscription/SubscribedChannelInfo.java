package org.schabi.newpipe.subscription;

class SubscribedChannelInfo {
    private String name, link, avatar;
    SubscribedChannelInfo(String name, String link, String avatar) {
        this.name = name;
        this.link = link;
        this.avatar = avatar;
    }

    public String getName() {
        return name;
    }

    public String getLink() {
        return link;
    }

    public String getAvatar() {
        return avatar;
    }
}
