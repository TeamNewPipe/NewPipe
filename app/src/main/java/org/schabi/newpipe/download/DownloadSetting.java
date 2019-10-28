package org.schabi.newpipe.download;

import java.util.Locale;

import us.shandian.giga.io.StoredFileHelper;

public class DownloadSetting {

    private StoredFileHelper storedFileHelper;
    private int threadCount;
    private String[] urls;
    private char kind;
    private String psName;
    private String[] psArgs;
    private Long nearLength;
    private String source;
    private String videoResolution;
    private int audioBitRate;
    private Locale subtitleLocale;

    public DownloadSetting(StoredFileHelper storedFileHelper, int threadCount, String[] urls,
                           String source, char kind, String psName, String[] psArgs, Long nearLength,
                           String videoResolution, int audioBitRate, Locale subtitleLocale) {
        this.storedFileHelper = storedFileHelper;
        this.threadCount = threadCount;
        this.urls = urls;
        this.kind = kind;
        this.psName = psName;
        this.psArgs = psArgs;
        this.nearLength = nearLength;
        this.source = source;
        this.videoResolution = videoResolution;
        this.audioBitRate = audioBitRate;
        this.subtitleLocale = subtitleLocale;
    }

    public StoredFileHelper getStoredFileHelper() {
        return storedFileHelper;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public String[] getUrls() {
        return urls;
    }

    public char getKind() {
        return this.kind;
    }

    public String getPsName() {
        return this.psName;
    }

    public String[] getPsArgs() {
        return this.psArgs;
    }

    public Long getNearLength() {
        return this.nearLength;
    }

    public String getSource() {
        return this.source;
    }

    public String getVideoResolution() {
        return this.videoResolution;
    }

    public int getAudioBitRate() {
        return this.audioBitRate;
    }

    public Locale getSubtitleLocale() {
        return this.subtitleLocale;
    }
}
