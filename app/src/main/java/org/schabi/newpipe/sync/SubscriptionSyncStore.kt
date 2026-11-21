/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import java.util.concurrent.Callable
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.database.sync.SubscriptionSyncChangeEntity
import org.schabi.newpipe.database.sync.SubscriptionSyncOriginStateEntity
import org.schabi.newpipe.database.sync.SubscriptionSyncPeerStateEntity
import org.schabi.newpipe.database.sync.SubscriptionSyncRecordEntity
import org.schabi.newpipe.profiles.ProfileManager

internal interface SubscriptionSyncStore {
    val localPeerId: String

    fun reconcileLocalSubscriptions()

    fun recordLocalUpsert(subscription: SubscriptionEntity)

    fun recordLocalUpserts(subscriptions: List<SubscriptionEntity>) {
        subscriptions.forEach(::recordLocalUpsert)
    }

    fun recordLocalDelete(profileId: String, serviceId: Int, url: String)

    fun recordLocalDelete(serviceId: Int, url: String) {
        recordLocalDelete(ProfileManager.DEFAULT_PROFILE_ID, serviceId, url)
    }

    fun getKnownRevisions(): Map<String, Long>

    fun getPendingChanges(peerId: String, limit: Int): SubscriptionChangeBatch

    fun acknowledgePeer(peerId: String, knownRevisions: Map<String, Long>)

    fun applyChanges(changes: List<SubscriptionChange>): SubscriptionApplyResult

    fun clearPeerKnowledge()
}

internal class RoomSubscriptionSyncStore internal constructor(
    private val database: AppDatabase,
    override val localPeerId: String,
    private val canMaterializeProfile: (String) -> Boolean = { true }
) : SubscriptionSyncStore {
    private val syncDao = database.subscriptionSyncDAO()
    private val subscriptionDao = database.subscriptionDAO()

    override fun reconcileLocalSubscriptions() {
        database.runInTransaction {
            val subscriptions = subscriptionDao
                .getAllDirect()
                .filter(::isSynchronizable)
            val liveRecords = subscriptions.associateBy { subscription ->
                SubscriptionRecordId.from(
                    subscription.profileId,
                    subscription.serviceId,
                    requireNotNull(subscription.url)
                )
            }
            val syncRecords = syncDao.getAllRecords().associateBy(
                SubscriptionSyncRecordEntity::recordId
            )

            subscriptions.forEach { subscription ->
                val recordId = SubscriptionRecordId.from(
                    subscription.profileId,
                    subscription.serviceId,
                    requireNotNull(subscription.url)
                )
                val record = syncRecords[recordId]
                if (
                    record == null ||
                    record.isDeleted ||
                    record.youtubeModeMask != subscription.youtubeModeMask ||
                    record.notificationMode != subscription.notificationMode ||
                    record.notificationKeywords != subscription.notificationKeywords
                ) {
                    recordLocalUpsertInTransaction(subscription)
                }
            }

            syncRecords.values
                .filterNot(SubscriptionSyncRecordEntity::isDeleted)
                .filterNot { record -> liveRecords.containsKey(record.recordId) }
                .forEach { record ->
                    recordLocalDeleteInTransaction(
                        record.profileId,
                        record.serviceId,
                        record.url
                    )
                }
        }
    }

    override fun recordLocalUpsert(subscription: SubscriptionEntity) {
        database.runInTransaction {
            recordLocalUpsertInTransaction(subscription)
        }
    }

    override fun recordLocalUpserts(subscriptions: List<SubscriptionEntity>) {
        if (subscriptions.isEmpty()) return
        database.runInTransaction {
            subscriptions.forEach(::recordLocalUpsertInTransaction)
        }
    }

    override fun recordLocalDelete(profileId: String, serviceId: Int, url: String) {
        database.runInTransaction {
            recordLocalDeleteInTransaction(profileId, serviceId, url)
        }
    }

    override fun getKnownRevisions(): Map<String, Long> {
        return syncDao.getAllOriginStates()
            .filter { it.contiguousRevision > 0 }
            .associate { it.originPeerId to it.contiguousRevision }
    }

    override fun getPendingChanges(
        peerId: String,
        limit: Int
    ): SubscriptionChangeBatch {
        require(limit in 1..MAX_SUBSCRIPTION_CHANGES_PER_BATCH)
        val peerKnowledge = syncDao.getPeerStates(peerId)
            .associate { it.originPeerId to it.acknowledgedRevision }
        val origins = syncDao.getChangeOrigins().sorted()
        val candidates = origins.flatMap { origin ->
            syncDao.getChangesAfter(
                origin,
                peerKnowledge[origin] ?: 0,
                limit
            )
        }.map { it.toModel() }
            .sortedBy(SubscriptionChange::versionStamp)
            .take(limit)

        val pendingCount = origins.sumOf { origin ->
            syncDao.countChangesAfter(origin, peerKnowledge[origin] ?: 0)
        }
        return SubscriptionChangeBatch(
            changes = candidates,
            hasMore = pendingCount > candidates.size.toLong()
        )
    }

    override fun acknowledgePeer(
        peerId: String,
        knownRevisions: Map<String, Long>
    ) {
        SubscriptionSyncValidation.validateKnownRevisions(knownRevisions)
        database.runInTransaction {
            val localKnowledge = getKnownRevisions()
            val existing = syncDao.getPeerStates(peerId)
                .associate { it.originPeerId to it.acknowledgedRevision }
            knownRevisions.forEach { (origin, claimedRevision) ->
                val safeRevision = minOf(claimedRevision, localKnowledge[origin] ?: 0)
                val acknowledgedRevision = maxOf(existing[origin] ?: 0, safeRevision)
                if (acknowledgedRevision > 0) {
                    syncDao.upsertPeerState(
                        SubscriptionSyncPeerStateEntity(
                            peerId = peerId,
                            originPeerId = origin,
                            acknowledgedRevision = acknowledgedRevision
                        )
                    )
                }
            }
        }
    }

    override fun applyChanges(changes: List<SubscriptionChange>): SubscriptionApplyResult {
        SubscriptionSyncValidation.validateChanges(changes)
        return database.runInTransaction(
            Callable {
                val maximumAcceptedLamport = minOf(
                    syncDao.getMaximumLamportVersion() + MAX_REMOTE_LAMPORT_ADVANCE,
                    MAX_SYNC_REVISION
                )
                if (changes.any { it.lamportVersion > maximumAcceptedLamport }) {
                    throw SubscriptionSyncException(
                        "A subscription change advances the logical clock too far"
                    )
                }
                var accepted = 0
                var added = 0
                var removed = 0

                changes.forEach { change ->
                    if (syncDao.insertChange(change.toEntity()) == -1L) {
                        return@forEach
                    }
                    accepted += 1
                    advanceContiguousRevision(change.originPeerId)

                    val currentRecord = syncDao.getRecord(change.recordId)
                    if (
                        currentRecord != null &&
                        change.versionStamp <= currentRecord.versionStamp
                    ) {
                        return@forEach
                    }

                    if (!canMaterializeProfile(change.profileId)) {
                        syncDao.upsertRecord(change.toRecordEntity())
                        return@forEach
                    }

                    when (change.type) {
                        SubscriptionChangeType.UPSERT -> {
                            val existing = subscriptionDao.getSubscriptionDirectForProfile(
                                change.profileId,
                                change.serviceId,
                                change.url
                            )
                            val incoming = requireNotNull(change.subscription)
                                .toEntity(existing)
                                .apply {
                                    profileId = change.profileId
                                }
                            subscriptionDao.upsertAll(listOf(incoming))
                            if (existing == null) {
                                added += 1
                            }
                        }

                        SubscriptionChangeType.DELETE -> {
                            if (
                                subscriptionDao.deleteSubscriptionForProfile(
                                    change.profileId,
                                    change.serviceId,
                                    change.url
                                ) > 0
                            ) {
                                removed += 1
                            }
                        }
                    }
                    syncDao.upsertRecord(change.toRecordEntity())
                }

                SubscriptionApplyResult(
                    acceptedChanges = accepted,
                    addedSubscriptions = added,
                    removedSubscriptions = removed
                )
            }
        )
    }

    override fun clearPeerKnowledge() {
        syncDao.deleteAllPeerStates()
    }

    private fun recordLocalUpsertInTransaction(subscription: SubscriptionEntity) {
        val syncedSubscription = SyncedSubscription.from(subscription)
        validateLocalIdentity(syncedSubscription.serviceId, syncedSubscription.url)
        val recordId = SubscriptionRecordId.from(
            subscription.profileId,
            syncedSubscription.serviceId,
            syncedSubscription.url
        )
        val currentRecord = syncDao.getRecord(recordId)
        if (
            currentRecord != null &&
            !currentRecord.isDeleted &&
            currentRecord.youtubeModeMask == syncedSubscription.youtubeModeMask &&
            currentRecord.notificationMode == syncedSubscription.notificationMode &&
            currentRecord.notificationKeywords == syncedSubscription.notificationKeywords
        ) {
            return
        }
        saveLocalChange(
            profileId = subscription.profileId,
            recordId = recordId,
            serviceId = syncedSubscription.serviceId,
            url = syncedSubscription.url,
            type = SubscriptionChangeType.UPSERT,
            subscription = syncedSubscription,
            currentRecord = currentRecord
        )
    }

    private fun recordLocalDeleteInTransaction(
        profileId: String,
        serviceId: Int,
        url: String
    ) {
        val canonicalUrl = url.trim()
        validateLocalIdentity(serviceId, canonicalUrl)
        val recordId = SubscriptionRecordId.from(profileId, serviceId, canonicalUrl)
        val currentRecord = syncDao.getRecord(recordId)
        if (currentRecord?.isDeleted == true) {
            return
        }
        saveLocalChange(
            profileId = profileId,
            recordId = recordId,
            serviceId = serviceId,
            url = canonicalUrl,
            type = SubscriptionChangeType.DELETE,
            subscription = null,
            currentRecord = currentRecord
        )
    }

    private fun saveLocalChange(
        profileId: String,
        recordId: String,
        serviceId: Int,
        url: String,
        type: SubscriptionChangeType,
        subscription: SyncedSubscription?,
        currentRecord: SubscriptionSyncRecordEntity?
    ) {
        val originState = syncDao.getOriginState(localPeerId)
        val originRevision = incrementVersion(
            originState?.contiguousRevision ?: 0
        )
        val lamportVersion = incrementVersion(
            maxOf(
                syncDao.getMaximumLamportVersion(),
                currentRecord?.lamportVersion ?: 0
            )
        )
        val change = SubscriptionChange(
            originPeerId = localPeerId,
            originRevision = originRevision,
            lamportVersion = lamportVersion,
            recordId = recordId,
            profileId = profileId,
            serviceId = serviceId,
            url = url,
            type = type,
            subscription = subscription
        )
        SubscriptionSyncValidation.validateChanges(listOf(change))
        check(syncDao.insertChange(change.toEntity()) != -1L) {
            "The local subscription revision already exists"
        }
        syncDao.upsertOriginState(
            SubscriptionSyncOriginStateEntity(localPeerId, originRevision)
        )
        syncDao.upsertRecord(change.toRecordEntity())
    }

    private fun incrementVersion(value: Long): Long {
        if (value >= MAX_SYNC_REVISION) {
            throw SubscriptionSyncException("The subscription journal version is exhausted")
        }
        return value + 1
    }

    private fun advanceContiguousRevision(originPeerId: String) {
        var contiguous = syncDao.getOriginState(originPeerId)?.contiguousRevision ?: 0
        while (
            contiguous < MAX_SYNC_REVISION &&
            syncDao.hasChange(originPeerId, contiguous + 1)
        ) {
            contiguous += 1
        }
        syncDao.upsertOriginState(
            SubscriptionSyncOriginStateEntity(originPeerId, contiguous)
        )
    }

    private fun validateLocalIdentity(serviceId: Int, url: String) {
        if (
            serviceId < 0 ||
            url.isBlank() ||
            url.length > MAX_SUBSCRIPTION_URL_LENGTH
        ) {
            throw SubscriptionSyncException(
                "This subscription does not have a synchronizable service and URL"
            )
        }
    }

    private fun isSynchronizable(subscription: SubscriptionEntity): Boolean {
        val url = subscription.url
        return subscription.serviceId >= 0 &&
            !url.isNullOrBlank() &&
            url.length <= MAX_SUBSCRIPTION_URL_LENGTH
    }

    private fun SubscriptionSyncChangeEntity.toModel() = SubscriptionChange(
        originPeerId = originPeerId,
        originRevision = originRevision,
        lamportVersion = lamportVersion,
        recordId = recordId,
        profileId = profileId,
        serviceId = serviceId,
        url = url,
        type = try {
            SubscriptionChangeType.valueOf(changeType)
        } catch (error: IllegalArgumentException) {
            throw SubscriptionSyncException(
                "The local subscription journal contains an invalid change",
                error
            )
        },
        subscription = if (changeType == SubscriptionChangeType.UPSERT.name) {
            SyncedSubscription(
                serviceId = serviceId,
                url = url,
                name = name,
                avatarUrl = avatarUrl,
                subscriberCount = subscriberCount,
                description = description,
                youtubeModeMask = youtubeModeMask
                    ?: SubscriptionEntity.YOUTUBE_MODE_REGULAR,
                notificationMode = notificationMode,
                notificationKeywords = notificationKeywords
            )
        } else {
            null
        }
    )

    private fun SubscriptionChange.toEntity() = SubscriptionSyncChangeEntity(
        originPeerId = originPeerId,
        originRevision = originRevision,
        lamportVersion = lamportVersion,
        recordId = recordId,
        profileId = profileId,
        changeType = type.name,
        serviceId = serviceId,
        url = url,
        name = subscription?.name,
        avatarUrl = subscription?.avatarUrl,
        subscriberCount = subscription?.subscriberCount,
        description = subscription?.description,
        youtubeModeMask = subscription?.youtubeModeMask,
        notificationMode = subscription?.notificationMode,
        notificationKeywords = subscription?.notificationKeywords
    )

    private fun SubscriptionChange.toRecordEntity() = SubscriptionSyncRecordEntity(
        recordId = recordId,
        profileId = profileId,
        serviceId = serviceId,
        url = url,
        lamportVersion = lamportVersion,
        originPeerId = originPeerId,
        originRevision = originRevision,
        isDeleted = type == SubscriptionChangeType.DELETE,
        youtubeModeMask = subscription?.youtubeModeMask
            ?: SubscriptionEntity.YOUTUBE_MODE_REGULAR,
        notificationMode = subscription?.notificationMode,
        notificationKeywords = subscription?.notificationKeywords
    )

    private val SubscriptionSyncRecordEntity.versionStamp: SubscriptionVersionStamp
        get() = SubscriptionVersionStamp(
            lamportVersion,
            originPeerId,
            originRevision
        )

    companion object {
        private const val MAX_REMOTE_LAMPORT_ADVANCE = 1_000_000L

        @Volatile
        private var instance: RoomSubscriptionSyncStore? = null

        fun get(context: Context): RoomSubscriptionSyncStore {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val applicationContext = context.applicationContext
                    val stateRepository = AndroidSyncStateRepository(applicationContext)
                    RoomSubscriptionSyncStore(
                        database = NewPipeDatabase.getInstance(applicationContext),
                        localPeerId = stateRepository.loadOrCreateIdentity().peerId.toBase58(),
                        canMaterializeProfile = { profileId ->
                            ProfileManager.getProfile(applicationContext, profileId) != null
                        }
                    )
                }.also { instance = it }
            }
        }
    }
}
