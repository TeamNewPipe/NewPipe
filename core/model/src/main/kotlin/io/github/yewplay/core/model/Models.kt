/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.model

/** A video as the screens show it. */
data class YpVideo(
    val serviceId: Int,
    val url: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val progressMillis: Long = 0
) {
    /** Watched fraction from 0 to 1, or 0 when unknown. */
    val progressFraction: Float
        get() = if (durationSeconds <= 0) 0f else
            (progressMillis / 1000f / durationSeconds).coerceIn(0f, 1f)
}

/** A playlist saved by the user, local or bookmarked from a service. */
data class YpPlaylist(
    val id: Long,
    val name: String,
    val thumbnailUrl: String?,
    val streamCount: Long,
    val isLocal: Boolean,
    /** Service and URL of a bookmarked (remote) playlist; unused for local ones. */
    val serviceId: Int = -1,
    val url: String? = null
)

/** A subscribed channel. */
data class YpChannel(
    val serviceId: Int,
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val hasNewVideos: Boolean = false
)

/** A group of subscribed channels ("Music", "Finance", ...). */
data class YpGroup(
    val id: Long,
    val name: String
)
