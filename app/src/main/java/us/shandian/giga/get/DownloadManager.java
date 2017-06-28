package us.shandian.giga.get;

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
