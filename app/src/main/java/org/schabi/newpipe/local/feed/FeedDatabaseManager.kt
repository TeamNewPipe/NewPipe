package org.schabi.newpipe.local.feed

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.schabi.newpipe.MainActivity.DEBUG
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.profiles.ProfileManager

class FeedDatabaseManager @JvmOverloads constructor(
    context: Context,
    private val activeProfileId: String = ProfileManager.getActiveProfileId(context)
) {
    private val database = NewPipeDatabase.getInstance(context)
    private val appContext = context.applicationContext
    private val feedTable = database.feedDAO()
    private val feedGroupTable = database.feedGroupDAO()
    private val streamTable = database.streamDAO()

    companion object {
        /**
         * Only items that are newer than this will be saved.
         */
        val FEED_OLDEST_ALLOWED_DATE: OffsetDateTime
            get() = FeedRetentionPolicy.cutoff(LocalDate.now(ZoneOffset.UTC), false)

        @JvmStatic
        fun oldestAllowedDate(context: Context): OffsetDateTime {
            val enabled = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(org.schabi.newpipe.R.string.feed_extended_history_key), false)
            return FeedRetentionPolicy.cutoff(LocalDate.now(ZoneOffset.UTC), enabled)
        }
    }

    fun groups() = feedGroupTable.getAllForProfile(activeProfileId)

    fun database() = database

    fun getStreams(
        groupId: Long,
        includePlayedStreams: Boolean,
        includePartiallyPlayedStreams: Boolean,
        includeFutureStreams: Boolean,
        scope: FeedScope,
        sortByDiscovery: Boolean = false,
        limit: Int = 500,
        offset: Int = 0
    ): Maybe<List<StreamWithState>> {
        return observeStreams(
            groupId,
            includePlayedStreams,
            includePartiallyPlayedStreams,
            includeFutureStreams,
            scope,
            sortByDiscovery,
            limit,
            offset
        ).firstElement()
    }

    fun observeStreams(
        groupId: Long,
        includePlayedStreams: Boolean,
        includePartiallyPlayedStreams: Boolean,
        includeFutureStreams: Boolean,
        scope: FeedScope,
        sortByDiscovery: Boolean = false,
        limit: Int = 500,
        offset: Int = 0
    ): Flowable<List<StreamWithState>> {
        val query: (List<Long>) -> Flowable<List<StreamWithState>> = { excluded ->
            feedTable.observeStreams(
                activeProfileId,
                groupId,
                includePlayedStreams,
                includePartiallyPlayedStreams,
                if (includeFutureStreams) null else OffsetDateTime.now(),
                scope.serviceId,
                scope.youtubeModeMask,
                sortByDiscovery,
                // A long-lived observation must include uploads published after it began.
                useCurrentTime = !includeFutureStreams,
                limit = limit.coerceAtLeast(1),
                offset = offset.coerceAtLeast(0),
                excludedSubscriptions = excluded
            )
        }
        if (groupId != FeedGroupEntity.GROUP_ALL_ID) return query(listOf(-1L))
        return FeedAllExclusions.changes(appContext, activeProfileId)
            .observeOn(Schedulers.io())
            .map { keys ->
                if (keys.isEmpty()) {
                    listOf(-1L)
                } else {
                    database.subscriptionDAO().getAllDirectForProfile(activeProfileId)
                        .filter { FeedAllExclusions.channelKey(it.serviceId, it.url.orEmpty()) in keys }
                        .map { it.uid }
                        .ifEmpty { listOf(-1L) }
                }
            }
            .switchMap(query)
    }

    fun outdatedSubscriptions(
        outdatedThreshold: OffsetDateTime
    ): Flowable<List<SubscriptionEntity>> {
        return feedTable.getAllOutdated(activeProfileId, outdatedThreshold)
    }

    fun outdatedSubscriptionsForScope(
        scope: FeedScope,
        outdatedThreshold: OffsetDateTime
    ): Flowable<List<SubscriptionEntity>> {
        return feedTable.getAllOutdatedForScope(
            activeProfileId,
            scope.serviceId,
            scope.youtubeModeMask,
            outdatedThreshold
        )
    }

    fun outdatedSubscriptionsWithNotificationModes(
        outdatedThreshold: OffsetDateTime,
        notificationModes: List<Int>
    ) = feedTable.getOutdatedWithNotificationModes(
        activeProfileId,
        outdatedThreshold,
        notificationModes
    )

    fun notLoadedCount(
        groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        scope: FeedScope
    ): Flowable<Long> {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID ->
                feedTable.notLoadedCount(
                    activeProfileId,
                    scope.serviceId,
                    scope.youtubeModeMask
                )

            else ->
                feedTable.notLoadedCountForGroup(
                    activeProfileId,
                    groupId,
                    scope.serviceId,
                    scope.youtubeModeMask
                )
        }
    }

    fun outdatedSubscriptionsForGroup(
        groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        outdatedThreshold: OffsetDateTime
    ) = feedTable.getAllOutdatedForGroup(
        activeProfileId,
        groupId,
        outdatedThreshold
    )

    fun outdatedSubscriptionsForGroupAndScope(
        groupId: Long,
        scope: FeedScope,
        outdatedThreshold: OffsetDateTime
    ) = feedTable.getAllOutdatedForGroupAndScope(
        activeProfileId,
        groupId,
        scope.serviceId,
        scope.youtubeModeMask,
        outdatedThreshold
    )

    fun markAsOutdated(subscriptionId: Long, youtubeModeMask: Int) {
        youtubeModeMasks(youtubeModeMask).forEach { modeMask ->
            feedTable.setLastUpdatedForSubscription(
                FeedLastUpdatedEntity(subscriptionId, modeMask, null)
            )
        }
    }

    fun doesStreamExist(stream: StreamInfoItem): Boolean {
        return streamTable.exists(stream.serviceId, stream.url)
    }

    fun upsertAll(
        subscriptionId: Long,
        items: List<StreamInfoItem>,
        youtubeModeMask: Int = SubscriptionEntity.YOUTUBE_MODE_REGULAR,
        oldestAllowedDate: OffsetDateTime = oldestAllowedDate(appContext),
        uploaderAvatarUrl: String? = null,
        isComplete: Boolean = true
    ) {
        val itemsToInsert = items.mapNotNull { stream ->
            val uploadDate = stream.uploadDate

            when {
                uploadDate == null && stream.streamType == StreamType.LIVE_STREAM -> stream
                uploadDate != null && uploadDate.offsetDateTime() >= oldestAllowedDate -> stream
                else -> null
            }
        }

        // Preserve first-seen time even when a livestream membership is replaced during refresh
        // or the same channel is loaded in another YouTube mode.
        val firstSeen = feedTable.getMemberships(subscriptionId)
            .groupBy { it.streamId }.mapValues { (_, memberships) -> memberships.minOf { it.firstDiscoveredAt } }
        val discoveredAt = System.currentTimeMillis()
        val modeMasks = youtubeModeMasks(youtubeModeMask)
        if (isComplete) {
            modeMasks.forEach { feedTable.unlinkOldLivestreams(subscriptionId, it) }
        }

        if (itemsToInsert.isNotEmpty()) {
            val streamEntities = itemsToInsert.map { item ->
                StreamEntity(item).apply {
                    if (this.uploaderAvatarUrl.isNullOrBlank()) {
                        this.uploaderAvatarUrl = uploaderAvatarUrl
                    }
                }
            }
            val streamIds = streamTable.upsertAll(streamEntities)
            val feedEntities = streamIds.flatMap { streamId ->
                modeMasks.map { modeMask ->
                    FeedEntity(streamId, subscriptionId, modeMask, firstSeen[streamId] ?: discoveredAt)
                }
            }

            feedTable.insertAll(feedEntities)
        }

        val updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
        modeMasks.forEach { modeMask ->
            feedTable.setLastUpdatedForSubscription(
                FeedLastUpdatedEntity(subscriptionId, modeMask, updatedAt)
            )
        }
    }

    fun removeOrphansOrOlderStreams(oldestAllowedDate: OffsetDateTime = oldestAllowedDate(appContext)) {
        feedTable.unlinkStreamsOlderThanForProfile(activeProfileId, oldestAllowedDate)
        streamTable.deleteOrphans()
    }

    fun clear() {
        feedTable.deleteAllForProfile(activeProfileId)
        feedTable.deleteAllLastUpdatedForProfile(activeProfileId)
        val deletedOrphans = streamTable.deleteOrphans()
        if (DEBUG) {
            Log.d(
                this::class.java.simpleName,
                "clear() → streamTable.deleteOrphans() → $deletedOrphans"
            )
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Feed Groups
    // /////////////////////////////////////////////////////////////////////////

    fun subscriptionIdsForGroup(groupId: Long): Flowable<List<Long>> {
        return feedGroupTable.getSubscriptionIdsForProfile(activeProfileId, groupId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun groupIdsForSubscription(subscriptionId: Long): Flowable<List<Long>> {
        return feedGroupTable
            .getGroupIdsForSubscriptionForProfile(activeProfileId, subscriptionId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun setGroupsForSubscription(
        subscriptionId: Long,
        groupIds: List<Long>
    ): Completable {
        return Completable.fromAction {
            feedGroupTable.setGroupsForSubscriptionForProfile(
                activeProfileId,
                subscriptionId,
                groupIds
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun updateSubscriptionsForGroup(groupId: Long, subscriptionIds: List<Long>): Completable {
        return Completable
            .fromCallable {
                feedGroupTable.updateSubscriptionsForGroupForProfile(
                    activeProfileId,
                    groupId,
                    subscriptionIds
                )
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun createGroup(name: String, icon: FeedGroupIcon): Maybe<Long> {
        return Maybe.fromCallable {
            feedGroupTable.insert(
                FeedGroupEntity(
                    uid = 0,
                    name = name,
                    icon = icon,
                    profileId = activeProfileId
                )
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun getGroup(groupId: Long): Maybe<FeedGroupEntity> {
        return feedGroupTable.getGroupForProfile(activeProfileId, groupId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun updateGroup(feedGroupEntity: FeedGroupEntity): Completable {
        feedGroupEntity.profileId = activeProfileId
        return Completable.fromCallable {
            feedGroupTable.updateForProfile(activeProfileId, feedGroupEntity)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun deleteGroup(groupId: Long): Completable {
        return Completable.fromCallable {
            feedGroupTable.deleteForProfile(activeProfileId, groupId)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun updateGroupsOrder(groupIdList: List<Long>): Completable {
        var index = 0L
        val orderMap = groupIdList.associateBy({ it }, { index++ })

        return Completable.fromCallable {
            feedGroupTable.updateOrderForProfile(activeProfileId, orderMap)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun oldestSubscriptionUpdate(
        groupId: Long,
        scope: FeedScope
    ): Flowable<List<OffsetDateTime?>> {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID ->
                feedTable.oldestSubscriptionUpdateFromAll(
                    activeProfileId,
                    scope.serviceId,
                    scope.youtubeModeMask
                )

            else ->
                feedTable.oldestSubscriptionUpdate(
                    activeProfileId,
                    groupId,
                    scope.serviceId,
                    scope.youtubeModeMask
                )
        }
    }

    private fun youtubeModeMasks(mask: Int): List<Int> {
        return listOf(
            SubscriptionEntity.YOUTUBE_MODE_REGULAR,
            SubscriptionEntity.YOUTUBE_MODE_MUSIC
        ).filter { modeMask -> mask and modeMask != 0 }
            .ifEmpty { listOf(SubscriptionEntity.YOUTUBE_MODE_REGULAR) }
    }
}
