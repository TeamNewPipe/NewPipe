package org.schabi.newpipe.extractor.stream_info;

import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.FoundAdException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamUrlIdHandler;

import java.util.List;
import java.util.Vector;

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
    private List<StreamPreviewInfo> itemList = new Vector<>();
    private List<Throwable> errors = new Vector<>();
    private UrlIdHandler urlIdHandler;
    private int serviceId = -1;

    public StreamPreviewInfoCollector(UrlIdHandler handler, int serviceId) {
        urlIdHandler = handler;
        this.serviceId = serviceId;
    }

    public List<StreamPreviewInfo> getItemList() {
        return itemList;
    }

    public List<Throwable> getErrors() {
        return errors;
    }

    public void addError(Exception e) {
        errors.add(e);
    }

    public void commit(StreamPreviewInfoExtractor extractor) throws ParsingException {
        try {
            StreamPreviewInfo resultItem = new StreamPreviewInfo();
            // importand information
            resultItem.service_id = serviceId;
            resultItem.webpage_url = extractor.getWebPageUrl();
            if (urlIdHandler == null) {
                throw new ParsingException("Error: UrlIdHandler not set");
            } else if(!resultItem.webpage_url.isEmpty()) {
                resultItem.id = (new YoutubeStreamUrlIdHandler()).getId(resultItem.webpage_url);
            }
            resultItem.title = extractor.getTitle();
            resultItem.stream_type = extractor.getStreamType();

            // optional information
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
            itemList.add(resultItem);
        } catch(FoundAdException ae) {
            System.out.println("AD_WARNING: " + ae.getMessage());
        } catch (Exception e) {
            addError(e);
        }
    }
}
