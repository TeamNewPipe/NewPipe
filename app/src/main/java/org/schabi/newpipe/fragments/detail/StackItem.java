package org.schabi.newpipe.fragments.detail;

import java.io.Serializable;

class StackItem implements Serializable {
    private int serviceId;
    private String title;
    private String url;

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
