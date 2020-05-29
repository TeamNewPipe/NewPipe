package org.schabi.newpipe.local.feed

import android.content.Context
import android.util.Log
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.Calendar
import java.util.Date
import org.schabi.newpipe.MainActivity.DEBUG
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.subscription.FeedGroupIcon

class FeedDatabaseManager(context: Context) {
    private val database = NewPipeDatabase.getInstance(context)
    private val feedTable = database.feedDAO()
    private val feedGroupTable = database.feedGroupDAO()
    private val streamTable = database.streamDAO()

    companion object {
        /**
         * Only items that are newer than this will be saved.
         */
        val FEED_OLDEST_ALLOWED_DATE: Calendar = Calendar.getInstance().apply {
            add(Calendar.WEEK_OF_YEAR, -13)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun groups() = feedGroupTable.getAll()

    fun database() = database

    fun asStreamItems(groupId: Long = FeedGroupEntity.GROUP_ALL_ID): Flowable<List<StreamInfoItem>> {
        val streams = when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> feedTable.getAllStreams()
            else -> feedTable.getAllStreamsFromGroup(groupId)
        }

        return streams.map<List<StreamInfoItem>> {
            val items = ArrayList<StreamInfoItem>(it.size)
            for (streamEntity in it) items.add(streamEntity.toStreamInfoItem())
            return@map items
        }
    }

    fun outdatedSubscriptions(outdatedThreshold: Date) = feedTable.getAllOutdated(outdatedThreshold)

    fun notLoadedCount(groupId: Long = FeedGroupEntity.GROUP_ALL_ID): Flowable<Long> {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> feedTable.notLoadedCount()
            else -> feedTable.notLoadedCountForGroup(groupId)
        }
    }

    fun outdatedSubscriptionsForGroup(groupId: Long = FeedGroupEntity.GROUP_ALL_ID, outdatedThreshold: Date) =
            feedTable.getAllOutdatedForGroup(groupId, outdatedThreshold)

    fun markAsOutdated(subscriptionId: Long) = feedTable
            .setLastUpdatedForSubscription(FeedLastUpdatedEntity(subscriptionId, null))

    fun upsertAll(
        subscriptionId: Long,
        items: List<StreamInfoItem>,
        oldestAllowedDate: Date = FEED_OLDEST_ALLOWED_DATE.time
    ) {
        val itemsToInsert = ArrayList<StreamInfoItem>()
        loop@ for (streamItem in items) {
            val uploadDate = streamItem.uploadDate

            itemsToInsert += when {
                uploadDate == null && streamItem.streamType == StreamType.LIVE_STREAM -> streamItem
                uploadDate != null && uploadDate.date().time >= oldestAllowedDate -> streamItem
                else -> continue@loop
            }
        }

        feedTable.unlinkOldLivestreams(subscriptionId)

        if (itemsToInsert.isNotEmpty()) {
            val streamEntities = itemsToInsert.map { StreamEntity(it) }
            val streamIds = streamTable.upsertAll(streamEntities)
            val feedEntities = streamIds.map { FeedEntity(it, subscriptionId) }

            feedTable.insertAll(feedEntities)
        }

        feedTable.setLastUpdatedForSubscription(FeedLastUpdatedEntity(subscriptionId, Calendar.getInstance().time))
    }

    fun removeOrphansOrOlderStreams(oldestAllowedDate: Date = FEED_OLDEST_ALLOWED_DATE.time) {
        feedTable.unlinkStreamsOlderThan(oldestAllowedDate)
        streamTable.deleteOrphans()
    }

    fun clear() {
        feedTable.deleteAll()
        val deletedOrphans = streamTable.deleteOrphans()
        if (DEBUG) Log.d(this::class.java.simpleName, "clear() → streamTable.deleteOrphans() → $deletedOrphans")
    }

    // /////////////////////////////////////////////////////////////////////////
    // Feed Groups
    // /////////////////////////////////////////////////////////////////////////

    fun subscriptionIdsForGroup(groupId: Long): Flowable<List<Long>> {
        return feedGroupTable.getSubscriptionIdsFor(groupId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    fun updateSubscriptionsForGroup(groupId: Long, subscriptionIds: List<Long>): Completable {
        return Completable.fromCallable { feedGroupTable.updateSubscriptionsForGroup(groupId, subscriptionIds) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    fun createGroup(name: String, icon: FeedGroupIcon): Maybe<Long> {
        return Maybe.fromCallable { feedGroupTable.insert(FeedGroupEntity(0, name, icon)) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    fun getGroup(groupId: Long): Maybe<FeedGroupEntity> {
        return feedGroupTable.getGroup(groupId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    fun updateGroup(feedGroupEntity: FeedGroupEntity): Completable {
        return Completable.fromCallable { feedGroupTable.update(feedGroupEntity) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    fun deleteGroup(groupId: Long): Completable {
        return Completable.fromCallable { feedGroupTable.delete(groupId) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    fun updateGroupsOrder(groupIdList: List<Long>): Completable {
        var index = 0L
        val orderMap = groupIdList.associateBy({ it }, { index++ })

        return Completable.fromCallable { feedGroupTable.updateOrder(orderMap) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    fun oldestSubscriptionUpdate(groupId: Long): Flowable<List<Date>> {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> feedTable.oldestSubscriptionUpdateFromAll()
            else -> feedTable.oldestSubscriptionUpdate(groupId)
        }
    }
}
