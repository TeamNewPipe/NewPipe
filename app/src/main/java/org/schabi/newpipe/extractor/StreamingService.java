package org.schabi.newpipe.extractor;

import java.io.IOException;

/**
 * Created by Christian Schabesberger on 23.08.15.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamingService.java is part of NewPipe.
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

public abstract class StreamingService {
    public class ServiceInfo {
        public String name = "";
    }

    private int serviceId;

    public StreamingService(int id) {
        serviceId = id;
    }

    public abstract ServiceInfo getServiceInfo();

    public abstract StreamExtractor getExtractorInstance(String url, Downloader downloader)
            throws IOException, ExtractionException;
    public abstract SearchEngine getSearchEngineInstance(Downloader downloader);
    public abstract StreamUrlIdHandler getUrlIdHandlerInstance();

    public final int getServiceId() {
        return serviceId;
    }
}
