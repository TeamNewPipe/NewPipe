package org.schabi.newpipe.extractor;

/**
 * Created by Christian Schabesberger on 04.03.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * AudioStream.java is part of NewPipe.
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

public class AudioStream {
    public String url = "";
    public int format = -1;
    public int bandwidth = -1;
    public int sampling_rate = -1;

    public AudioStream(String url, int format, int bandwidth, int samplingRate) {
        this.url = url; this.format = format;
        this.bandwidth = bandwidth; this.sampling_rate = samplingRate;
    }

    // reveals wether two streams are the same, but have diferent urls
    public boolean equalStats(AudioStream cmp) {
        return format == cmp.format
                && bandwidth == cmp.bandwidth
                && sampling_rate == cmp.sampling_rate;
    }

    // revelas wether two streams are equal
    public boolean equals(AudioStream cmp) {
        return cmp != null && equalStats(cmp)
                && url == cmp.url;
    }
}
