package org.schabi.newpipe.fragments.detail;

import org.schabi.newpipe.player.playqueue.PlayQueue;

import java.io.Serializable;

class StackItem implements Serializable {
    private final int serviceId;
    private String url;
    private String title;
    private PlayQueue playQueue;

    StackItem(final int serviceId, final String url,
              final String title, final PlayQueue playQueue) {
        this.serviceId = serviceId;
        this.url = url;
        this.title = title;
        this.playQueue = playQueue;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public void setPlayQueue(final PlayQueue queue) {
        this.playQueue = queue;
    }

    public int getServiceId() {
        return serviceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    @Override
    public String toString() {
        return getServiceId() + ":" + getUrl() + " > " + getTitle();
    }
}
