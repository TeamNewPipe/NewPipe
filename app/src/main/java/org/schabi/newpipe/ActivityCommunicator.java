package org.schabi.newpipe;

/**
 * Created by Christian Schabesberger on 24.12.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * ActivityCommunicator.java is part of NewPipe.
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

import android.graphics.Bitmap;

import java.util.List;

/**
 * Singleton:
 * Used to send data between certain Activity/Services within the same process.
 * This can be considered as an ugly hack inside the Android universe. **/
public class ActivityCommunicator {

    private static ActivityCommunicator activityCommunicator = null;

    public static ActivityCommunicator getCommunicator() {
        if(activityCommunicator == null) {
            activityCommunicator = new ActivityCommunicator();
        }
        return activityCommunicator;
    }

    // Thumbnail send from VideoItemDetailFragment to BackgroundPlayer
    public volatile Bitmap backgroundPlayerThumbnail;

    // Sent from any activity to ErrorActivity.
    public volatile List<Exception> errorList;
    public volatile Class returnActivity;
    public volatile ErrorActivity.ErrorInfo errorInfo;
}
