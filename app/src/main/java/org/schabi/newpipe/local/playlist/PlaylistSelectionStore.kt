/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.playlist

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.Callable
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.local.ListSeparators

/** Applies a selected edit only while the complete persisted playlist still matches its source. */
class PlaylistSelectionStore(private val database: AppDatabase, private val activeProfileId: () -> String) {
    fun replace(
        profileId: String,
        playlistId: Long,
        expectedStreamIds: List<Long>,
        replacementStreamIds: List<Long>,
        afterCommit: () -> Unit = {}
    ): Single<Boolean> {
        val expected = expectedStreamIds.toList()
        val replacement = replacementStreamIds.toList()
        validateReplacement(expected, replacement)
        return Single.fromCallable {
            val accepted = replaceNow(profileId, playlistId, expected, replacement)
            // Complete captured, profile-scoped presentation updates even if the view was destroyed.
            if (accepted) afterCommit()
            accepted
        }.subscribeOn(Schedulers.io())
    }

    fun replaceWithHeadings(
        profileId: String,
        playlistId: Long,
        savedOrder: List<PlaylistStreamEntry>,
        replacement: List<PlaylistStreamEntry>,
        separators: ListSeparators,
        expectedHeadings: Map<Int, String>,
        replacementHeadings: Map<Int, String>
    ): Single<Boolean> {
        val source = savedOrder.toList()
        val expected = source.map { it.streamId }
        val result = replacement.map { it.streamId }
        val oldHeadings = expectedHeadings.toMap()
        val newHeadings = replacementHeadings.toMap()
        validateReplacement(expected, result)
        return Single.fromCallable {
            separators.commitSelection(source, oldHeadings, newHeadings) {
                replaceNow(profileId, playlistId, expected, result)
            }
        }.subscribeOn(Schedulers.io())
    }

    private fun replaceNow(profileId: String, playlistId: Long, expected: List<Long>, replacement: List<Long>): Boolean = database.runInTransaction(
        Callable {
            val streams = database.playlistStreamDAO()
            if (activeProfileId() != profileId || !streams.playlistBelongsToProfile(profileId, playlistId)) return@Callable false
            if (streams.getQuickAddEntries(playlistId).map { it.streamUid } != expected) return@Callable false
            val playlist = database.playlistDAO().getPlaylistDirectForProfile(profileId, playlistId) ?: return@Callable false
            streams.deleteBatchForProfile(profileId, playlistId)
            streams.insertAllForProfile(profileId, playlistId, replacement.mapIndexed { index, id -> PlaylistStreamEntity(playlistId, id, index) })
            if (!playlist.isThumbnailPermanent) {
                playlist.thumbnailStreamId = replacement.firstOrNull() ?: PlaylistEntity.DEFAULT_THUMBNAIL_ID
                database.playlistDAO().updateForProfile(profileId, playlist)
            }
            true
        }
    )

    private fun validateReplacement(expected: List<Long>, replacement: List<Long>) {
        val available = expected.groupingBy { it }.eachCount()
        require(replacement.groupingBy { it }.eachCount().all { (id, count) -> count <= (available[id] ?: 0) })
    }
}
