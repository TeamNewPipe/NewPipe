package org.schabi.newpipe.extractor.channel;

import org.schabi.newpipe.extractor.exceptions.ParsingException;

/**
 * Created by Christian Schabesberger on 12.02.17.
 *
 * Copyright (C) Christian Schabesberger 2017 <chris.schabesberger@mailbox.org>
 * ChannelInfoItemExtractor.java is part of NewPipe.
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

public interface ChannelInfoItemExtractor {
    String getThumbnailUrl() throws ParsingException;
    String getChannelName() throws ParsingException;
    String getWebPageUrl() throws ParsingException;
    String getDescription() throws ParsingException;
    long getSubscriberCount() throws ParsingException;
    int getVideoAmount() throws ParsingException;
}
