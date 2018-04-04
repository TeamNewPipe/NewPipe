package org.schabi.newpipe.downloadmanager.get;
/**
 * Copyright (C) 2014 Peter Cai
 * Changes by Christian Schabesberger (C) 2018
 *
 * org.schabi.newpipe.downloadmanager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * org.schabi.newpipe.downloadmanager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with org.schabi.newpipe.downloadmanager.  If not, see <http://www.gnu.org/licenses/>.
 */

public interface DownloadManager {
    int BLOCK_SIZE = 512 * 1024;

    /**
     * Start a new download mission
     *
     * @param url      the url to download
     * @param location the location
     * @param name     the name of the file to create
     * @param isAudio  true if the download is an audio file
     * @param threads  the number of threads maximal used to download chunks of the file.    @return the identifier of the mission.
     */
    int startMission(String url, String location, String name, boolean isAudio, int threads);

    /**
     * Resume the execution of a download mission.
     *
     * @param id the identifier of the mission to resume.
     */
    void resumeMission(int id);

    /**
     * Pause the execution of a download mission.
     *
     * @param id the identifier of the mission to pause.
     */
    void pauseMission(int id);

    /**
     * Deletes the mission from the downloaded list but keeps the downloaded file.
     *
     * @param id The mission identifier
     */
    void deleteMission(int id);

    /**
     * Get the download mission by its identifier
     *
     * @param id the identifier of the download mission
     * @return the download mission or null if the mission doesn't exist
     */
    DownloadMission getMission(int id);

    /**
     * Get the number of download missions.
     *
     * @return the number of download missions.
     */
    int getCount();

}
