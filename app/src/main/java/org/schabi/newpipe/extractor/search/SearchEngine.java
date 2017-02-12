package org.schabi.newpipe.extractor.search;

import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream_info.StreamInfoItemCollector;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Created by Christian Schabesberger on 10.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * SearchEngine.java is part of NewPipe.
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

public abstract class SearchEngine {
    public enum Filter {
        VIDEO, CHANNEL, PLAY_LIST
    }

    public static class NothingFoundException extends ExtractionException {
        public NothingFoundException(String message) {
            super(message);
        }
    }
    private InfoItemSearchCollector collector;

    public SearchEngine(UrlIdHandler urlIdHandler, int serviceId) {
        collector = new InfoItemSearchCollector(urlIdHandler, serviceId);
    }

    protected  InfoItemSearchCollector getInfoItemSearchCollector() {
        return collector;
    }
    //Result search(String query, int page);
    public abstract InfoItemSearchCollector search(
            String query, int page, String contentCountry, EnumSet<Filter> filter)
            throws ExtractionException, IOException;
}
