package org.schabi.newpipe.extractor.stream_info;

import org.schabi.newpipe.extractor.InfoItemCollector;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.UrlIdHandler;
import org.schabi.newpipe.extractor.exceptions.FoundAdException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.util.List;
import java.util.Vector;

/**
 * Created by Christian Schabesberger on 28.02.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemCollector.java is part of NewPipe.
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

public class StreamInfoItemCollector extends InfoItemCollector {

    private UrlIdHandler urlIdHandler;

    public StreamInfoItemCollector(UrlIdHandler handler, int serviceId) {
        super(serviceId);
        urlIdHandler = handler;
    }

    private UrlIdHandler getUrlIdHandler() {
        return urlIdHandler;
    }

    public StreamInfoItem extract(StreamInfoItemExtractor extractor) throws Exception {

        StreamInfoItem resultItem = new StreamInfoItem();
        // importand information
        resultItem.service_id = getServiceId();
        resultItem.webpage_url = extractor.getWebPageUrl();
        if (getUrlIdHandler() == null) {
            throw new ParsingException("Error: UrlIdHandler not set");
        } else if (!resultItem.webpage_url.isEmpty()) {
            resultItem.id = NewPipe.getService(getServiceId())
                    .getStreamUrlIdHandlerInstance()
                    .getId(resultItem.webpage_url);
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
        return resultItem;
    }

    public void commit(StreamInfoItemExtractor extractor) throws ParsingException {
        try {
            addItem(extract(extractor));
        } catch(FoundAdException ae) {
            System.out.println("AD_WARNING: " + ae.getMessage());
        } catch (Exception e) {
            addError(e);
        }
    }
}
