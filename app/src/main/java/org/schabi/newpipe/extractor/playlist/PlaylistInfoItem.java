package org.schabi.newpipe.extractor.playlist;

import org.schabi.newpipe.extractor.InfoItem;

import javax.annotation.Nullable;

public class PlaylistInfoItem extends InfoItem {
    private static final long serialVersionUID = 3771611321187138122L;

    private String uploaderName;
    @Nullable
    private String uploaderId;
    @Nullable
    private String uploaderUrl;
    /**
     * How many streams this playlist have
     */
    private long streamCount = 0;
    private PlaylistInfo.PlaylistType playlistType;

    public PlaylistInfoItem(final int serviceId, final String url, final String name) {
        super(InfoType.PLAYLIST, serviceId, url, name);
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public void setUploaderName(final String uploaderName) {
        this.uploaderName = uploaderName;
    }

    @Nullable
    public String getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(@Nullable final String uploaderId) {
        this.uploaderId = uploaderId;
    }

    @Nullable
    public String getUploaderUrl() {
        return uploaderUrl;
    }

    public void setUploaderUrl(@Nullable final String uploaderUrl) {
        this.uploaderUrl = uploaderUrl;
    }

    public long getStreamCount() {
        return streamCount;
    }

    public void setStreamCount(final long streamCount) {
        this.streamCount = streamCount;
    }

    public PlaylistInfo.PlaylistType getPlaylistType() {
        return playlistType;
    }

    public void setPlaylistType(final PlaylistInfo.PlaylistType playlistType) {
        this.playlistType = playlistType;
    }
}
