package org.schabi.newpipe.database.playlist;

import androidx.room.ColumnInfo;

/**
 * This class adds a field to {@link PlaylistMetadataEntry} that contains an integer representing
 * how many times a specific stream is already contained inside a local playlist. Used to be able
 * to grey out playlists which already contain the current stream in the playlist append dialog.
 * @see org.schabi.newpipe.local.playlist.LocalPlaylistManager#getPlaylistDuplicates(String)
 */
public class PlaylistDuplicatesEntry extends PlaylistMetadataEntry {
    public static final String PLAYLIST_TIMES_STREAM_IS_CONTAINED = "timesStreamIsContained";
    @ColumnInfo(name = PLAYLIST_TIMES_STREAM_IS_CONTAINED)
    public final long timesStreamIsContained;

    public PlaylistDuplicatesEntry(final long uid,
                                   final String name,
                                   final String thumbnailUrl,
                                   final long streamCount,
                                   final long timesStreamIsContained) {
        super(uid, name, thumbnailUrl, streamCount);
        this.timesStreamIsContained = timesStreamIsContained;
    }
}
