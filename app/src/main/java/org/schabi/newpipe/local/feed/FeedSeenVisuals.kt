/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import org.schabi.newpipe.database.stream.model.StreamStateEntity

internal enum class FeedItemVisualState(val opacity: Float) {
    UNSEEN(1.0f),
    SEEN(0.73f),
    WATCHED(0.50f)
}

internal object FeedSeenVisuals {
    /** Reuse the same completed-playback threshold as watch-history filtering. */
    fun isWatched(durationSeconds: Long, progressMillis: Long?): Boolean {
        return durationSeconds > 0 && progressMillis != null &&
            StreamStateEntity(0L, progressMillis).isFinished(durationSeconds)
    }

    fun state(watched: Boolean, seen: Boolean): FeedItemVisualState = when {
        watched -> FeedItemVisualState.WATCHED
        seen -> FeedItemVisualState.SEEN
        else -> FeedItemVisualState.UNSEEN
    }
}
