package org.schabi.newpipe.fragments.detail;

import org.schabi.newpipe.extractor.stream_info.StreamInfo;

import java.io.Serializable;


@SuppressWarnings("WeakerAccess")
public class StackItem implements Serializable {
    private String title, url;
    private StreamInfo info;

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

    public void setInfo(StreamInfo info) {
        this.info = info;
    }

    public StreamInfo getInfo() {
        return info;
    }

    @Override
    public String toString() {
        return getUrl() + " > " + getTitle();
    }
}
