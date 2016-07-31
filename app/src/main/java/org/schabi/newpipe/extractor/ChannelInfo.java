package org.schabi.newpipe.extractor;

import android.util.Log;

import java.util.List;
import java.util.Vector;

/**
 * Created by Christian Schabesberger on 31.07.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * ChannelInfo.java is part of NewPipe.
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

public class ChannelInfo {


    public void addException(Exception e) {
        errors.add(e);
    }

    public static ChannelInfo getInfo(ChannelExtractor extractor, Downloader dl)
        throws ParsingException {
        ChannelInfo info = new ChannelInfo();

        // importand data
        info.service_id = extractor.getServiceId();
        info.channel_name = extractor.getChannelName();

        try {
            info.avatar_url = extractor.getAvatarUrl();
        } catch (Exception e) {
            info.errors.add(e);
        }
        try {
            info.banner_url = extractor.getBannerUrl();
        } catch (Exception e) {
            info.errors.add(e);
        }
        try {
            info.feed_url = extractor.getFeedUrl();
        } catch(Exception e) {
            info.errors.add(e);
        }

        return info;
    }

    public int service_id = -1;
    public String channel_name = "";
    public String avatar_url = "";
    public String banner_url = "";
    public String feed_url = "";

    public List<Throwable> errors = new Vector<>();
}
