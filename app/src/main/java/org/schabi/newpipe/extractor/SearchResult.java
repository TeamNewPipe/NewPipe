package org.schabi.newpipe.extractor;

import java.io.IOException;
import java.util.List;
import java.util.Vector;

/**
 * Created by Christian Schabesberger on 29.02.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * SearchResult.java is part of NewPipe.
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

public class SearchResult {
    public static SearchResult getSearchResult(SearchEngine engine, String query,
                                               int page, String languageCode, Downloader dl)
            throws ExtractionException, IOException {

        SearchResult result = engine.search(query, page, languageCode, dl).getSearchResult();
        if(result.resultList.isEmpty()) {
            if(result.suggestion.isEmpty()) {
                throw new ExtractionException("Empty result despite no error");
            } else {
                // This is used as a fallback. Do not relay on it !!!
                throw new SearchEngine.NothingFoundException(result.suggestion);
            }
        }
        return result;
    }

    public String suggestion = "";
    public final List<StreamPreviewInfo> resultList = new Vector<>();
    public List<Exception> errors = new Vector<>();
}
