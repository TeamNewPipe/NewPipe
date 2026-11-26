package org.schabi.newpipe.database.notification

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingStreamNotificationDAO {
    // IGNORE also preserves closed identities: a repeated fetch must never reopen them.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entries: List<PendingStreamNotification>): List<Long>

    @Query(
        "SELECT * FROM pending_stream_notifications WHERE profile_id = :profileId " +
            "AND generation = :generation AND dismissed = 0 " +
            "ORDER BY created_at DESC, service_id, stream_url"
    )
    fun pending(profileId: String, generation: String): List<PendingStreamNotification>

    @Query(
        "UPDATE pending_stream_notifications SET dismissed = 1, stream_title = '', " +
            "channel_name = '', thumbnail_url = NULL WHERE profile_id = :profileId " +
            "AND generation = :generation AND service_id = :serviceId AND stream_url = :streamUrl"
    )
    fun closeItem(profileId: String, generation: String, serviceId: Int, streamUrl: String): Int

    @Query(
        "UPDATE pending_stream_notifications SET dismissed = 1, stream_title = '', " +
            "channel_name = '', thumbnail_url = NULL WHERE profile_id = :profileId " +
            "AND generation = :generation AND service_id = :serviceId AND channel_url = :channelUrl"
    )
    fun closeChannel(profileId: String, generation: String, serviceId: Int, channelUrl: String): Int

    @Query(
        "UPDATE pending_stream_notifications SET dismissed = 1, stream_title = '', " +
            "channel_name = '', thumbnail_url = NULL WHERE profile_id = :profileId " +
            "AND generation = :generation"
    )
    fun closeAll(profileId: String, generation: String): Int

    @Query("DELETE FROM pending_stream_notifications WHERE profile_id = :profileId")
    fun deleteProfile(profileId: String)

    @Query("SELECT DISTINCT generation FROM pending_stream_notifications")
    fun generations(): List<String>

    @Query("DELETE FROM pending_stream_notifications WHERE generation = :generation")
    fun deleteGeneration(generation: String)

    @Query("DELETE FROM pending_stream_notifications")
    fun deleteAll()
}
