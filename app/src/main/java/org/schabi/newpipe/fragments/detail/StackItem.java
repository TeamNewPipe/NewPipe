package org.schabi.newpipe.fragments.detail;

import org.schabi.newpipe.player.playqueue.PlayQueue;

import java.io.Serializable;

class StackItem implements Serializable {
    private final int serviceId;
    private String title;
    private final String url;
    private final PlayQueue playQueue;

    StackItem(int serviceId, String url, String title, PlayQueue playQueue) {
        this.serviceId = serviceId;
        this.url = url;
        this.title = title;
        this.playQueue = playQueue;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getServiceId() {
        return serviceId;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public PlayQueue getPlayQueue() { return playQueue; }

    @Override
    public String toString() {
        return getServiceId() + ":" + getUrl() + " > " + getTitle();
    }
}
