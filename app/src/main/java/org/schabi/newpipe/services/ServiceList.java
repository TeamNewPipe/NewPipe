package org.schabi.newpipe.services;

import android.util.Log;

import org.schabi.newpipe.services.youtube.YoutubeService;

/**
 * Created by Christian Schabesberger on 23.08.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * ServiceList.java is part of NewPipe.
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
public class ServiceList {
    private static final String TAG = ServiceList.class.toString();
    private static final StreamingService[] services = {
        new YoutubeService()
    };
    public static StreamingService[] getServices() {
        return services;
    }
    public static StreamingService getService(int serviceId) {
        return services[serviceId];
    }
    public static StreamingService getService(String serviceName) {
        return services[getIdOfService(serviceName)];
    }
    public static int getIdOfService(String serviceName) {
        for(int i = 0; i < services.length; i++) {
            if(services[i].getServiceInfo().name.equals(serviceName)) {
                return i;
            }
        }
        Log.e(TAG, "Error: Service " + serviceName + " not known.");
        return -1;
    }
}
