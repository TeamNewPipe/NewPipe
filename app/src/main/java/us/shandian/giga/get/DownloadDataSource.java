package us.shandian.giga.get;

import java.util.List;

/**
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