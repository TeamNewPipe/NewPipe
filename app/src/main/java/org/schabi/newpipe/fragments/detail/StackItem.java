package org.schabi.newpipe.fragments.detail;

import java.io.Serializable;

class StackItem implements Serializable {
    private final int serviceId;
    private String title;
    private final String url;

    StackItem(int serviceId, String url, String title) {
        this.serviceId = serviceId;
        this.url = url;
        this.title = title;
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

    @Override
    public String toString() {
        return getServiceId() + ":" + getUrl() + " > " + getTitle();
    }
}
