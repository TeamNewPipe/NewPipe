package org.schabi.newpipe;

/*
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

/**
 * Singleton:
 * Used to send data between certain Activity/Services within the same process.
 * This can be considered as an ugly hack inside the Android universe.
 **/
public class ActivityCommunicator {

    private static ActivityCommunicator activityCommunicator;
    private volatile Class returnActivity;

    public static ActivityCommunicator getCommunicator() {
        if (activityCommunicator == null) {
            activityCommunicator = new ActivityCommunicator();
        }
        return activityCommunicator;
    }

    public Class getReturnActivity() {
        return returnActivity;
    }

    public void setReturnActivity(final Class returnActivity) {
        this.returnActivity = returnActivity;
    }
}
