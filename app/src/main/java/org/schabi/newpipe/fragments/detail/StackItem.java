package org.schabi.newpipe.fragments.detail;

import java.io.Serializable;


@SuppressWarnings("WeakerAccess")
public class StackItem implements Serializable {
    private String title, url;

    public StackItem(String url, String title) {
        this.title = title;
        this.url = url;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return getUrl() + " > " + getTitle();
    }
}
