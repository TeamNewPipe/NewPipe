package org.schabi.newpipe.downloadmanager.get;

import java.util.List;

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
 *
 * Provides access to the storage of {@link DownloadMission}s
 */
public interface DownloadDataSource {

    /**
     * Load all missions
     *
     * @return a list of download missions
     */
    List<DownloadMission> loadMissions();

    /**
     * Add a download mission to the storage
     *
     * @param downloadMission the download mission to add
     * @return the identifier of the mission
     */
    void addMission(DownloadMission downloadMission);

    /**
     * Update a download mission which exists in the storage
     *
     * @param downloadMission the download mission to update
     * @throws IllegalArgumentException if the mission was not added to storage
     */
    void updateMission(DownloadMission downloadMission);


    /**
     * Delete a download mission
     *
     * @param downloadMission the mission to delete
     */
    void deleteMission(DownloadMission downloadMission);
}