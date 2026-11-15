/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class DeviceSyncLogCategory {
    PROFILES,
    SUBSCRIPTIONS,
    PLAYLISTS,
    WATCH_HISTORY,
    SEARCH_HISTORY,
    LEARNING_NOTES,
    FEED_GROUPS,
    HOME_TABS,
    CHANNEL_PROFILES,
    FILTERS,
    CONTENT_BLOCKING,
    SETTINGS,
    COMPLETED_DOWNLOADS,
    LESSON_DIFFICULTY,
    RADIO_FILTERS
}

@Serializable
enum class DeviceSyncLogStatus {
    SUCCEEDED,
    FAILED,
    DISABLED
}

@Serializable
enum class DeviceSyncLogDirection {
    OUTGOING,
    INCOMING
}

@Serializable
data class DeviceSyncLogCategoryResult(
    val category: DeviceSyncLogCategory,
    val status: DeviceSyncLogStatus,
    val sentChanges: Int = 0,
    val receivedChanges: Int = 0,
    val error: String? = null,
    val retryDiagnostic: String? = null
)

@Serializable
data class DeviceSyncLogAttempt(
    val deviceName: String,
    val peerId: String,
    val addresses: List<String>,
    val categories: List<DeviceSyncLogCategoryResult>,
    val attemptId: String? = null,
    val localAppVersion: SyncAppVersion? = null,
    val remoteAppVersion: SyncAppVersion? = null,
    val localSyncProtocolVersion: Int? = null,
    val remoteSyncProtocolVersion: Int? = null
)

@Serializable
data class DeviceSyncLogEntry(
    val timestampEpochMillis: Long,
    val background: Boolean,
    val succeededDevices: Int,
    val failedDevices: Int,
    val sentChanges: Int,
    val receivedChanges: Int,
    val direction: DeviceSyncLogDirection = DeviceSyncLogDirection.OUTGOING,
    val localAddresses: List<String> = emptyList(),
    val attempts: List<DeviceSyncLogAttempt> = emptyList(),
    val fatalError: String? = null
)

class DeviceSyncLogRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun entries(): List<DeviceSyncLogEntry> {
        val stored = preferences.getString(ENTRIES_KEY, null) ?: return emptyList()
        return runCatching {
            JSON.decodeFromString<List<DeviceSyncLogEntry>>(stored)
        }.getOrDefault(emptyList())
    }

    fun record(
        summary: DeviceSyncSummary,
        background: Boolean,
        localAddresses: List<String>
    ) {
        save(
            DeviceSyncLogEntry(
                timestampEpochMillis = System.currentTimeMillis(),
                background = background,
                succeededDevices = summary.succeeded,
                failedDevices = summary.failed,
                sentChanges = summary.sentChanges,
                receivedChanges = summary.receivedChanges,
                localAddresses = localAddresses,
                attempts = summary.attempts.map { it.toLogAttempt() }
            )
        )
    }

    fun recordFailure(background: Boolean, error: Throwable) {
        save(
            DeviceSyncLogEntry(
                timestampEpochMillis = System.currentTimeMillis(),
                background = background,
                succeededDevices = 0,
                failedDevices = 0,
                sentChanges = 0,
                receivedChanges = 0,
                fatalError = error.diagnosticMessage()
            )
        )
    }

    fun recordIncoming(peer: TrustedPeer, diagnostics: SyncPeerDiagnostics) {
        save(
            DeviceSyncLogEntry(
                timestampEpochMillis = System.currentTimeMillis(),
                background = false,
                succeededDevices = 0,
                failedDevices = 0,
                sentChanges = 0,
                receivedChanges = 0,
                direction = DeviceSyncLogDirection.INCOMING,
                attempts = listOf(
                    DeviceSyncLogAttempt(
                        deviceName = peer.deviceName,
                        peerId = peer.peerId,
                        addresses = peer.addresses,
                        attemptId = diagnostics.attemptId,
                        localAppVersion = diagnostics.localAppVersion,
                        remoteAppVersion = diagnostics.remoteAppVersion,
                        localSyncProtocolVersion = diagnostics.localSyncProtocolVersion,
                        remoteSyncProtocolVersion = diagnostics.remoteSyncProtocolVersion,
                        categories = emptyList()
                    )
                )
            )
        )
    }

    fun clear() {
        preferences.edit().remove(ENTRIES_KEY).apply()
    }

    @Synchronized
    private fun save(entry: DeviceSyncLogEntry) {
        val updated = buildList {
            add(entry)
            addAll(entries())
        }.take(MAX_ENTRIES)
        preferences.edit()
            .putString(ENTRIES_KEY, JSON.encodeToString(updated))
            .apply()
    }

    private fun DeviceSyncAttempt.toLogAttempt() = DeviceSyncLogAttempt(
        deviceName = peer.deviceName,
        peerId = peer.peerId,
        addresses = peer.addresses,
        attemptId = diagnostics?.attemptId,
        localAppVersion = diagnostics?.localAppVersion,
        remoteAppVersion = diagnostics?.remoteAppVersion,
        localSyncProtocolVersion = diagnostics?.localSyncProtocolVersion,
        remoteSyncProtocolVersion = diagnostics?.remoteSyncProtocolVersion,
        categories = buildList {
            add(
                categoryResult(
                    DeviceSyncLogCategory.PROFILES,
                    profileResult?.sentChanges,
                    profileResult?.receivedChanges,
                    profileError,
                    retryDiagnostic = retryDiagnostics[DeviceSyncLogCategory.PROFILES]
                )
            )
            add(
                categoryResult(
                    DeviceSyncLogCategory.SUBSCRIPTIONS,
                    result?.sentChanges,
                    result?.receivedChanges,
                    error,
                    subscriptionSkipped,
                    retryDiagnostics[DeviceSyncLogCategory.SUBSCRIPTIONS]
                )
            )
            add(
                categoryResult(
                    DeviceSyncLogCategory.PLAYLISTS,
                    playlistResult?.sentChanges,
                    playlistResult?.receivedChanges,
                    playlistError,
                    playlistSkipped,
                    retryDiagnostics[DeviceSyncLogCategory.PLAYLISTS]
                )
            )
            add(
                categoryResult(
                    DeviceSyncLogCategory.WATCH_HISTORY,
                    watchHistoryResult?.sentChanges,
                    watchHistoryResult?.receivedChanges,
                    watchHistoryError,
                    watchHistorySkipped,
                    retryDiagnostics[DeviceSyncLogCategory.WATCH_HISTORY]
                )
            )
            add(
                categoryResult(
                    DeviceSyncLogCategory.SEARCH_HISTORY,
                    searchHistoryResult?.sentChanges,
                    searchHistoryResult?.receivedChanges,
                    searchHistoryError,
                    searchHistorySkipped,
                    retryDiagnostics[DeviceSyncLogCategory.SEARCH_HISTORY]
                )
            )
            add(
                categoryResult(
                    DeviceSyncLogCategory.LEARNING_NOTES,
                    learningNotesResult?.sentChanges,
                    learningNotesResult?.receivedChanges,
                    learningNotesError,
                    learningNotesSkipped,
                    retryDiagnostics[DeviceSyncLogCategory.LEARNING_NOTES]
                )
            )
            StructuredPreferenceCategory.entries.forEach { category ->
                val syncResult = structuredPreferenceResults[category]
                add(
                    categoryResult(
                        category.toLogCategory(),
                        syncResult?.sentChanges,
                        syncResult?.receivedChanges,
                        structuredPreferenceErrors[category],
                        category in structuredPreferenceSkipped,
                        retryDiagnostics[category.toLogCategory()]
                    )
                )
            }
        }
    )

    private fun categoryResult(
        category: DeviceSyncLogCategory,
        sentChanges: Int?,
        receivedChanges: Int?,
        error: String?,
        disabled: Boolean = false,
        retryDiagnostic: String? = null
    ): DeviceSyncLogCategoryResult {
        return when {
            disabled -> DeviceSyncLogCategoryResult(
                category = category,
                status = DeviceSyncLogStatus.DISABLED,
                retryDiagnostic = retryDiagnostic
            )

            sentChanges != null && receivedChanges != null -> DeviceSyncLogCategoryResult(
                category = category,
                status = DeviceSyncLogStatus.SUCCEEDED,
                sentChanges = sentChanges,
                receivedChanges = receivedChanges,
                retryDiagnostic = retryDiagnostic
            )

            else -> DeviceSyncLogCategoryResult(
                category = category,
                status = DeviceSyncLogStatus.FAILED,
                error = error,
                retryDiagnostic = retryDiagnostic
            )
        }
    }

    private fun StructuredPreferenceCategory.toLogCategory(): DeviceSyncLogCategory {
        return when (this) {
            StructuredPreferenceCategory.LESSON_DIFFICULTY ->
                DeviceSyncLogCategory.LESSON_DIFFICULTY

            StructuredPreferenceCategory.FEED_GROUPS -> DeviceSyncLogCategory.FEED_GROUPS

            StructuredPreferenceCategory.HOME_TABS -> DeviceSyncLogCategory.HOME_TABS

            StructuredPreferenceCategory.CHANNEL_PROFILES ->
                DeviceSyncLogCategory.CHANNEL_PROFILES

            StructuredPreferenceCategory.FILTERS -> DeviceSyncLogCategory.FILTERS

            StructuredPreferenceCategory.RADIO_FILTERS -> DeviceSyncLogCategory.RADIO_FILTERS

            StructuredPreferenceCategory.CONTENT_BLOCKING ->
                DeviceSyncLogCategory.CONTENT_BLOCKING

            StructuredPreferenceCategory.SETTINGS -> DeviceSyncLogCategory.SETTINGS

            StructuredPreferenceCategory.COMPLETED_DOWNLOADS ->
                DeviceSyncLogCategory.COMPLETED_DOWNLOADS
        }
    }

    private fun Throwable.diagnosticMessage(): String {
        return generateSequence(this) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .joinToString(CAUSE_SEPARATOR) { cause ->
                val name = cause.javaClass.simpleName
                cause.message?.takeIf(String::isNotBlank)?.let { "$name: $it" } ?: name
            }
            .take(MAX_ERROR_LENGTH)
    }

    companion object {
        private const val PREFERENCES_NAME = "device_sync_log"
        private const val ENTRIES_KEY = "entries"
        private const val MAX_ENTRIES = 20
        private const val MAX_CAUSE_DEPTH = 4
        private const val MAX_ERROR_LENGTH = 2_048
        private const val CAUSE_SEPARATOR = " → "
        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
