package org.schabi.newpipe.database.playlist

import androidx.room.ColumnInfo
import androidx.room.Embedded
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.jvm.Throws

class PlaylistStreamEntry(
    @Embedded
    val streamEntity: StreamEntity,

    @ColumnInfo(name = StreamStateEntity.STREAM_PROGRESS_TIME, defaultValue = "0")
    val progressTime: Long,

    @ColumnInfo(name = PlaylistStreamEntity.JOIN_STREAM_ID)
    val streamId: Long,

    @ColumnInfo(name = PlaylistStreamEntity.JOIN_INDEX)
    val joinIndex: Int
) : LocalItem {

    @Throws(IllegalArgumentException::class)
    fun toStreamInfoItem(): StreamInfoItem {
        val item = StreamInfoItem(streamEntity.serviceId, streamEntity.url, streamEntity.title, streamEntity.streamType)
        item.duration = streamEntity.duration
        item.uploaderName = streamEntity.uploader
        item.thumbnailUrl = streamEntity.thumbnailUrl

        return item
    }

    override fun getLocalItemType(): LocalItem.LocalItemType {
        return LocalItem.LocalItemType.PLAYLIST_STREAM_ITEM
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is PlaylistStreamEntry || streamEntity != other.streamEntity ||
                progressTime != other.progressTime || streamId != other.streamId || joinIndex != other.joinIndex
        ) return false

        return true
    }

    override fun hashCode(): Int {
        var result = streamEntity.hashCode()
        result = 31 * result + progressTime.hashCode()
        result = 31 * result + streamId.hashCode()
        result = 31 * result + joinIndex
        return result
    }
}
