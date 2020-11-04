package org.schabi.newpipe.database.playlist.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import org.schabi.newpipe.database.stream.model.StreamEntity;

import static androidx.room.ForeignKey.CASCADE;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_INDEX;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_PLAYLIST_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_STREAM_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.PLAYLIST_STREAM_JOIN_TABLE;

@Entity(tableName = PLAYLIST_STREAM_JOIN_TABLE,
        primaryKeys = {JOIN_PLAYLIST_ID, JOIN_INDEX},
        indices = {
                @Index(value = {JOIN_PLAYLIST_ID, JOIN_INDEX}, unique = true),
                @Index(value = {JOIN_STREAM_ID})
        },
        foreignKeys = {
                @ForeignKey(entity = PlaylistEntity.class,
                        parentColumns = PlaylistEntity.PLAYLIST_ID,
                        childColumns = JOIN_PLAYLIST_ID,
                        onDelete = CASCADE, onUpdate = CASCADE, deferred = true),
                @ForeignKey(entity = StreamEntity.class,
                        parentColumns = StreamEntity.STREAM_ID,
                        childColumns = JOIN_STREAM_ID,
                        onDelete = CASCADE, onUpdate = CASCADE, deferred = true)
        })
public class PlaylistStreamEntity {
    public static final String PLAYLIST_STREAM_JOIN_TABLE = "playlist_stream_join";
    public static final String JOIN_PLAYLIST_ID = "playlist_id";
    public static final String JOIN_STREAM_ID = "stream_id";
    public static final String JOIN_INDEX = "join_index";

    @ColumnInfo(name = JOIN_PLAYLIST_ID)
    private long playlistUid;

    @ColumnInfo(name = JOIN_STREAM_ID)
    private long streamUid;

    @ColumnInfo(name = JOIN_INDEX)
    private int index;

    public PlaylistStreamEntity(final long playlistUid, final long streamUid, final int index) {
        this.playlistUid = playlistUid;
        this.streamUid = streamUid;
        this.index = index;
    }

    public long getPlaylistUid() {
        return playlistUid;
    }

    public void setPlaylistUid(final long playlistUid) {
        this.playlistUid = playlistUid;
    }

    public long getStreamUid() {
        return streamUid;
    }

    public void setStreamUid(final long streamUid) {
        this.streamUid = streamUid;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(final int index) {
        this.index = index;
    }
}
