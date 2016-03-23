package org.schabi.newpipe.extractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

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

@SuppressWarnings("ALL")
public abstract class SearchEngine {
    public static class NothingFoundException extends ExtractionException {
        public NothingFoundException(String message) {
            super(message);
        }
    }

    private StreamPreviewInfoCollector collector;

    public SearchEngine(StreamUrlIdHandler urlIdHandler, int serviceId) {
        collector = new StreamPreviewInfoCollector(urlIdHandler, serviceId);
    }

    public StreamPreviewInfoCollector getStreamPreviewInfoCollector() {
        return collector;
    }

    public abstract ArrayList<String> suggestionList(
            String query,String contentCountry, Downloader dl)
            throws ExtractionException, IOException;

    //Result search(String query, int page);
    public abstract StreamPreviewInfoCollector search(
            String query, int page, String contentCountry, Downloader dl)
            throws ExtractionException, IOException;
}
