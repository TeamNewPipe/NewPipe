/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.PeerId
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.feed.notifications.NotificationKeywordFilter
import org.schabi.newpipe.profiles.ProfileManager

internal const val SUBSCRIPTION_SYNC_PROTOCOL_ID = "/wizestream/subscriptions/1.0.0"
internal const val SUBSCRIPTION_SYNC_VERSION = 1
internal const val MAX_SUBSCRIPTION_CHANGES_PER_BATCH = 8
internal const val MAX_SYNC_ORIGINS = 128
internal const val MAX_SUBSCRIPTION_URL_LENGTH = 4096
internal const val MAX_SUBSCRIPTION_NAME_LENGTH = 512
internal const val MAX_SUBSCRIPTION_AVATAR_URL_LENGTH = 4096
internal const val MAX_SUBSCRIPTION_DESCRIPTION_LENGTH = 4 * 1024
internal const val MAX_SYNC_REVISION = 1_000_000_000_000L

@Serializable
internal enum class SubscriptionChangeType {
    UPSERT,
    DELETE
}

@Serializable
internal data class SyncedSubscription(
    val serviceId: Int,
    val url: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val subscriberCount: Long? = null,
    val description: String? = null,
    val youtubeModeMask: Int = SubscriptionEntity.YOUTUBE_MODE_REGULAR,
    val notificationMode: Int? = null,
    val notificationKeywords: String? = null
) {
    internal fun toEntity(existing: SubscriptionEntity? = null) = SubscriptionEntity(
        serviceId = serviceId,
        url = url,
        name = name,
        handle = existing?.handle,
        avatarUrl = avatarUrl,
        subscriberCount = subscriberCount,
        description = description,
        youtubeModeMask = youtubeModeMask,
        notificationMode = notificationMode
            ?: existing?.notificationMode
            ?: NotificationMode.DISABLED,
        notificationKeywords = notificationKeywords
            ?: existing?.notificationKeywords.orEmpty(),
        feedMuted = existing?.feedMuted ?: false
    )

    companion object {
        internal fun from(entity: SubscriptionEntity): SyncedSubscription {
            return SyncedSubscription(
                serviceId = entity.serviceId,
                url = requireNotNull(entity.url).trim(),
                name = entity.name?.take(MAX_SUBSCRIPTION_NAME_LENGTH),
                avatarUrl = entity.avatarUrl?.take(MAX_SUBSCRIPTION_AVATAR_URL_LENGTH),
                subscriberCount = entity.subscriberCount,
                description = entity.description?.take(MAX_SUBSCRIPTION_DESCRIPTION_LENGTH),
                youtubeModeMask = entity.youtubeModeMask,
                notificationMode = entity.notificationMode,
                notificationKeywords = entity.notificationKeywords
            )
        }
    }
}

@Serializable
internal data class SubscriptionChange(
    val originPeerId: String,
    val originRevision: Long,
    val lamportVersion: Long,
    val recordId: String,
    val profileId: String = ProfileManager.DEFAULT_PROFILE_ID,
    val serviceId: Int,
    val url: String,
    val type: SubscriptionChangeType,
    val subscription: SyncedSubscription? = null
) {
    internal val versionStamp: SubscriptionVersionStamp
        get() = SubscriptionVersionStamp(
            lamportVersion,
            originPeerId,
            originRevision
        )
}

@Serializable
internal data class SubscriptionSyncRequest(
    val version: Int = SUBSCRIPTION_SYNC_VERSION,
    val knownRevisions: Map<String, Long>,
    val changes: List<SubscriptionChange>,
    val hasMore: Boolean
)

@Serializable
internal data class SubscriptionSyncResponse(
    val accepted: Boolean,
    val error: String? = null,
    val knownRevisions: Map<String, Long> = emptyMap(),
    val changes: List<SubscriptionChange> = emptyList(),
    val hasMore: Boolean = false
)

data class SubscriptionSyncResult(
    val peer: TrustedPeer,
    val sentChanges: Int,
    val receivedChanges: Int,
    val addedSubscriptions: Int,
    val removedSubscriptions: Int,
    val rounds: Int
)

data class DeviceSyncAttempt(
    val peer: TrustedPeer,
    val diagnostics: SyncPeerDiagnostics? = null,
    val profileResult: ProfileSyncResult? = null,
    val profileError: String? = null,
    val result: SubscriptionSyncResult? = null,
    val error: String? = null,
    val subscriptionSkipped: Boolean = false,
    val playlistResult: PlaylistSyncResult? = null,
    val playlistError: String? = null,
    val playlistSkipped: Boolean = false,
    val watchHistoryResult: HistorySyncResult? = null,
    val watchHistoryError: String? = null,
    val watchHistorySkipped: Boolean = false,
    val searchHistoryResult: HistorySyncResult? = null,
    val searchHistoryError: String? = null,
    val searchHistorySkipped: Boolean = false,
    val learningNotesResult: HistorySyncResult? = null,
    val learningNotesError: String? = null,
    val learningNotesSkipped: Boolean = false,
    val structuredPreferenceResults: Map<StructuredPreferenceCategory, StructuredPreferenceSyncResult?> = emptyMap(),
    val structuredPreferenceErrors: Map<StructuredPreferenceCategory, String?> = emptyMap(),
    val structuredPreferenceSkipped: Set<StructuredPreferenceCategory> = emptySet(),
    val retryDiagnostics: Map<DeviceSyncLogCategory, String> = emptyMap()
)

data class DeviceSyncSummary(
    val attempts: List<DeviceSyncAttempt>
) {
    val succeeded: Int
        get() = attempts.count {
            it.profileResult != null &&
                (it.subscriptionSkipped || it.result != null) &&
                (it.playlistSkipped || it.playlistResult != null) &&
                (it.watchHistorySkipped || it.watchHistoryResult != null) &&
                (it.searchHistorySkipped || it.searchHistoryResult != null) &&
                (it.learningNotesSkipped || it.learningNotesResult != null) &&
                StructuredPreferenceCategory.entries.all { category ->
                    category in it.structuredPreferenceSkipped ||
                        it.structuredPreferenceResults[category] != null
                }
        }

    val failed: Int
        get() = attempts.size - succeeded

    val sentChanges: Int
        get() = attempts.sumOf {
            (it.profileResult?.sentChanges ?: 0) +
                (it.result?.sentChanges ?: 0) +
                (it.playlistResult?.sentChanges ?: 0) +
                (it.watchHistoryResult?.sentChanges ?: 0) +
                (it.searchHistoryResult?.sentChanges ?: 0) +
                (it.learningNotesResult?.sentChanges ?: 0) +
                it.structuredPreferenceResults.values.sumOf { result ->
                    result?.sentChanges ?: 0
                }
        }

    val receivedChanges: Int
        get() = attempts.sumOf {
            (it.profileResult?.receivedChanges ?: 0) +
                (it.result?.receivedChanges ?: 0) +
                (it.playlistResult?.receivedChanges ?: 0) +
                (it.watchHistoryResult?.receivedChanges ?: 0) +
                (it.searchHistoryResult?.receivedChanges ?: 0) +
                (it.learningNotesResult?.receivedChanges ?: 0) +
                it.structuredPreferenceResults.values.sumOf { result ->
                    result?.receivedChanges ?: 0
                }
        }
}

internal data class SubscriptionVersionStamp(
    val lamportVersion: Long,
    val originPeerId: String,
    val originRevision: Long
) : Comparable<SubscriptionVersionStamp> {
    override fun compareTo(other: SubscriptionVersionStamp): Int {
        return compareValuesBy(
            this,
            other,
            SubscriptionVersionStamp::lamportVersion,
            SubscriptionVersionStamp::originPeerId,
            SubscriptionVersionStamp::originRevision
        )
    }
}

internal data class SubscriptionChangeBatch(
    val changes: List<SubscriptionChange>,
    val hasMore: Boolean
)

internal data class SubscriptionApplyResult(
    val acceptedChanges: Int,
    val addedSubscriptions: Int,
    val removedSubscriptions: Int
)

internal object SubscriptionSyncValidation {
    fun validateRequest(request: SubscriptionSyncRequest) {
        if (request.version != SUBSCRIPTION_SYNC_VERSION) {
            throw SubscriptionSyncException(
                "Unsupported subscription synchronization version: ${request.version}"
            )
        }
        validateKnownRevisions(request.knownRevisions)
        validateChanges(request.changes)
    }

    fun validateResponse(response: SubscriptionSyncResponse) {
        if (!response.accepted) {
            if (response.error.isNullOrBlank()) {
                throw SubscriptionSyncException("The remote device rejected synchronization")
            }
            if (response.error.length > MAX_SYNC_ERROR_LENGTH) {
                throw SubscriptionSyncException("The synchronization error is too large")
            }
            return
        }
        if (response.error != null) {
            throw SubscriptionSyncException("A successful synchronization response has an error")
        }
        validateKnownRevisions(response.knownRevisions)
        validateChanges(response.changes)
    }

    fun validateKnownRevisions(knownRevisions: Map<String, Long>) {
        if (knownRevisions.size > MAX_SYNC_ORIGINS) {
            throw SubscriptionSyncException("The synchronization clock has too many origins")
        }
        knownRevisions.forEach { (peerId, revision) ->
            validatePeerId(peerId)
            if (revision !in 0..MAX_SYNC_REVISION) {
                throw SubscriptionSyncException(
                    "A synchronization revision is outside the supported range"
                )
            }
        }
    }

    fun validateChanges(changes: List<SubscriptionChange>) {
        if (changes.size > MAX_SUBSCRIPTION_CHANGES_PER_BATCH) {
            throw SubscriptionSyncException("Too many subscription changes were sent")
        }
        val revisions = hashSetOf<Pair<String, Long>>()
        changes.forEach { change ->
            validatePeerId(change.originPeerId)
            if (
                change.originRevision !in 1..MAX_SYNC_REVISION ||
                change.lamportVersion !in 1..MAX_SYNC_REVISION
            ) {
                throw SubscriptionSyncException("A subscription change has an invalid version")
            }
            if (!revisions.add(change.originPeerId to change.originRevision)) {
                throw SubscriptionSyncException("A subscription change was sent more than once")
            }
            if (!ProfileManager.isCanonicalProfileId(change.profileId)) {
                throw SubscriptionSyncException(
                    "A subscription change has an invalid profile identity"
                )
            }
            if (
                change.url.isBlank() ||
                change.url != change.url.trim() ||
                change.url.length > MAX_SUBSCRIPTION_URL_LENGTH ||
                change.serviceId < 0
            ) {
                throw SubscriptionSyncException("A subscription change has an invalid identity")
            }
            if (
                change.recordId != SubscriptionRecordId.from(
                    change.profileId,
                    change.serviceId,
                    change.url
                )
            ) {
                throw SubscriptionSyncException("A subscription record identity is invalid")
            }
            when (change.type) {
                SubscriptionChangeType.UPSERT -> {
                    val subscription = change.subscription
                        ?: throw SubscriptionSyncException(
                            "A subscription addition has no subscription data"
                        )
                    validateSubscription(subscription)
                    if (
                        subscription.serviceId != change.serviceId ||
                        subscription.url != change.url
                    ) {
                        throw SubscriptionSyncException(
                            "Subscription data does not match its record identity"
                        )
                    }
                }

                SubscriptionChangeType.DELETE -> {
                    if (change.subscription != null) {
                        throw SubscriptionSyncException(
                            "A subscription deletion contains unexpected data"
                        )
                    }
                }
            }
        }
    }

    private fun validateSubscription(subscription: SyncedSubscription) {
        if (
            (subscription.name?.length ?: 0) > MAX_SUBSCRIPTION_NAME_LENGTH ||
            (subscription.avatarUrl?.length ?: 0) >
            MAX_SUBSCRIPTION_AVATAR_URL_LENGTH ||
            (subscription.description?.length ?: 0) >
            MAX_SUBSCRIPTION_DESCRIPTION_LENGTH ||
            subscription.youtubeModeMask < SubscriptionEntity.YOUTUBE_MODE_REGULAR ||
            subscription.youtubeModeMask > SubscriptionEntity.YOUTUBE_MODE_ALL ||
            subscription.notificationMode?.let {
                it !in NotificationMode.DISABLED..NotificationMode.KEYWORDS_ONLY
            } == true ||
            subscription.notificationKeywords?.let { keywords ->
                keywords != NotificationKeywordFilter.normalize(keywords) ||
                    (
                        keywords.isNotEmpty() &&
                            !NotificationKeywordFilter.isValid(keywords)
                        )
            } == true ||
            (
                subscription.notificationMode == NotificationMode.KEYWORDS_ONLY &&
                    !NotificationKeywordFilter.isValid(
                        subscription.notificationKeywords.orEmpty()
                    )
                )
        ) {
            throw SubscriptionSyncException("Subscription metadata is invalid")
        }
    }

    private fun validatePeerId(peerId: String) {
        try {
            PeerId.fromBase58(peerId)
        } catch (error: Exception) {
            throw SubscriptionSyncException("A synchronization PeerID is invalid", error)
        }
    }

    private const val MAX_SYNC_ERROR_LENGTH = 512
}

internal object SubscriptionRecordId {
    fun from(serviceId: Int, url: String): String {
        return from(ProfileManager.DEFAULT_PROFILE_ID, serviceId, url)
    }

    fun from(profileId: String, serviceId: Int, url: String): String {
        val canonicalUrl = url.trim()
        val identity = if (profileId == ProfileManager.DEFAULT_PROFILE_ID) {
            "$serviceId\u0000$canonicalUrl"
        } else {
            "$profileId\u0000$serviceId\u0000$canonicalUrl"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}

class SubscriptionSyncException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
