/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.feed.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.stream.StreamType

/** Read-only activity from this profile's retained feed, never watch history or archive save times. */
@Dao
abstract class ChannelActivityDAO {
    @Query(
        """
        SELECT DISTINCT s.uid AS streamId, s.service_id AS serviceId,
            s.upload_date AS publishedAt, s.is_upload_date_approximation AS approximate,
            s.textual_upload_date AS dateText, s.stream_type AS streamType,
            s.upcoming_status AS upcoming
        FROM streams s
        INNER JOIN feed f ON f.stream_id = s.uid
        INNER JOIN subscriptions sub ON sub.uid = f.subscription_id
        WHERE sub.profile_id = :profileId AND sub.service_id = :serviceId
            AND sub.url = :channelUrl AND s.service_id = :serviceId
            AND (sub.service_id <> 0 OR (f.youtube_mode_mask & sub.youtube_mode_mask) <> 0)
        """
    )
    protected abstract fun samples(profileId: String, serviceId: Int, channelUrl: String): List<ChannelActivityRecord>

    @Query(
        """
        SELECT MAX(lu.last_updated)
        FROM feed_last_updated lu
        INNER JOIN subscriptions sub ON sub.uid = lu.subscription_id
        WHERE sub.profile_id = :profileId AND sub.service_id = :serviceId AND sub.url = :channelUrl
            AND (sub.service_id <> 0 OR (lu.youtube_mode_mask & sub.youtube_mode_mask) <> 0)
        """
    )
    protected abstract fun lastUpdated(profileId: String, serviceId: Int, channelUrl: String): OffsetDateTime?

    @Transaction
    open fun snapshot(profileId: String, serviceId: Int, channelUrl: String): ChannelActivitySnapshot = ChannelActivitySnapshot(
        samples(profileId, serviceId, channelUrl),
        lastUpdated(profileId, serviceId, channelUrl)
    )
}

data class ChannelActivityRecord(
    val streamId: Long,
    val serviceId: Int,
    val publishedAt: OffsetDateTime?,
    val approximate: Boolean?,
    val dateText: String?,
    val streamType: StreamType,
    val upcoming: Boolean?
)

data class ChannelActivitySnapshot(val records: List<ChannelActivityRecord>, val lastUpdated: OffsetDateTime?)
