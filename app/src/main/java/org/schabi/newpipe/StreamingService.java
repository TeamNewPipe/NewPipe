package org.schabi.newpipe;

/**
 * Created by Christian Schabesberger on 23.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
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

public interface StreamingService {
    class ServiceInfo {
        public String name = "";
    }
    ServiceInfo getServiceInfo();
    Extractor getExtractorInstance();
    SearchEngine getSearchEngineInstance();

    /**When a VIEW_ACTION is caught this function will test if the url delivered within the calling
    Intent was meant to be watched with this Service.
    Return false if this service shall not allow to be callean through ACTIONs.*/
    boolean acceptUrl(String videoUrl);
}
