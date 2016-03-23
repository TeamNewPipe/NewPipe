package org.schabi.newpipe.extractor;

/**
 * Created by Christian Schabesberger on 04.03.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * VideoStream.java is part of NewPipe.
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

public class VideoStream {
    //url of the stream
    public String url = "";
    public int format = -1;
    public String resolution = "";

    public VideoStream(String url, int format, String res) {
        this.url = url; this.format = format; resolution = res;
    }

    // reveals wether two streams are the same, but have diferent urls
    public boolean equalStats(VideoStream cmp) {
        return format == cmp.format
                && resolution == cmp.resolution;
    }

    // revelas wether two streams are equal
    public boolean equals(VideoStream cmp) {
        return cmp != null && equalStats(cmp)
                && url == cmp.url;
    }
}
