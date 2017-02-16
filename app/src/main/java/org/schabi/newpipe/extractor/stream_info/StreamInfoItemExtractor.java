package org.schabi.newpipe.extractor.stream_info;

import org.schabi.newpipe.extractor.AbstractStreamInfo;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

/**
 * Created by Christian Schabesberger on 28.02.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemExtractor.java is part of NewPipe.
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

public interface StreamInfoItemExtractor {
    AbstractStreamInfo.StreamType getStreamType() throws ParsingException;
    String getWebPageUrl() throws ParsingException;
    String getTitle() throws ParsingException;
    int getDuration() throws ParsingException;
    String getUploader() throws ParsingException;
    String getUploadDate() throws ParsingException;
    long getViewCount() throws  ParsingException;
    String getThumbnailUrl() throws  ParsingException;
}
