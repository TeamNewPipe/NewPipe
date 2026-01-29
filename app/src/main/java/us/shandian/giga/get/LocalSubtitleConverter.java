package us.shandian.giga.get;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import android.util.Log;

import org.schabi.newpipe.streams.io.SharpStream;

import org.schabi.newpipe.extractor.utils.SubtitleDeduplicator;

final class LocalSubtitleConverter {

    private static final String TAG = "LocalSubtitleConverter";

    private LocalSubtitleConverter() {
        // no instance
    }

    /**
     * Converts a local(file://) TTML subtitle file into VTT format
     * and stores it in the user-defined/chosen directory.
     *
     * @param localSubtitleUri file:// URI of the local TTML subtitle
     * @param subtitleMission current download mission, and it is a command
     *                initiated manually by the user (via a button).
     * @return 0 if success, non-zero error code otherwise
     */
    public static int convertLocalTtmlToVtt(String localSubtitleUri,
                                       DownloadMission subtitleMission) {

        if (!isValidLocalUri(localSubtitleUri)) {
            return 1;
        }

        File ttmlFile = new File(
                getAbsolutePathFromLocalUri(localSubtitleUri)
        );

        if (!ttmlFile.exists()) {
            subtitleMission.notifyError(DownloadMission.ERROR_FILE_CREATION, null);
            return 2;
        }

        if (!subtitleMission.storage.canWrite()) {
            subtitleMission.notifyError(DownloadMission.ERROR_PERMISSION_DENIED, null);
            return 3;
        }

        writeLocalTtmlAsVtt(ttmlFile, subtitleMission);

        printLocalSubtitleConvertedOk(subtitleMission);

        return 0;
    }


    private static boolean isValidLocalUri(String localUri) {
        String URL_PREFIX = SubtitleDeduplicator.LOCAL_SUBTITLE_URL_PREFIX;

        if (localUri.length() <= URL_PREFIX.length()) {
             return false;
        }

        return true;
    }

    private static String getAbsolutePathFromLocalUri(String localSubtitleUri) {
        String URL_PREFIX = SubtitleDeduplicator.LOCAL_SUBTITLE_URL_PREFIX;
        int prefixLength = URL_PREFIX.length();
        // Remove URL_PREFIX
        String absolutePath = localSubtitleUri.substring(prefixLength);
        return absolutePath;
    }

    private static void writeLocalTtmlAsVtt(File localTtmlFile,
                                            DownloadMission mission) {
        try (FileInputStream inputTtmlStream = new FileInputStream(localTtmlFile);
             SharpStream outputVttStream = mission.storage.getStream()) {

            byte[] buffer = new byte[DownloadMission.BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputTtmlStream.read(buffer)) != -1) {
                outputVttStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                mission.notifyProgress(bytesRead);
            }

            mission.length = totalBytes;
            mission.unknownLength = false;
            mission.notifyFinished();

        } catch (IOException e) {
            String logMessage = "Error extracting subtitle paragraphs from " +
                                localTtmlFile.getAbsolutePath() + ", error:" +
                                e.getMessage();
            Log.e(TAG, logMessage);
            mission.notifyError(DownloadMission.ERROR_FILE_CREATION, e);
        }
    }

    private static void printLocalSubtitleConvertedOk(DownloadMission mission) {
        try {
            String logMessage = "Local subtitle uri is extracted to:" +
                                mission.storage.getName();
            Log.i(TAG, logMessage);
        } catch (NullPointerException e) {
            Log.w(TAG, "Fail to convert ttml subtitle to vtt.", e);
        }
    }
}
