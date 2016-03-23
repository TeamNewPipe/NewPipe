package org.schabi.newpipe.extractor;

import android.graphics.Bitmap;

/**
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * AbstractVideoInfo.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

/**Common properties between StreamInfo and StreamPreviewInfo.*/
public abstract class AbstractVideoInfo {
    public static enum StreamType {
        NONE,   // placeholder to check if stream type was checked or not
        VIDEO_STREAM,
        AUDIO_STREAM,
        LIVE_STREAM,
        AUDIO_LIVE_STREAM,
        FILE
    }

    public StreamType stream_type;
    public int service_id = -1;
    public String id = "";
    public String title = "";
    public String uploader = "";
    public String thumbnail_url = "";
    public Bitmap thumbnail = null;
    public String webpage_url = "";
    public String upload_date = "";
    public long view_count = -1;
}
