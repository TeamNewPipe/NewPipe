/*
 * SPDX-FileCopyrightText: 2017-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.schabi.newpipe.database.feed.dao.FeedDAO
import org.schabi.newpipe.database.feed.dao.FeedGroupDAO
import org.schabi.newpipe.database.feed.dao.SavedSearchFeedDAO
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.feed.model.SavedSearchFeedEntity
import org.schabi.newpipe.database.feed.model.SavedSearchFeedStreamEntity
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.learning.dao.LearningContentDAO
import org.schabi.newpipe.database.learning.dao.LearningDashboardDAO
import org.schabi.newpipe.database.learning.dao.LearningNoteDAO
import org.schabi.newpipe.database.learning.dao.LearningSessionDAO
import org.schabi.newpipe.database.learning.model.LearningContentSourceEntity
import org.schabi.newpipe.database.learning.model.LearningContentStreamEntity
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.learning.model.LearningSessionEntity
import org.schabi.newpipe.database.notification.PendingStreamNotification
import org.schabi.newpipe.database.notification.PendingStreamNotificationDAO
import org.schabi.newpipe.database.playlist.dao.PlaylistDAO
import org.schabi.newpipe.database.playlist.dao.PlaylistRemoteDAO
import org.schabi.newpipe.database.playlist.dao.PlaylistStreamDAO
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.dao.StreamStateDAO
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionDAO
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.database.sync.HistorySyncChangeEntity
import org.schabi.newpipe.database.sync.HistorySyncDAO
import org.schabi.newpipe.database.sync.HistorySyncOriginStateEntity
import org.schabi.newpipe.database.sync.HistorySyncPeerStateEntity
import org.schabi.newpipe.database.sync.HistorySyncRecordEntity
import org.schabi.newpipe.database.sync.PlaylistSyncChangeEntity
import org.schabi.newpipe.database.sync.PlaylistSyncDAO
import org.schabi.newpipe.database.sync.PlaylistSyncLocalMapEntity
import org.schabi.newpipe.database.sync.PlaylistSyncOriginStateEntity
import org.schabi.newpipe.database.sync.PlaylistSyncPeerStateEntity
import org.schabi.newpipe.database.sync.PlaylistSyncRecordEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncChangeEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncDAO
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncFeedGroupMapEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncLocalStateEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncOriginStateEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncPeerStateEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity
import org.schabi.newpipe.database.sync.SubscriptionSyncChangeEntity
import org.schabi.newpipe.database.sync.SubscriptionSyncDAO
import org.schabi.newpipe.database.sync.SubscriptionSyncOriginStateEntity
import org.schabi.newpipe.database.sync.SubscriptionSyncPeerStateEntity
import org.schabi.newpipe.database.sync.SubscriptionSyncRecordEntity

@TypeConverters(Converters::class)
@Database(
    version = Migrations.DB_VER_CURRENT,
    entities = [
        SubscriptionEntity::class,
        SearchHistoryEntry::class,
        StreamEntity::class,
        StreamHistoryEntity::class,
        StreamStateEntity::class,
        PlaylistEntity::class,
        PlaylistStreamEntity::class,
        PlaylistRemoteEntity::class,
        FeedEntity::class,
        FeedGroupEntity::class,
        FeedGroupSubscriptionEntity::class,
        FeedLastUpdatedEntity::class,
        SavedSearchFeedEntity::class,
        SavedSearchFeedStreamEntity::class,
        SubscriptionSyncChangeEntity::class,
        SubscriptionSyncRecordEntity::class,
        SubscriptionSyncOriginStateEntity::class,
        SubscriptionSyncPeerStateEntity::class,
        PlaylistSyncChangeEntity::class,
        PlaylistSyncRecordEntity::class,
        PlaylistSyncOriginStateEntity::class,
        PlaylistSyncPeerStateEntity::class,
        PlaylistSyncLocalMapEntity::class,
        HistorySyncChangeEntity::class,
        HistorySyncRecordEntity::class,
        HistorySyncOriginStateEntity::class,
        HistorySyncPeerStateEntity::class,
        StructuredPreferenceSyncChangeEntity::class,
        StructuredPreferenceSyncRecordEntity::class,
        StructuredPreferenceSyncOriginStateEntity::class,
        StructuredPreferenceSyncPeerStateEntity::class,
        StructuredPreferenceSyncFeedGroupMapEntity::class,
        StructuredPreferenceSyncLocalStateEntity::class,
        LearningNoteEntity::class,
        LearningSessionEntity::class,
        LearningContentSourceEntity::class,
        LearningContentStreamEntity::class,
        PendingStreamNotification::class
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDAO(): FeedDAO
    abstract fun channelActivityDAO(): org.schabi.newpipe.database.feed.dao.ChannelActivityDAO
    abstract fun feedGroupDAO(): FeedGroupDAO
    abstract fun savedSearchFeedDAO(): SavedSearchFeedDAO
    abstract fun playlistDAO(): PlaylistDAO
    abstract fun playlistRemoteDAO(): PlaylistRemoteDAO
    abstract fun playlistStreamDAO(): PlaylistStreamDAO
    abstract fun searchHistoryDAO(): SearchHistoryDAO
    abstract fun smartPlaylistDAO(): org.schabi.newpipe.local.playlist.smart.SmartPlaylistDAO
    abstract fun streamDAO(): StreamDAO
    abstract fun streamHistoryDAO(): StreamHistoryDAO
    abstract fun streamStateDAO(): StreamStateDAO
    abstract fun subscriptionDAO(): SubscriptionDAO
    abstract fun subscriptionSyncDAO(): SubscriptionSyncDAO
    abstract fun playlistSyncDAO(): PlaylistSyncDAO
    abstract fun historySyncDAO(): HistorySyncDAO
    abstract fun structuredPreferenceSyncDAO(): StructuredPreferenceSyncDAO
    abstract fun learningDashboardDAO(): LearningDashboardDAO
    abstract fun learningContentDAO(): LearningContentDAO
    abstract fun learningNoteDAO(): LearningNoteDAO
    abstract fun learningSessionDAO(): LearningSessionDAO
    abstract fun pendingStreamNotificationDAO(): PendingStreamNotificationDAO

    companion object {
        const val DATABASE_NAME: String = "newpipe.db"
    }
}
