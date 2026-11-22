package org.schabi.newpipe.database.stream.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import java.time.OffsetDateTime
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.stream.StreamUploadDate
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity.Companion.STREAM_ID
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.StreamTypeUtil

@Dao
abstract class StreamDAO : BasicDAO<StreamEntity> {
    @Query("SELECT * FROM streams")
    abstract override fun getAll(): Flowable<List<StreamEntity>>

    @Query("DELETE FROM streams")
    abstract override fun deleteAll(): Int

    @Query("SELECT * FROM streams WHERE service_id = :serviceId")
    abstract override fun listByService(serviceId: Int): Flowable<List<StreamEntity>>

    @Query("SELECT * FROM streams WHERE url = :url AND service_id = :serviceId")
    abstract fun getStream(serviceId: Long, url: String): Flowable<List<StreamEntity>>

    @Query("SELECT * FROM streams WHERE uid = :streamId")
    abstract fun getStreamDirect(streamId: Long): StreamEntity?

    @Query("SELECT * FROM streams WHERE service_id = :serviceId AND url = :url")
    abstract fun getStreamDirect(serviceId: Int, url: String): StreamEntity?

    @Query(
        """
        UPDATE streams SET duration = :duration
        WHERE service_id = :serviceId AND url = :url AND duration <= 0
          AND EXISTS (
            SELECT 1 FROM feed
            INNER JOIN subscriptions ON subscriptions.uid = feed.subscription_id
            WHERE feed.stream_id = streams.uid AND subscriptions.profile_id = :profileId
          )
        """
    )
    abstract fun fillMissingFeedDuration(serviceId: Int, url: String, duration: Long, profileId: String): Int

    @Query("UPDATE streams SET uploader_url = :uploaderUrl WHERE url = :url AND service_id = :serviceId")
    abstract fun setUploaderUrl(serviceId: Long, url: String, uploaderUrl: String): Completable

    @Query(
        """
        SELECT uploader_avatar_url FROM streams
        WHERE service_id = :serviceId
        AND uploader_url = :uploaderUrl
        AND uploader_avatar_url IS NOT NULL
        AND TRIM(uploader_avatar_url) != ''
        ORDER BY uid ASC
        LIMIT 1
        """
    )
    abstract fun findKnownUploaderAvatar(serviceId: Int, uploaderUrl: String): String?

    @Query(
        """
        UPDATE streams SET uploader_avatar_url = :avatarUrl
        WHERE service_id = :serviceId
        AND uploader_url = :uploaderUrl
        AND (uploader_avatar_url IS NULL OR TRIM(uploader_avatar_url) = '')
        """
    )
    abstract fun backfillMissingUploaderAvatar(
        serviceId: Int,
        uploaderUrl: String,
        avatarUrl: String
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun silentInsertInternal(stream: StreamEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun silentInsertAllInternal(streams: List<StreamEntity>): List<Long>

    @Query("SELECT COUNT(*) != 0 FROM streams WHERE url = :url AND service_id = :serviceId")
    internal abstract fun exists(serviceId: Int, url: String): Boolean

    @Query(
        """
        SELECT uid, stream_type, textual_upload_date, upload_date,
        is_upload_date_approximation, duration, uploader_avatar_url, is_short_form_content,
        content_category
        FROM streams WHERE url = :url AND service_id = :serviceId
        """
    )
    internal abstract fun getMinimalStreamForCompare(serviceId: Int, url: String): StreamCompareFeed?

    @Transaction
    open fun upsert(newerStream: StreamEntity): Long {
        val uid = silentInsertInternal(newerStream)

        if (uid != -1L) {
            newerStream.uid = uid
            return uid
        }

        compareAndUpdateStream(newerStream)

        update(newerStream)
        return newerStream.uid
    }

    @Transaction
    open fun upsertAll(streams: List<StreamEntity>): List<Long> {
        val insertUidList = silentInsertAllInternal(streams)

        val streamIds = ArrayList<Long>(streams.size)
        for ((index, uid) in insertUidList.withIndex()) {
            val newerStream = streams[index]
            if (uid != -1L) {
                streamIds.add(uid)
                newerStream.uid = uid
                continue
            }

            compareAndUpdateStream(newerStream)
            streamIds.add(newerStream.uid)
        }

        update(streams)
        return streamIds
    }

    private fun compareAndUpdateStream(newerStream: StreamEntity) {
        val existentMinimalStream = getMinimalStreamForCompare(newerStream.serviceId, newerStream.url)
            ?: error("Stream cannot be null just after insertion.")
        newerStream.uid = existentMinimalStream.uid

        if (!StreamTypeUtil.isLiveStream(newerStream.streamType)) {
            // Preserve stable source dates and first-seen timestamps. A source-provided
            // relative date is still better than the synthetic timestamp of an undated Short.
            val existingDateIsSynthetic = StreamUploadDate.isSynthetic(
                newerStream.serviceId,
                existentMinimalStream.isUploadDateApproximation,
                existentMinimalStream.textualUploadDate
            )
            val hasBetterPrecision = newerStream.uploadDate != null &&
                (
                    newerStream.isUploadDateApproximation != true ||
                        (existingDateIsSynthetic && !newerStream.hasSyntheticUploadDate)
                    )
            if (existentMinimalStream.uploadDate != null && !hasBetterPrecision) {
                newerStream.uploadDate = existentMinimalStream.uploadDate
                newerStream.textualUploadDate = existentMinimalStream.textualUploadDate
                newerStream.isUploadDateApproximation = existentMinimalStream.isUploadDateApproximation
            }

            if (existentMinimalStream.duration > 0 && newerStream.duration < 0) {
                newerStream.duration = existentMinimalStream.duration
            }
        }

        if (newerStream.uploaderAvatarUrl.isNullOrBlank()) {
            newerStream.uploaderAvatarUrl = existentMinimalStream.uploaderAvatarUrl
        }

        // A refresh without category metadata must not discard a previously verified label.
        if (newerStream.contentCategory == null) {
            newerStream.contentCategory = existentMinimalStream.contentCategory
        }

        // Some endpoints cannot identify Shorts reliably. Once an extractor has positively
        // identified an item as short-form content, do not erase that information on refresh.
        newerStream.isShortFormContent =
            newerStream.isShortFormContent || existentMinimalStream.isShortFormContent
    }

    @Query(
        """
        DELETE FROM streams WHERE

        NOT EXISTS (SELECT 1 FROM stream_history sh
        WHERE sh.stream_id = streams.uid)

        AND NOT EXISTS (SELECT 1 FROM stream_state ss
        WHERE ss.stream_id = streams.uid)

        AND NOT EXISTS (SELECT 1 FROM playlist_stream_join ps
        WHERE ps.stream_id = streams.uid)

        AND NOT EXISTS (SELECT 1 FROM feed f
        WHERE f.stream_id = streams.uid)

        AND NOT EXISTS (SELECT 1 FROM saved_search_feed_stream ssf
        WHERE ssf.stream_id = streams.uid)

        AND NOT EXISTS (SELECT 1 FROM learning_notes ln
        WHERE ln.stream_id = streams.uid)

        AND NOT EXISTS (SELECT 1 FROM learning_sessions ls
        WHERE ls.stream_id = streams.uid)

        AND NOT EXISTS (SELECT 1 FROM learning_content_streams lcs
        WHERE lcs.stream_id = streams.uid)
        """
    )
    abstract fun deleteOrphans(): Int

    /**
     * Minimal entry class used when comparing/updating an existent stream.
     */
    internal data class StreamCompareFeed(
        @ColumnInfo(name = STREAM_ID)
        var uid: Long = 0,

        @ColumnInfo(name = StreamEntity.STREAM_TYPE)
        var streamType: StreamType,

        @ColumnInfo(name = StreamEntity.STREAM_TEXTUAL_UPLOAD_DATE)
        var textualUploadDate: String? = null,

        @ColumnInfo(name = StreamEntity.STREAM_UPLOAD_DATE)
        var uploadDate: OffsetDateTime? = null,

        @ColumnInfo(name = StreamEntity.STREAM_IS_UPLOAD_DATE_APPROXIMATION)
        var isUploadDateApproximation: Boolean? = null,

        @ColumnInfo(name = StreamEntity.STREAM_DURATION)
        var duration: Long,

        @ColumnInfo(name = StreamEntity.STREAM_UPLOADER_AVATAR_URL)
        var uploaderAvatarUrl: String? = null,

        @ColumnInfo(name = StreamEntity.STREAM_IS_SHORT_FORM_CONTENT)
        var isShortFormContent: Boolean = false,

        @ColumnInfo(name = StreamEntity.STREAM_CONTENT_CATEGORY)
        var contentCategory: String? = null
    )
}
