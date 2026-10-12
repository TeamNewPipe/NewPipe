package org.schabi.newpipe.database.stream

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity

data class StreamWithState(
    @Embedded
    val stream: StreamEntity,

    @ColumnInfo(name = StreamStateEntity.STREAM_PROGRESS_MILLIS)
    val stateProgressMillis: Long?
)
