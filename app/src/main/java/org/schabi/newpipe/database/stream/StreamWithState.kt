package org.schabi.newpipe.database.stream

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import org.schabi.newpipe.database.stream.model.StreamEntity

data class StreamWithState(
    @Embedded
    val stream: StreamEntity,

    @ColumnInfo(name = "progress_time")
    val stateProgressMillis: Long?
)
