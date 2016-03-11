package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamUrlIdHandler;

/**
 * Created by Christian Schabesberger on 28.02.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamPreviewInfoCollector.java is part of NewPipe.
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

public class StreamPreviewInfoCollector {
    private SearchResult result = new SearchResult();
    private StreamUrlIdHandler urlIdHandler = null;
    private int serviceId = -1;

    public StreamPreviewInfoCollector(StreamUrlIdHandler handler, int serviceId) {
        urlIdHandler = handler;
        this.serviceId = serviceId;
    }

    public void setSuggestion(String suggestion) {
        result.suggestion = suggestion;
    }

    public void addError(Exception e) {
        result.errors.add(e);
    }

    public SearchResult getSearchResult() {
        return result;
    }

    public void commit(StreamPreviewInfoExtractor extractor) throws ParsingException {
        try {
            StreamPreviewInfo resultItem = new StreamPreviewInfo();
            // importand information
            resultItem.service_id = serviceId;
            resultItem.webpage_url = extractor.getWebPageUrl();
            if (urlIdHandler == null) {
                throw new ParsingException("Error: UrlIdHandler not set");
            } else {
                resultItem.id = (new YoutubeStreamUrlIdHandler()).getVideoId(resultItem.webpage_url);
            }
            resultItem.title = extractor.getTitle();
            resultItem.stream_type = extractor.getStreamType();

            // optional iformation
            try {
                resultItem.duration = extractor.getDuration();
            } catch (Exception e) {
                addError(e);
            }
            try {
                resultItem.uploader = extractor.getUploader();
            } catch (Exception e) {
                addError(e);
            }
            try {
                resultItem.upload_date = extractor.getUploadDate();
            } catch (Exception e) {
                addError(e);
            }
            try {
                resultItem.view_count = extractor.getViewCount();
            } catch (Exception e) {
                addError(e);
            }
            try {
                resultItem.thumbnail_url = extractor.getThumbnailUrl();
            } catch (Exception e) {
                addError(e);
            }

            result.resultList.add(resultItem);
        } catch (Exception e) {
            addError(e);
        }

    }
}
