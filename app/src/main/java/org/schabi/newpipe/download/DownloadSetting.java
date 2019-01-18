package org.schabi.newpipe.download;

import android.os.Parcel;
import android.os.Parcelable;

import org.schabi.newpipe.extractor.stream.Stream;

public class DownloadSetting {

    private Stream videoStream = null;
    private Stream audioStream = null;
    private Stream subtitlesStream = null;
    private String location;
    private String setting;
    private int threadCount = -1;

    public static final String SETTING_VIDEO = "v";
    public static final String SETTING_AUDIO = "a";
    public static final String SETTING_SUBTITLES = "s";

    public DownloadSetting(String setting, String location, Stream quality, int threadCount) {
        this.setting = setting;
        this.location = location;
        this.threadCount = threadCount;
        switch (setting) {
            case SETTING_AUDIO:
                audioStream = quality;
                break;
            case SETTING_VIDEO:
                videoStream = quality;
                break;
            case SETTING_SUBTITLES:
                subtitlesStream = quality;
                break;
            default:
                break;
        }
    }

    public String getSetting() {
        return setting;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public String getLocation() {
        return location;
    }

    public Stream getStream() {
        switch (setting) {
            case SETTING_AUDIO:
                return audioStream;
            case SETTING_VIDEO:
                return videoStream;
            case SETTING_SUBTITLES:
                return subtitlesStream;
            default:
                return null;
        }
    }
}
