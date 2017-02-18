package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeService;

/**
 * Created by Christian Schabesberger on 23.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * NewPipe.java is part of NewPipe.
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

/**Provides access to the video streaming services supported by NewPipe.
 * Currently only Youtube until the API becomes more stable.*/

@SuppressWarnings("ALL")
public class NewPipe {

    private NewPipe() {
    }

    private static final String TAG = NewPipe.class.toString();


    private static Downloader downloader = null;

    public static StreamingService[] getServices() {
        return ServiceList.serviceList;
    }
    public static StreamingService getService(int serviceId)throws ExtractionException {
        for(StreamingService s : ServiceList.serviceList) {
            if(s.getServiceId() == serviceId) {
                return s;
            }
        }
        return null;
    }
    public static StreamingService getService(String serviceName) throws ExtractionException {
        return ServiceList.serviceList[getIdOfService(serviceName)];
    }
    public static String getNameOfService(int id) {
        try {
            return getService(id).getServiceInfo().name;
        } catch (Exception e) {
            System.err.println("Service id not known");
            e.printStackTrace();
            return "";
        }
    }
    public static int getIdOfService(String serviceName) {
        for(int i = 0; i < ServiceList.serviceList.length; i++) {
            if(ServiceList.serviceList[i].getServiceInfo().name.equals(serviceName)) {
                return i;
            }
        }
        return -1;
    }

    public static void init(Downloader d) {
        downloader = d;
    }

    public static Downloader getDownloader() {
        return downloader;
    }

    public static StreamingService getServiceByUrl(String url) {
        for(StreamingService s : ServiceList.serviceList) {
            if(s.getLinkTypeByUrl(url) != StreamingService.LinkType.NONE) {
                return s;
            }
        }
        return null;
    }
}
