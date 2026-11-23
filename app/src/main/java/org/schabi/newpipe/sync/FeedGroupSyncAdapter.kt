/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncFeedGroupMapEntity
import org.schabi.newpipe.database.sync.StructuredPreferenceSyncRecordEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon

internal class FeedGroupSyncAdapter(
    database: AppDatabase,
    private val recordRepository: StructuredPreferenceRecordRepository,
    private val canMaterializeProfile: (String) -> Boolean = { true }
) : StructuredPreferenceCategoryAdapter {
    override val category = StructuredPreferenceCategory.FEED_GROUPS

    private val feedGroupDao = database.feedGroupDAO()
    private val subscriptionDao = database.subscriptionDAO()

    override fun snapshotHash(): String {
        val subscriptions = subscriptionDao
            .getAllDirect()
            .associateBy { it.profileId to it.uid }
        val snapshot = feedGroupDao
            .getAllDirect()
            .filter { canMaterializeProfile(it.profileId) }
            .map { group ->
                FeedGroupSnapshot(
                    uid = group.uid,
                    profileId = group.profileId,
                    name = group.name,
                    iconId = group.icon.id,
                    sortOrder = group.sortOrder,
                    memberships = feedGroupDao
                        .getSubscriptionIdsForDirectForProfile(
                            group.profileId,
                            group.uid
                        )
                        .mapNotNull { id -> subscriptions[group.profileId to id] }
                        .map {
                            "${it.serviceId}\u0000${it.url?.trim().orEmpty()}"
                        }
                        .sorted()
                )
            }
        return structuredPreferenceDigest(
            STRUCTURED_PREFERENCE_JSON.encodeToString(snapshot)
        )
    }

    override fun reconcile(bootstrap: Boolean) {
        val groups = feedGroupDao.getAllDirect()
            .filter { canMaterializeProfile(it.profileId) }
        if (groups.size > MAX_FEED_GROUPS) {
            throw StructuredPreferenceSyncException(
                "There are too many feed groups to synchronize"
            )
        }
        val duplicateOrdinals = linkedMapOf<Triple<String, String, Int>, Int>()
        val mappings = groups.associateWith { group ->
            recordRepository.getFeedGroupMapping(group.uid)
                ?: run {
                    val identity = Triple(
                        group.profileId,
                        group.name.trim(),
                        group.icon.id
                    )
                    val ordinal = duplicateOrdinals[identity] ?: 0
                    duplicateOrdinals[identity] = ordinal + 1
                    val recordId = if (bootstrap) {
                        StructuredPreferenceRecordId.initialFeedGroup(
                            group.profileId,
                            group.name,
                            group.icon.id,
                            ordinal
                        )
                    } else {
                        StructuredPreferenceRecordId.feedGroup()
                    }
                    StructuredPreferenceSyncFeedGroupMapEntity(
                        groupRecordId = recordId,
                        groupUid = group.uid
                    ).also(recordRepository::saveFeedGroupMapping)
                }
        }
        val desiredGroupIds = mappings.values.mapTo(hashSetOf()) {
            it.groupRecordId
        }
        groups.forEach { group ->
            val recordId = requireNotNull(mappings[group]).groupRecordId
            recordRepository.saveLocalUpsert(
                category = category,
                recordId = recordId,
                recordType = StructuredPreferenceRecordType.FEED_GROUP,
                record = SyncedStructuredPreferenceRecord(
                    feedGroup = SyncedFeedGroup(
                        name = group.name.trim().take(MAX_STRUCTURED_NAME_LENGTH),
                        iconId = group.icon.id,
                        profileId = group.profileId
                    )
                )
            )
        }
        recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.FEED_GROUP
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filter {
                val group = requireNotNull(recordRepository.decodeRecord(it).feedGroup)
                canMaterializeProfile(group.profileId)
            }
            .filterNot { it.recordId in desiredGroupIds }
            .forEach(recordRepository::saveLocalDelete)

        val subscriptionsById = subscriptionDao
            .getAllDirect()
            .associateBy { it.profileId to it.uid }
        val desiredMemberships = linkedMapOf<String, Pair<String, SyncedFeedGroupMembership>>()
        groups.forEach { group ->
            val groupRecordId = requireNotNull(mappings[group]).groupRecordId
            feedGroupDao.getSubscriptionIdsForDirectForProfile(
                group.profileId,
                group.uid
            )
                .mapNotNull { id -> subscriptionsById[group.profileId to id] }
                .forEach { subscription ->
                    val url = subscription.url?.trim().orEmpty()
                    if (url.isEmpty()) {
                        return@forEach
                    }
                    val membership = SyncedFeedGroupMembership(
                        groupRecordId = groupRecordId,
                        serviceId = subscription.serviceId,
                        subscriptionUrl = url,
                        profileId = group.profileId
                    )
                    val recordId = StructuredPreferenceRecordId.feedGroupMembership(
                        groupRecordId,
                        membership.serviceId,
                        membership.subscriptionUrl
                    )
                    desiredMemberships[recordId] = groupRecordId to membership
                    recordRepository.saveLocalUpsert(
                        category = category,
                        recordId = recordId,
                        recordType = StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP,
                        parentRecordId = groupRecordId,
                        record = SyncedStructuredPreferenceRecord(
                            feedGroupMembership = membership
                        )
                    )
                }
        }
        recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP
        ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .filter {
                val membership = requireNotNull(recordRepository.decodeRecord(it).feedGroupMembership)
                canMaterializeProfile(membership.profileId)
            }
            .filterNot { it.recordId in desiredMemberships }
            .forEach(recordRepository::saveLocalDelete)

        groups.groupBy(FeedGroupEntity::profileId).forEach { (profileId, profileGroups) ->
            recordRepository.saveLocalUpsert(
                category = category,
                recordId = StructuredPreferenceRecordId.feedGroupOrder(profileId),
                recordType = StructuredPreferenceRecordType.FEED_GROUP_ORDER,
                record = SyncedStructuredPreferenceRecord(
                    feedGroupOrder = SyncedFeedGroupOrder(
                        groupRecordIds = profileGroups
                            .sortedBy(FeedGroupEntity::sortOrder)
                            .map { requireNotNull(mappings[it]).groupRecordId },
                        profileId = profileId
                    )
                )
            )
        }
    }

    override fun materialize() {
        val metadataRecords = recordRepository.getRecordsByType(
            category,
            StructuredPreferenceRecordType.FEED_GROUP
        )
        metadataRecords.filter(StructuredPreferenceSyncRecordEntity::isDeleted)
            .forEach { record ->
                val data = recordRepository.decodeRecord(record).feedGroup
                    ?: throw StructuredPreferenceSyncException(
                        "Stored feed group metadata is invalid"
                    )
                recordRepository.getFeedGroupMapping(record.recordId)?.let { mapping ->
                    feedGroupDao.deleteForProfile(
                        data.profileId,
                        mapping.groupUid
                    )
                }
            }
        metadataRecords.filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .forEach { record ->
                val data = recordRepository.decodeRecord(record).feedGroup
                    ?: throw StructuredPreferenceSyncException(
                        "Stored feed group metadata is invalid"
                    )
                val mapping = recordRepository.getFeedGroupMapping(record.recordId)
                if (!canMaterializeProfile(data.profileId)) {
                    mapping?.let {
                        feedGroupDao.deleteForProfile(data.profileId, it.groupUid)
                    }
                    return@forEach
                }
                var group = mapping?.let {
                    feedGroupDao.getGroupDirectForProfile(
                        data.profileId,
                        it.groupUid
                    )
                }
                if (group == null) {
                    val uid = feedGroupDao.insert(
                        FeedGroupEntity(
                            uid = 0,
                            name = data.name,
                            icon = feedGroupIcon(data.iconId),
                            profileId = data.profileId
                        )
                    )
                    recordRepository.saveFeedGroupMapping(
                        StructuredPreferenceSyncFeedGroupMapEntity(
                            groupRecordId = record.recordId,
                            groupUid = uid
                        )
                    )
                    group = requireNotNull(
                        feedGroupDao.getGroupDirectForProfile(
                            data.profileId,
                            uid
                        )
                    )
                }
                group.name = data.name
                group.icon = feedGroupIcon(data.iconId)
                feedGroupDao.updateForProfile(data.profileId, group)
            }

        val subscriptions = subscriptionDao
            .getAllDirect()
            .associateBy {
                Triple(it.profileId, it.serviceId, it.url?.trim())
            }
        metadataRecords.filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .forEach { groupRecord ->
                val groupData = recordRepository.decodeRecord(groupRecord).feedGroup
                    ?: throw StructuredPreferenceSyncException(
                        "Stored feed group metadata is invalid"
                    )
                if (!canMaterializeProfile(groupData.profileId)) {
                    return@forEach
                }
                val mapping = recordRepository.getFeedGroupMapping(groupRecord.recordId)
                    ?: return@forEach
                val subscriptionIds = recordRepository.getChildRecords(
                    category,
                    groupRecord.recordId
                ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
                    .filter {
                        it.recordType ==
                            StructuredPreferenceRecordType.FEED_GROUP_MEMBERSHIP.name
                    }
                    .mapNotNull { record ->
                        val data = recordRepository.decodeRecord(record).feedGroupMembership
                            ?: return@mapNotNull null
                        data.takeIf { it.profileId == groupData.profileId }
                            ?.let {
                                subscriptions[
                                    Triple(
                                        groupData.profileId,
                                        it.serviceId,
                                        it.subscriptionUrl
                                    )
                                ]?.uid
                            }
                    }
                feedGroupDao.updateSubscriptionsForGroupForProfile(
                    groupData.profileId,
                    mapping.groupUid,
                    subscriptionIds
                )
            }

        val liveMetadataById = metadataRecords
            .filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
            .associateBy(StructuredPreferenceSyncRecordEntity::recordId)
        val profileIds = buildSet {
            liveMetadataById.values.forEach { record ->
                recordRepository.decodeRecord(record).feedGroup?.profileId?.let(::add)
            }
            recordRepository.getRecordsByType(
                category,
                StructuredPreferenceRecordType.FEED_GROUP_ORDER
            ).filterNot(StructuredPreferenceSyncRecordEntity::isDeleted)
                .forEach { record ->
                    recordRepository.decodeRecord(record).feedGroupOrder?.profileId?.let(::add)
                }
        }
        profileIds.forEach { profileId ->
            if (!canMaterializeProfile(profileId)) {
                feedGroupDao.deleteAllForProfile(profileId)
                return@forEach
            }
            val order = recordRepository.getRecord(
                category,
                StructuredPreferenceRecordId.feedGroupOrder(profileId)
            )?.takeUnless(StructuredPreferenceSyncRecordEntity::isDeleted)
                ?.let(recordRepository::decodeRecord)
                ?.feedGroupOrder
                ?.takeIf { it.profileId == profileId }
                ?.groupRecordIds
                .orEmpty()
            val orderedUids = order.mapNotNull { recordId ->
                val metadata = liveMetadataById[recordId]
                    ?.let(recordRepository::decodeRecord)
                    ?.feedGroup
                    ?.takeIf { it.profileId == profileId }
                    ?: return@mapNotNull null
                recordRepository.getFeedGroupMapping(recordId)?.groupUid
            }
            val remainingUids = feedGroupDao
                .getAllDirectForProfile(profileId)
                .map(FeedGroupEntity::uid)
                .filterNot(orderedUids::contains)
            feedGroupDao.updateOrderForProfile(
                profileId,
                (orderedUids + remainingUids).mapIndexed { index, uid ->
                    uid to index.toLong()
                }.toMap()
            )
        }
    }

    private fun feedGroupIcon(iconId: Int): FeedGroupIcon {
        return FeedGroupIcon.entries.firstOrNull { it.id == iconId }
            ?: throw StructuredPreferenceSyncException("Unknown feed group icon")
    }

    @Serializable
    private data class FeedGroupSnapshot(
        val uid: Long,
        val profileId: String,
        val name: String,
        val iconId: Int,
        val sortOrder: Long,
        val memberships: List<String>
    )
}
