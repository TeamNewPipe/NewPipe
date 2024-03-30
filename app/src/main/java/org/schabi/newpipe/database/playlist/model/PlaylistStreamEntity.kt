package org.schabi.newpipe.database.playlist.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity

@Entity(tableName = PlaylistStreamEntity.PLAYLIST_STREAM_JOIN_TABLE, primaryKeys = [PlaylistStreamEntity.JOIN_PLAYLIST_ID, PlaylistStreamEntity.JOIN_INDEX], indices = [Index(value = [PlaylistStreamEntity.JOIN_PLAYLIST_ID, PlaylistStreamEntity.JOIN_INDEX], unique = true), Index(value = [PlaylistStreamEntity.JOIN_STREAM_ID])], foreignKeys = [ForeignKey(entity = PlaylistEntity::class, parentColumns = PlaylistEntity.Companion.PLAYLIST_ID, childColumns = PlaylistStreamEntity.JOIN_PLAYLIST_ID, onDelete = CASCADE, onUpdate = CASCADE, deferred = true), ForeignKey(entity = StreamEntity::class, parentColumns = StreamEntity.STREAM_ID, childColumns = PlaylistStreamEntity.JOIN_STREAM_ID, onDelete = CASCADE, onUpdate = CASCADE, deferred = true)])
class PlaylistStreamEntity(@field:ColumnInfo(name = JOIN_PLAYLIST_ID) private var playlistUid: Long, @field:ColumnInfo(name = JOIN_STREAM_ID) private var streamUid: Long, @field:ColumnInfo(name = JOIN_INDEX) private var index: Int) {
    fun getPlaylistUid(): Long {
        return playlistUid
    }

    fun setPlaylistUid(playlistUid: Long) {
        this.playlistUid = playlistUid
    }

    fun getStreamUid(): Long {
        return streamUid
    }

    fun setStreamUid(streamUid: Long) {
        this.streamUid = streamUid
    }

    fun getIndex(): Int {
        return index
    }

    fun setIndex(index: Int) {
        this.index = index
    }

    companion object {
        val PLAYLIST_STREAM_JOIN_TABLE: String = "playlist_stream_join"
        val JOIN_PLAYLIST_ID: String = "playlist_id"
        val JOIN_STREAM_ID: String = "stream_id"
        val JOIN_INDEX: String = "join_index"
    }
}
