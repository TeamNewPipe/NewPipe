package org.schabi.newpipe.extractor;

/**
 * Created by Christian Schabesberger on 11.05.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamPreviewInfoSearchCollector.java is part of NewPipe.
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

public class StreamPreviewInfoSearchCollector extends StreamPreviewInfoCollector {

    private String suggestion = "";

    public StreamPreviewInfoSearchCollector(StreamUrlIdHandler handler, int serviceId) {
        super(handler, serviceId);
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public SearchResult getSearchResult() {
        SearchResult result = new SearchResult();
        result.suggestion = suggestion;
        result.errors = getErrors();
        result.resultList = getItemList();
        return result;
    }
}
