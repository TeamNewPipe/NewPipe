/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.util.UUID
import kotlinx.serialization.Serializable

internal const val SYNC_DIAGNOSTICS_PROTOCOL_ID = "/wizestream/diagnostics/1.0.0"
internal const val SYNC_DIAGNOSTICS_VERSION = 1
internal const val MAX_APP_VERSION_NAME_LENGTH = 64

@Serializable
data class SyncAppVersion(
    val name: String,
    val code: Long
)

@Serializable
internal data class SyncDiagnosticsRequest(
    val version: Int = SYNC_DIAGNOSTICS_VERSION,
    val attemptId: String,
    val appVersion: SyncAppVersion,
    val syncProtocolVersion: Int = SYNC_PROTOCOL_VERSION
)

@Serializable
internal data class SyncDiagnosticsResponse(
    val accepted: Boolean,
    val error: String? = null,
    val attemptId: String? = null,
    val appVersion: SyncAppVersion? = null,
    val syncProtocolVersion: Int? = null
)

data class SyncPeerDiagnostics(
    val attemptId: String,
    val localAppVersion: SyncAppVersion,
    val remoteAppVersion: SyncAppVersion? = null,
    val localSyncProtocolVersion: Int = SYNC_PROTOCOL_VERSION,
    val remoteSyncProtocolVersion: Int? = null
)

internal object SyncDiagnosticsValidation {
    fun validateRequest(request: SyncDiagnosticsRequest) {
        if (request.version != SYNC_DIAGNOSTICS_VERSION) {
            throw SyncDiagnosticsException(
                "Unsupported sync diagnostics version: ${request.version}"
            )
        }
        validateAttemptId(request.attemptId)
        validateAppVersion(request.appVersion)
        validateSyncProtocolVersion(request.syncProtocolVersion)
    }

    fun validateResponse(response: SyncDiagnosticsResponse, attemptId: String) {
        if (!response.accepted) {
            throw SyncDiagnosticsException(
                response.error ?: "The remote device rejected sync diagnostics"
            )
        }
        if (response.error != null || response.attemptId != attemptId) {
            throw SyncDiagnosticsException("The sync diagnostics response is invalid")
        }
        validateAppVersion(
            response.appVersion
                ?: throw SyncDiagnosticsException("The remote app version is missing")
        )
        validateSyncProtocolVersion(
            response.syncProtocolVersion
                ?: throw SyncDiagnosticsException("The remote sync protocol version is missing")
        )
    }

    fun validateAttemptId(attemptId: String) {
        val canonical = runCatching { UUID.fromString(attemptId).toString() }.getOrNull()
        if (canonical != attemptId) {
            throw SyncDiagnosticsException("The sync attempt ID is invalid")
        }
    }

    fun validateAppVersion(appVersion: SyncAppVersion) {
        if (
            appVersion.name.isBlank() ||
            appVersion.name.length > MAX_APP_VERSION_NAME_LENGTH ||
            appVersion.code < 0
        ) {
            throw SyncDiagnosticsException("The app version is invalid")
        }
    }

    private fun validateSyncProtocolVersion(version: Int) {
        if (version < 1) {
            throw SyncDiagnosticsException("The sync protocol version is invalid")
        }
    }
}

class SyncDiagnosticsException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
