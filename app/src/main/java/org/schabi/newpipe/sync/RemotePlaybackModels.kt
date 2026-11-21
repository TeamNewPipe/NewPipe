/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.sync

import java.net.URI
import java.util.UUID
import kotlinx.serialization.Serializable

internal const val REMOTE_PLAYBACK_PROTOCOL_ID = "/wizestream/remote-playback/1.0.0"
internal const val REMOTE_PLAYBACK_VERSION = 1

@Serializable
enum class RemotePlaybackAction {
    OPEN,
    PLAY,
    PAUSE,
    SEEK,
    PREVIOUS,
    NEXT,
    STOP,
    STATUS
}

@Serializable
data class RemotePlaybackRequest(
    val version: Int = REMOTE_PLAYBACK_VERSION,
    val requestId: String,
    val action: RemotePlaybackAction,
    val serviceId: Int? = null,
    val url: String? = null,
    val title: String? = null,
    val positionMillis: Long? = null
)

@Serializable
data class RemotePlaybackStatus(
    val playing: Boolean,
    val title: String = "",
    val positionMillis: Long = 0,
    val durationMillis: Long = 0
)

@Serializable
data class RemotePlaybackResponse(
    val accepted: Boolean,
    val requestId: String,
    val status: RemotePlaybackStatus? = null,
    val error: String? = null
)

internal object RemotePlaybackValidation {
    private const val MAX_TITLE_LENGTH = 256
    private const val MAX_URL_LENGTH = 2_048
    private const val MAX_SEEK_MS = 24L * 60 * 60 * 1_000

    fun validateRequest(request: RemotePlaybackRequest) {
        if (request.version != REMOTE_PLAYBACK_VERSION) {
            throw RemotePlaybackException("Unsupported remote playback protocol version")
        }
        validateRequestId(request.requestId)
        when (request.action) {
            RemotePlaybackAction.OPEN -> {
                if (request.serviceId == null || request.serviceId < 0 ||
                    request.url.isNullOrBlank() || request.url.length > MAX_URL_LENGTH ||
                    request.title.isNullOrBlank() || request.title.length > MAX_TITLE_LENGTH ||
                    request.positionMillis == null || request.positionMillis !in 0..MAX_SEEK_MS
                ) {
                    throw RemotePlaybackException("The cast media description is invalid")
                }
                val location = runCatching { URI(request.url) }.getOrNull()
                if (location?.scheme != "https" || location?.host.isNullOrBlank() ||
                    location?.userInfo != null || location?.fragment != null
                ) {
                    throw RemotePlaybackException("Only HTTPS service video URLs can be cast")
                }
            }

            RemotePlaybackAction.SEEK -> {
                if (request.positionMillis == null || request.positionMillis !in 0..MAX_SEEK_MS ||
                    request.serviceId != null || request.url != null || request.title != null
                ) {
                    throw RemotePlaybackException("Invalid remote seek position")
                }
            }

            else -> {
                if (request.serviceId != null || request.url != null ||
                    request.title != null || request.positionMillis != null
                ) {
                    throw RemotePlaybackException("Unexpected media data on remote control")
                }
            }
        }
    }

    fun validateResponse(response: RemotePlaybackResponse, requestId: String) {
        if (response.requestId != requestId) {
            throw RemotePlaybackException("Mismatched remote playback request ID")
        }
        if (!response.accepted) {
            throw RemotePlaybackException(response.error ?: "The receiver rejected remote playback")
        }
        if (response.error != null) {
            throw RemotePlaybackException("Invalid remote playback response")
        }
        response.status?.let { status ->
            if (status.title.length > MAX_TITLE_LENGTH ||
                status.positionMillis < 0 || status.durationMillis < 0
            ) {
                throw RemotePlaybackException("Invalid remote playback status")
            }
        }
    }

    private fun validateRequestId(value: String) {
        if (runCatching { UUID.fromString(value).toString() }.getOrNull() != value) {
            throw RemotePlaybackException("Invalid remote playback request ID")
        }
    }
}

internal class RemotePlaybackException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
