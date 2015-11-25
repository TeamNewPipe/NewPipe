package org.schabi.newpipe.services;

import android.graphics.Bitmap;

/**Common properties between VideoInfo and VideoPreviewInfo.*/
public abstract class AbstractVideoInfo {
    public String id = "";
    public String title = "";
    public String uploader = "";
    //public int duration = -1;
    public String thumbnail_url = "";
    public Bitmap thumbnail = null;
    public String webpage_url = "";
    public String upload_date = "";
    public long view_count = -1;
}
