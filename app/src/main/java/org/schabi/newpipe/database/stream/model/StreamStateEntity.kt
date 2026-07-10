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
import org.schabi.newpipe.database.stream.model.StreamStateEntity.Companion.PLAYBACK_FINISHED_END_MILLISECONDS

@Entity(
    tableName = "stream_state",
    primaryKeys = ["stream_id"],
    foreignKeys = [
        ForeignKey(
            entity = StreamEntity::class,
            parentColumns = arrayOf("uid"),
            childColumns = arrayOf("stream_id"),
            onDelete = CASCADE,
            onUpdate = CASCADE
        )
    ]
)
data class StreamStateEntity(
    @ColumnInfo(name = "stream_id")
    val streamUid: Long,

    @ColumnInfo(name = "progress_time")
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
