/*
 * SPDX-FileCopyrightText: 2018-2023 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.stream.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import org.schabi.newpipe.database.stream.model.StreamEntity.Companion.STREAM_ID
import org.schabi.newpipe.database.stream.model.StreamStateEntity.Companion.JOIN_STREAM_ID
import org.schabi.newpipe.database.stream.model.StreamStateEntity.Companion.PLAYBACK_FINISHED_END_MILLISECONDS
import org.schabi.newpipe.database.stream.model.StreamStateEntity.Companion.STREAM_STATE_TABLE

@Entity(
    tableName = STREAM_STATE_TABLE,
    primaryKeys = [JOIN_STREAM_ID],
    foreignKeys = [
        ForeignKey(
            entity = StreamEntity::class,
            parentColumns = arrayOf(STREAM_ID),
            childColumns = arrayOf(JOIN_STREAM_ID),
            onDelete = CASCADE,
            onUpdate = CASCADE
        )
    ]
)
data class StreamStateEntity(
    @ColumnInfo(name = JOIN_STREAM_ID)
    val streamUid: Long,

    @ColumnInfo(name = STREAM_PROGRESS_MILLIS)
    val progressMillis: Long
) {
    /**
     * The state will be considered valid, and thus be saved, if the progress is more than
     * [PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS] or at least 1/4 of the video length.
     * @param durationInSeconds the duration of the stream connected with this state, in seconds
     * @return whether this stream state entity should be saved or not
     */
    fun isValid(durationInSeconds: Long): Boolean {
        return progressMillis > PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS ||
            progressMillis > durationInSeconds * 1000 / 4
    }

    /**
     * The video will be considered as finished, if the time left is less than
     * [PLAYBACK_FINISHED_END_MILLISECONDS] and the progress is at least 3/4 of the video length.
     * The state will be saved anyway, so that it can be shown under stream info items, but the
     * player will not resume if a state is considered as finished. Finished streams are also the
     * ones that can be filtered out in the feed fragment.
     * @param durationInSeconds the duration of the stream connected with this state, in seconds
     * @return whether the stream is finished or not
     */
    fun isFinished(durationInSeconds: Long): Boolean {
        return progressMillis >= durationInSeconds * 1000 - PLAYBACK_FINISHED_END_MILLISECONDS &&
            progressMillis >= durationInSeconds * 1000 * 3 / 4
    }

    companion object {
        const val STREAM_STATE_TABLE = "stream_state"
        const val JOIN_STREAM_ID = "stream_id"

        // This additional field is required for the SQL query because 'stream_id' is used
        // for some other joins already
        const val JOIN_STREAM_ID_ALIAS = "stream_id_alias"
        const val STREAM_PROGRESS_MILLIS = "progress_time"

        /**
         * Playback state will not be saved, if playback time is less than this threshold
         * (5000ms = 5s).
         */
        const val PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS = 5000L

        /**
         * Stream will be considered finished if the playback time left exceeds this threshold
         * (60000ms = 60s).
         * @see org.schabi.newpipe.database.stream.model.StreamStateEntity.isFinished
         */
        const val PLAYBACK_FINISHED_END_MILLISECONDS = 60000L
    }
}
